package com.company.platform.workflow;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.development.DevFileView;
import com.company.platform.development.DevProjectView;
import com.company.platform.development.DevelopmentAccessService;
import com.company.platform.development.DevelopmentScheduleService;
import com.company.platform.integration.IntegrationService;
import com.company.platform.integration.IntegrationTaskView;
import com.company.platform.integration.SeaTunnelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ProjectWorkflowService {
    private final PlatformStore store;
    private final DevelopmentScheduleService schedules;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Long, AtomicBoolean> cancelFlags = new java.util.concurrent.ConcurrentHashMap<>();
    private DevelopmentAccessService developmentAccess;
    private IntegrationService integrationService;
    private ProjectTaskRunner taskRunner;
    private ThreadPoolTaskExecutor rerunExecutor;
    private ThreadPoolTaskExecutor taskExecutor;

    public ProjectWorkflowService(PlatformStore store, DevelopmentScheduleService schedules, JdbcTemplate jdbc) {
        this.store = store;
        this.schedules = schedules;
        this.jdbc = jdbc;
    }

    @Autowired(required = false)
    public void setDevelopmentAccess(DevelopmentAccessService developmentAccess) { this.developmentAccess = developmentAccess; }
    @Autowired(required = false)
    public void setIntegrationService(IntegrationService integrationService) { this.integrationService = integrationService; }
    @Autowired(required = false)
    public void setTaskRunner(ProjectTaskRunner taskRunner) { this.taskRunner = taskRunner; }
    @Autowired(required = false)
    public void setRerunExecutor(@Qualifier("projectRerunExecutor") ThreadPoolTaskExecutor executor) { this.rerunExecutor = executor; }
    @Autowired(required = false)
    public void setTaskExecutor(@Qualifier("projectTaskExecutor") ThreadPoolTaskExecutor executor) { this.taskExecutor = executor; }

    public List<ProjectWorkflowDefinition> definitions() { return definitions("admin"); }
    public List<ProjectWorkflowDefinition> definitions(String operator) {
        return store.projects.values().stream()
                .filter(project -> canViewProject(project.id(), operator))
                .sorted(Comparator.comparing(DevProjectView::name, String.CASE_INSENSITIVE_ORDER))
                .map(this::definition)
                .toList();
    }

    private ProjectWorkflowDefinition definition(DevProjectView project) {
        List<DevFileView> files = projectFiles(project.id());
        List<IntegrationTaskView> integrationTasks = projectIntegrationTasks(project.id());
        int enabled = 0, running = 0, failed = 0;
        for (DevFileView file : files) {
            try {
                DevelopmentScheduleService.ScheduleView schedule = schedules.get(file.id());
                if (schedule.enabled()) enabled++;
                String status = schedules.runtime(file.id()).status();
                if (isRunning(status)) running++;
                if (isFailed(status)) failed++;
            } catch (RuntimeException ignored) { }
        }
        for (IntegrationTaskView task : integrationTasks) {
            Boolean scheduleEnabled = jdbc.query("SELECT enabled FROM integration_task_schedule WHERE task_id=?",
                    rs -> rs.next() && rs.getBoolean(1), task.id());
            if (Boolean.TRUE.equals(scheduleEnabled)) enabled++;
            String status = latestIntegrationStatus(task.id());
            if (isRunning(status)) running++;
            if (isFailed(status)) failed++;
        }
        return new ProjectWorkflowDefinition(project.id(), project.name(), project.description(),
                files.size() + integrationTasks.size(), enabled, running, failed);
    }

    public WorkflowView graph(long projectId) { return graph(projectId, "admin"); }
    public WorkflowView graph(long projectId, String operator) {
        requireProjectView(projectId, operator);
        DevProjectView project = requireProject(projectId);
        List<DevFileView> files = projectFiles(projectId);
        List<IntegrationTaskView> integrations = projectIntegrationTasks(projectId);
        Map<Long, Set<Long>> upstreams = unifiedUpstreamMap(files, integrations);
        Set<Long> nodeIds = new LinkedHashSet<>();
        files.forEach(file -> nodeIds.add(file.id()));
        integrations.forEach(task -> nodeIds.add(integrationNodeId(task.id())));
        Map<Long, Integer> levels = dependencyLevels(nodeIds, upstreams);
        Map<Integer, Integer> rowByLevel = new HashMap<>();
        List<WorkflowNodeView> nodes = new ArrayList<>();
        for (IntegrationTaskView task : integrations) {
            long id = integrationNodeId(task.id());
            int level = levels.getOrDefault(id, 0), row = rowByLevel.merge(level, 1, Integer::sum) - 1;
            nodes.add(new WorkflowNodeView(id, task.name(), NodeType.SEATUNNEL, null,
                    integrationConfig(task.id()), 70 + level * 252, 88 + row * 112, "integration_" + task.id()));
        }
        for (DevFileView file : files) {
            int level = levels.getOrDefault(file.id(), 0), row = rowByLevel.merge(level, 1, Integer::sum) - 1;
            nodes.add(new WorkflowNodeView(file.id(), stripExtension(file.name()), nodeType(file), file.id(), "{}",
                    70 + level * 252, 88 + row * 112, "task_" + file.id()));
        }
        List<WorkflowEdgeView> edges = new ArrayList<>();
        long edgeId = -1;
        for (Map.Entry<Long, Set<Long>> entry : upstreams.entrySet())
            for (Long upstream : entry.getValue()) edges.add(new WorkflowEdgeView(edgeId--, upstream, entry.getKey()));
        return new WorkflowView(0, project.name(), "project_" + project.id(),
                "项目数据集成与开发任务全链路依赖图", "SYNCED", 1, nodes, edges, null, LocalDateTime.now());
    }

    public ImpactView impact(long projectId, long sourceFileId, boolean includeSource) { return impact(projectId, sourceFileId, includeSource, "admin"); }
    public ImpactView impact(long projectId, long sourceFileId, boolean includeSource, String operator) {
        requireProjectView(projectId, operator);
        DevFileView source = requireProjectFile(projectId, sourceFileId);
        return impactByNode(projectId, sourceFileId, sourceFileId, null, stripExtension(source.name()), includeSource);
    }

    public ImpactView impactIntegration(long projectId, long taskId, boolean includeSource, String operator) {
        requireProjectView(projectId, operator);
        IntegrationTaskView task = requireProjectIntegrationTask(projectId, taskId);
        return impactByNode(projectId, integrationNodeId(taskId), null, taskId, task.name(), includeSource);
    }

    private ImpactView impactByNode(long projectId, long sourceNodeId, Long sourceFileId, Long sourceIntegrationTaskId,
                                    String sourceName, boolean includeSource) {
        List<DevFileView> files = projectFiles(projectId);
        List<IntegrationTaskView> integrations = projectIntegrationTasks(projectId);
        Map<Long, Set<Long>> upstreams = unifiedUpstreamMap(files, integrations);
        Set<Long> impactedNodes = descendants(sourceNodeId, upstreams);
        if (includeSource && sourceFileId != null) impactedNodes.add(sourceFileId);
        Map<Long,Integer> levels = levelsFromSource(sourceNodeId, impactedNodes, upstreams);
        Map<Long, DevFileView> byId = files.stream().collect(Collectors.toMap(DevFileView::id, x -> x));
        List<ImpactTask> tasks = impactedNodes.stream().filter(id -> id > 0).map(byId::get).filter(Objects::nonNull)
                .sorted(Comparator.comparingInt((DevFileView f) -> levels.getOrDefault(f.id(), 0))
                        .thenComparing(DevFileView::name, String.CASE_INSENSITIVE_ORDER))
                .map(f -> new ImpactTask(f.id(), stripExtension(f.name()), f.fileType(), levels.getOrDefault(f.id(), 0),
                        f.lifecycleStatus(), safeRuntime(f.id())))
                .toList();
        return new ImpactView(projectId, sourceFileId, sourceIntegrationTaskId, sourceName, includeSource, tasks);
    }

    public RerunBatchView startRerun(RerunRequest request, String operator) {
        requireProjectEdit(request.projectId(), operator);
        validateSource(request.sourceFileId(), request.sourceIntegrationTaskId());
        ImpactView impact = request.sourceIntegrationTaskId() != null
                ? impactIntegration(request.projectId(), request.sourceIntegrationTaskId(), request.includeSource(), operator)
                : impact(request.projectId(), request.sourceFileId(), request.includeSource(), operator);
        if (impact.tasks().isEmpty() && !(request.sourceIntegrationTaskId() != null && request.includeSource()))
            throw new BadRequestException("当前节点没有可重跑的下游任务");
        LocalDate businessDate = parseBusinessDate(request.businessDate());
        preventDuplicateActive(request.projectId(), request.sourceFileId(), request.sourceIntegrationTaskId(), businessDate);
        long batchId = createBatch(request.projectId(), request.sourceFileId(), request.sourceIntegrationTaskId(), null,
                request.includeSource(), businessDate, operator, impact.tasks());
        submitBatch(batchId);
        return batch(batchId, operator);
    }

    public List<RerunBatchView> history(long projectId, int limit, String operator) {
        requireProjectView(projectId, operator);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.queryForList("SELECT id FROM workflow_rerun_batch WHERE project_id=? ORDER BY id DESC LIMIT " + safeLimit, projectId)
                .stream().map(row -> batch(((Number)row.get("id")).longValue(), operator)).toList();
    }

    public RerunBatchView retryFailed(long batchId, String operator) {
        RerunBatchView original = batch(batchId, operator);
        requireProjectEdit(original.projectId(), operator);
        if (Set.of("PENDING", "RUNNING", "CANCELLING").contains(original.status())) throw new BadRequestException("批次仍在运行，不能重试");
        List<ImpactTask> retry = original.tasks().stream()
                .filter(task -> Set.of("FAILED", "WAITING_DEPENDENCY", "CANCELED").contains(task.status()))
                .map(task -> {
                    DevFileView file = task.fileId() == null ? null : store.files.get(task.fileId());
                    return file == null ? null : new ImpactTask(file.id(), stripExtension(file.name()), file.fileType(), task.sequenceNo(), file.lifecycleStatus(), safeRuntime(file.id()));
                }).filter(Objects::nonNull).toList();
        boolean retrySource = original.sourceIntegrationTaskId() != null && original.sourceStatus() != null
                && !"SUCCESS".equalsIgnoreCase(original.sourceStatus());
        if (retry.isEmpty() && !retrySource) throw new BadRequestException("该批次没有失败或等待依赖的任务");
        preventDuplicateActive(original.projectId(), original.sourceFileId(), original.sourceIntegrationTaskId(), LocalDate.parse(original.businessDate()));
        long next = createBatch(original.projectId(), original.sourceFileId(), original.sourceIntegrationTaskId(), original.id(),
                retrySource, LocalDate.parse(original.businessDate()), operator, retry);
        submitBatch(next);
        return batch(next, operator);
    }

    public RerunBatchView cancel(long batchId, String operator) {
        RerunBatchView current = batch(batchId, operator);
        requireProjectEdit(current.projectId(), operator);
        if (!Set.of("PENDING", "RUNNING", "CANCELLING").contains(current.status())) return current;
        jdbc.update("UPDATE workflow_rerun_batch SET cancel_requested=TRUE,status='CANCELLING' WHERE id=?", batchId);
        cancelFlags.computeIfAbsent(batchId, ignored -> new AtomicBoolean()).set(true);
        if (current.sourceExecutionId() != null && current.sourceIntegrationTaskId() != null && integrationService != null) {
            try { integrationService.cancel(current.sourceExecutionId()); } catch (RuntimeException ignored) { }
        }
        return batch(batchId, operator);
    }

    public RerunBatchView batch(long batchId) { return batch(batchId, "admin"); }
    public RerunBatchView batch(long batchId, String operator) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM workflow_rerun_batch WHERE id=?", batchId);
        if (rows.isEmpty()) throw new NotFoundException("重跑批次不存在：" + batchId);
        Map<String,Object> b = rows.getFirst();
        requireProjectView(((Number)b.get("project_id")).longValue(), operator);
        List<RerunTaskView> tasks = jdbc.query("SELECT t.*,f.name,f.file_type FROM workflow_rerun_task t LEFT JOIN dev_file f ON f.id=t.file_id WHERE t.batch_id=? ORDER BY t.sequence_no",
                (rs,n) -> new RerunTaskView(rs.getLong("id"), rs.getObject("file_id", Long.class), stripExtension(rs.getString("name")),
                        rs.getString("file_type"), rs.getInt("sequence_no"), rs.getString("status"), rs.getString("execution_id"),
                        time(rs.getTimestamp("started_at")), time(rs.getTimestamp("finished_at")), rs.getString("error_message"), rs.getString("output_log")), batchId);
        return new RerunBatchView(((Number)b.get("id")).longValue(), ((Number)b.get("project_id")).longValue(),
                numberLong(b.get("source_file_id")), numberLong(b.get("source_integration_task_id")), numberLong(b.get("parent_batch_id")),
                String.valueOf(b.get("business_date")), String.valueOf(b.get("status")), bool(b.get("cancel_requested")),
                string(b.get("source_status")), string(b.get("source_execution_id")), string(b.get("source_error_message")),
                number(b.get("total_tasks")), number(b.get("success_tasks")), number(b.get("failed_tasks")), number(b.get("waiting_tasks")),
                string(b.get("created_by")), timestamp(b.get("created_at")), timestamp(b.get("started_at")), timestamp(b.get("finished_at")), tasks);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverIncompleteBatches() {
        List<Map<String,Object>> active = jdbc.queryForList("SELECT id,status FROM workflow_rerun_batch WHERE status IN ('PENDING','RUNNING','CANCELLING') ORDER BY id");
        for (Map<String,Object> row : active) {
            long id = ((Number)row.get("id")).longValue();
            if ("CANCELLING".equalsIgnoreCase(String.valueOf(row.get("status")))) {
                markCanceled(id, "服务重启时批次处于取消中");
                continue;
            }
            jdbc.update("UPDATE workflow_rerun_task t JOIN dev_file f ON f.id=t.file_id SET t.status='FAILED',t.finished_at=CURRENT_TIMESTAMP,t.error_message='服务重启，本地脚本运行状态不可恢复' WHERE t.batch_id=? AND t.status='RUNNING' AND UPPER(f.file_type) IN ('PYTHON','SHELL')", id);
            jdbc.update("UPDATE workflow_rerun_task t JOIN dev_file f ON f.id=t.file_id SET t.status='PENDING',t.started_at=NULL,t.finished_at=NULL,t.error_message=NULL WHERE t.batch_id=? AND t.status='RUNNING' AND UPPER(f.file_type)='SQL'", id);
            jdbc.update("UPDATE workflow_rerun_batch SET status='PENDING',cancel_requested=FALSE WHERE id=?", id);
            submitBatch(id);
        }
    }

    private long createBatch(long projectId, Long sourceFileId, Long sourceIntegrationTaskId, Long parentBatchId,
                             boolean includeSource, LocalDate businessDate, String operator, List<ImpactTask> tasks) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO workflow_rerun_batch(project_id,source_file_id,source_integration_task_id,parent_batch_id,business_date,scope,include_source,status,total_tasks,created_by) VALUES(?,?,?,?,?,?,?,'PENDING',?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            if (sourceFileId == null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, sourceFileId);
            if (sourceIntegrationTaskId == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, sourceIntegrationTaskId);
            if (parentBatchId == null) ps.setNull(4, java.sql.Types.BIGINT); else ps.setLong(4, parentBatchId);
            ps.setDate(5, Date.valueOf(businessDate)); ps.setString(6, "ALL_DOWNSTREAM"); ps.setBoolean(7, includeSource);
            ps.setInt(8, tasks.size()); ps.setString(9, operator == null || operator.isBlank() ? "admin" : operator);
            return ps;
        }, key);
        long batchId = Objects.requireNonNull(key.getKey()).longValue();
        int sequence = 1;
        for (ImpactTask task : tasks) jdbc.update("INSERT INTO workflow_rerun_task(batch_id,file_id,sequence_no,status) VALUES(?,?,?,'PENDING')",
                batchId, task.fileId(), sequence++);
        return batchId;
    }

    private void submitBatch(long batchId) {
        Runnable job = () -> executeBatch(batchId);
        if (rerunExecutor != null) rerunExecutor.execute(job);
        else Thread.ofPlatform().name("project-rerun-fallback-" + batchId).start(job);
    }

    private void executeBatch(long batchId) {
        AtomicBoolean cancelled = cancelFlags.computeIfAbsent(batchId, ignored -> new AtomicBoolean(false));
        try {
            Map<String,Object> b = jdbc.queryForMap("SELECT * FROM workflow_rerun_batch WHERE id=?", batchId);
            if (bool(b.get("cancel_requested"))) cancelled.set(true);
            if (cancelled.get()) { markCanceled(batchId, "批次已取消"); return; }
            jdbc.update("UPDATE workflow_rerun_batch SET status='RUNNING',started_at=COALESCE(started_at,CURRENT_TIMESTAMP),finished_at=NULL WHERE id=?", batchId);
            long projectId = ((Number)b.get("project_id")).longValue();
            Long sourceIntegration = numberLong(b.get("source_integration_task_id"));
            boolean includeSource = bool(b.get("include_source"));
            String operator = string(b.get("created_by"));
            LocalDate businessDate = ((Date)b.get("business_date")).toLocalDate();
            if (sourceIntegration != null && includeSource) {
                if (!runIntegrationSource(batchId, sourceIntegration, cancelled)) {
                    jdbc.update("UPDATE workflow_rerun_task SET status='WAITING_DEPENDENCY' WHERE batch_id=? AND status='PENDING'", batchId);
                    refreshBatchCounters(batchId, true, cancelled.get());
                    return;
                }
            }
            runDevelopmentTasks(batchId, projectId, businessDate, operator, cancelled);
            refreshBatchCounters(batchId, true, cancelled.get());
        } catch (RuntimeException ex) {
            jdbc.update("UPDATE workflow_rerun_batch SET status='FAILED',finished_at=CURRENT_TIMESTAMP,source_error_message=COALESCE(source_error_message,?) WHERE id=?", message(ex), batchId);
        } finally {
            cancelFlags.remove(batchId);
        }
    }

    private boolean runIntegrationSource(long batchId, long taskId, AtomicBoolean cancelled) {
        if (integrationService == null) throw new BadRequestException("数据集成运行服务不可用");
        try {
            jdbc.update("UPDATE workflow_rerun_batch SET source_status='RUNNING',source_error_message=NULL WHERE id=?", batchId);
            SeaTunnelGateway.SubmitResult submitted = integrationService.execute(taskId, "WORKFLOW");
            jdbc.update("UPDATE workflow_rerun_batch SET source_execution_id=? WHERE id=?", submitted.executionId(), batchId);
            while (true) {
                if (cancelled.get()) {
                    try { integrationService.cancel(submitted.executionId()); } catch (RuntimeException ignored) { }
                    jdbc.update("UPDATE workflow_rerun_batch SET source_status='CANCELED',source_error_message='批次已取消' WHERE id=?", batchId);
                    return false;
                }
                SeaTunnelGateway.JobStatus state = integrationService.status(submitted.executionId());
                String status = state.status() == null ? "UNKNOWN" : state.status().toUpperCase();
                if (isRunning(status)) { Thread.sleep(1000L); continue; }
                if (Set.of("SUCCESS","FINISHED").contains(status)) {
                    jdbc.update("UPDATE workflow_rerun_batch SET source_status='SUCCESS' WHERE id=?", batchId); return true;
                }
                jdbc.update("UPDATE workflow_rerun_batch SET source_status='FAILED',source_error_message=? WHERE id=?", state.message(), batchId);
                return false;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            cancelled.set(true);
            jdbc.update("UPDATE workflow_rerun_batch SET source_status='CANCELED',source_error_message='批次线程被中断' WHERE id=?", batchId);
            return false;
        } catch (RuntimeException ex) {
            jdbc.update("UPDATE workflow_rerun_batch SET source_status='FAILED',source_error_message=? WHERE id=?", message(ex), batchId);
            return false;
        }
    }

    private void runDevelopmentTasks(long batchId, long projectId, LocalDate businessDate, String operator, AtomicBoolean cancelled) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT file_id,sequence_no,status FROM workflow_rerun_task WHERE batch_id=? ORDER BY sequence_no", batchId);
        Set<Long> batchFiles = rows.stream().map(r -> ((Number)r.get("file_id")).longValue()).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Set<Long>> upstreams = upstreamMap(projectFiles(projectId));
        Map<Long,Integer> levels = dependencyLevels(batchFiles, filterUpstreams(upstreams, batchFiles));
        Map<Long,String> states = new java.util.concurrent.ConcurrentHashMap<>();
        rows.stream().filter(r -> "SUCCESS".equals(String.valueOf(r.get("status")))).forEach(r -> states.put(((Number)r.get("file_id")).longValue(), "SUCCESS"));
        Map<Integer,List<Long>> byLevel = batchFiles.stream().collect(Collectors.groupingBy(id -> levels.getOrDefault(id, 0), TreeMap::new, Collectors.toList()));
        for (List<Long> levelFiles : byLevel.values()) {
            if (cancelled.get()) break;
            List<Long> ready = new ArrayList<>();
            for (Long fileId : levelFiles) {
                String existing = states.get(fileId);
                if ("SUCCESS".equals(existing)) continue;
                boolean blocked = upstreams.getOrDefault(fileId, Set.of()).stream().filter(batchFiles::contains)
                        .anyMatch(up -> !"SUCCESS".equals(states.get(up)));
                if (blocked) {
                    states.put(fileId, "WAITING_DEPENDENCY");
                    jdbc.update("UPDATE workflow_rerun_task SET status='WAITING_DEPENDENCY' WHERE batch_id=? AND file_id=?", batchId, fileId);
                } else ready.add(fileId);
            }
            List<Future<ProjectTaskRunner.Result>> futures = new ArrayList<>();
            for (Long fileId : ready) {
                if (taskExecutor != null) futures.add(taskExecutor.submit(() -> runOne(batchId, fileId, businessDate, operator, cancelled)));
                else states.put(fileId, runOne(batchId, fileId, businessDate, operator, cancelled).status());
            }
            for (int i=0;i<futures.size();i++) {
                try { states.put(ready.get(i), futures.get(i).get().status()); }
                catch (Exception ex) { states.put(ready.get(i), failTask(batchId, ready.get(i), message(ex)).status()); }
            }
            refreshBatchCounters(batchId, false, false);
        }
        if (cancelled.get()) markRemainingCanceled(batchId);
    }

    private ProjectTaskRunner.Result runOne(long batchId, long fileId, LocalDate businessDate, String operator, AtomicBoolean cancelled) {
        if (taskRunner == null) return failTask(batchId, fileId, "项目任务运行器不可用");
        jdbc.update("UPDATE workflow_rerun_task SET status='RUNNING',started_at=CURRENT_TIMESTAMP,finished_at=NULL,error_message=NULL WHERE batch_id=? AND file_id=?", batchId, fileId);
        ProjectTaskRunner.Result result = taskRunner.run(fileId, businessDate, operator, cancelled);
        jdbc.update("UPDATE workflow_rerun_task SET status=?,execution_id=?,finished_at=CURRENT_TIMESTAMP,error_message=?,output_log=? WHERE batch_id=? AND file_id=?",
                result.status(), result.executionId(), result.errorMessage(), result.outputLog(), batchId, fileId);
        return result;
    }

    private ProjectTaskRunner.Result failTask(long batchId, long fileId, String message) {
        jdbc.update("UPDATE workflow_rerun_task SET status='FAILED',finished_at=CURRENT_TIMESTAMP,error_message=? WHERE batch_id=? AND file_id=?", message, batchId, fileId);
        return new ProjectTaskRunner.Result("FAILED", null, message, "");
    }

    private void refreshBatchCounters(long batchId, boolean finish, boolean canceled) {
        Map<String,Object> c = jdbc.queryForMap("SELECT COUNT(*) total,SUM(status='SUCCESS') success_count,SUM(status='FAILED') failed_count,SUM(status='WAITING_DEPENDENCY') waiting_count,SUM(status='CANCELED') canceled_count FROM workflow_rerun_task WHERE batch_id=?", batchId);
        int total=number(c.get("total")), success=number(c.get("success_count")), failed=number(c.get("failed_count")), waiting=number(c.get("waiting_count"));
        String sourceStatus = jdbc.queryForObject("SELECT source_status FROM workflow_rerun_batch WHERE id=?", String.class, batchId);
        String status = "RUNNING";
        if (finish) {
            if (canceled || number(c.get("canceled_count")) > 0) status = "CANCELED";
            else if (failed > 0 || waiting > 0 || "FAILED".equalsIgnoreCase(sourceStatus)) status = "FAILED";
            else status = "SUCCESS";
        }
        jdbc.update("UPDATE workflow_rerun_batch SET status=?,total_tasks=?,success_tasks=?,failed_tasks=?,waiting_tasks=?,finished_at=? WHERE id=?",
                status,total,success,failed,waiting,finish?java.sql.Timestamp.valueOf(LocalDateTime.now()):null,batchId);
    }

    private void markRemainingCanceled(long batchId) {
        jdbc.update("UPDATE workflow_rerun_task SET status='CANCELED',finished_at=CURRENT_TIMESTAMP,error_message='批次已取消' WHERE batch_id=? AND status IN ('PENDING','RUNNING','WAITING_DEPENDENCY')", batchId);
    }
    private void markCanceled(long batchId, String reason) {
        markRemainingCanceled(batchId);
        jdbc.update("UPDATE workflow_rerun_batch SET status='CANCELED',cancel_requested=TRUE,finished_at=CURRENT_TIMESTAMP,source_status=CASE WHEN source_status='RUNNING' THEN 'CANCELED' ELSE source_status END,source_error_message=CASE WHEN source_status='RUNNING' THEN ? ELSE source_error_message END WHERE id=?", reason, batchId);
    }

    private void preventDuplicateActive(long projectId, Long sourceFileId, Long sourceIntegrationTaskId, LocalDate businessDate) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM workflow_rerun_batch WHERE project_id=? AND source_file_id <=> ? AND source_integration_task_id <=> ? AND business_date=? AND status IN ('PENDING','RUNNING','CANCELLING')",
                Integer.class, projectId, sourceFileId, sourceIntegrationTaskId, Date.valueOf(businessDate));
        if (count != null && count > 0) throw new BadRequestException("相同来源和业务日期已有运行中的重跑批次，请勿重复提交");
    }

    private void validateSource(Long fileId, Long integrationTaskId) {
        if ((fileId == null) == (integrationTaskId == null)) throw new BadRequestException("sourceFileId 和 sourceIntegrationTaskId 必须且只能填写一个");
    }
    private LocalDate parseBusinessDate(String value) {
        try { return value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value.trim()); }
        catch (RuntimeException ex) { throw new BadRequestException("业务日期格式必须为 yyyy-MM-dd"); }
    }

    private List<DevFileView> projectFiles(long projectId) {
        return store.files.values().stream().filter(f -> f.projectId() == projectId)
                .filter(f -> Set.of("SQL","PYTHON","SHELL").contains((f.fileType()==null?"":f.fileType()).toUpperCase()))
                .sorted(Comparator.comparing(DevFileView::name, String.CASE_INSENSITIVE_ORDER).thenComparingLong(DevFileView::id)).toList();
    }
    private List<IntegrationTaskView> projectIntegrationTasks(long projectId) {
        return store.integrationTasks.values().stream().filter(task -> Objects.equals(task.projectId(), projectId))
                .sorted(Comparator.comparing(IntegrationTaskView::name, String.CASE_INSENSITIVE_ORDER).thenComparingLong(IntegrationTaskView::id)).toList();
    }
    private Map<Long, Set<Long>> unifiedUpstreamMap(List<DevFileView> files, List<IntegrationTaskView> integrations) {
        Map<Long, Set<Long>> result = upstreamMap(files);
        Set<Long> fileIds = files.stream().map(DevFileView::id).collect(Collectors.toSet());
        for (IntegrationTaskView task : integrations) for (Long downstream : task.downstreamFileIds() == null ? List.<Long>of() : task.downstreamFileIds())
            if (fileIds.contains(downstream)) result.computeIfAbsent(downstream, ignored -> new LinkedHashSet<>()).add(integrationNodeId(task.id()));
        integrations.forEach(task -> result.putIfAbsent(integrationNodeId(task.id()), new LinkedHashSet<>()));
        return result;
    }
    private Map<Long, Set<Long>> upstreamMap(List<DevFileView> files) {
        Set<Long> ids = files.stream().map(DevFileView::id).collect(Collectors.toSet());
        Map<Long, Set<Long>> result = new LinkedHashMap<>();
        for (DevFileView file : files) {
            LinkedHashSet<Long> ups;
            try { ups = schedules.get(file.id()).dependencies().stream().map(DevelopmentScheduleService.DependencyView::fileId)
                    .filter(ids::contains).collect(Collectors.toCollection(LinkedHashSet::new)); }
            catch (RuntimeException ex) { ups = new LinkedHashSet<>(); }
            result.put(file.id(), ups);
        }
        return result;
    }
    private Map<Long,Set<Long>> filterUpstreams(Map<Long,Set<Long>> source, Set<Long> ids) {
        Map<Long,Set<Long>> result = new LinkedHashMap<>();
        for (Long id : ids) result.put(id, source.getOrDefault(id, Set.of()).stream().filter(ids::contains).collect(Collectors.toCollection(LinkedHashSet::new)));
        return result;
    }
    private Set<Long> descendants(long source, Map<Long, Set<Long>> upstreamByTarget) {
        Map<Long, Set<Long>> downstream = new HashMap<>();
        upstreamByTarget.forEach((target, ups) -> ups.forEach(up -> downstream.computeIfAbsent(up, x -> new LinkedHashSet<>()).add(target)));
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>(downstream.getOrDefault(source, Set.of()));
        while (!queue.isEmpty()) { long id=queue.removeFirst(); if (!result.add(id)) continue; queue.addAll(downstream.getOrDefault(id, Set.of())); }
        return result;
    }
    private Map<Long,Integer> levelsFromSource(long source, Set<Long> impacted, Map<Long, Set<Long>> upstreamByTarget) {
        Map<Long,Integer> levels = new HashMap<>(); levels.put(source,0);
        for (int i=0;i<upstreamByTarget.size()+1;i++) for (Long target : impacted) {
            int level = upstreamByTarget.getOrDefault(target, Set.of()).stream().mapToInt(up -> levels.getOrDefault(up,-1)).max().orElse(-1)+1;
            if (level > levels.getOrDefault(target,-1)) levels.put(target,level);
        }
        impacted.forEach(id -> levels.putIfAbsent(id,1)); return levels;
    }
    private Map<Long,Integer> dependencyLevels(Set<Long> ids, Map<Long, Set<Long>> upstreams) {
        Map<Long,Integer> indegree=new LinkedHashMap<>(), levels=new HashMap<>(); Map<Long,List<Long>> downstream=new HashMap<>();
        ids.forEach(id -> indegree.put(id,0));
        upstreams.forEach((target,ups)->ups.forEach(up->{ if(ids.contains(up)&&ids.contains(target)){indegree.put(target,indegree.get(target)+1);downstream.computeIfAbsent(up,k->new ArrayList<>()).add(target);}}));
        Deque<Long> q=new ArrayDeque<>(); indegree.forEach((id,d)->{if(d==0){q.add(id);levels.put(id,0);}});
        while(!q.isEmpty()){long id=q.removeFirst();int level=levels.getOrDefault(id,0);for(Long target:downstream.getOrDefault(id,List.of())){levels.put(target,Math.max(levels.getOrDefault(target,0),level+1));int next=indegree.get(target)-1;indegree.put(target,next);if(next==0)q.add(target);}}
        ids.forEach(id->levels.putIfAbsent(id,0)); return levels;
    }

    private DevProjectView requireProject(long projectId) { DevProjectView project=store.projects.get(projectId); if(project==null)throw new NotFoundException("开发项目不存在："+projectId); return project; }
    private DevFileView requireProjectFile(long projectId,long fileId){DevFileView file=store.files.get(fileId);if(file==null||file.projectId()!=projectId)throw new NotFoundException("项目内开发任务不存在："+fileId);return file;}
    private IntegrationTaskView requireProjectIntegrationTask(long projectId,long taskId){IntegrationTaskView task=store.integrationTasks.get(taskId);if(task==null||!Objects.equals(task.projectId(),projectId))throw new NotFoundException("项目内离线同步任务不存在："+taskId);return task;}
    private boolean canViewProject(long projectId,String operator){return developmentAccess==null||developmentAccess.canProjectView(projectId,operator);}
    private void requireProjectView(long projectId,String operator){if(developmentAccess!=null)developmentAccess.requireProjectView(projectId,operator);}
    private void requireProjectEdit(long projectId,String operator){if(developmentAccess!=null)developmentAccess.requireProjectEdit(projectId,operator);}
    private String safeRuntime(long fileId){try{return schedules.runtime(fileId).status();}catch(RuntimeException ex){return "NEVER_RUN";}}
    private String latestIntegrationStatus(long taskId){return jdbc.query("SELECT status FROM integration_instance WHERE task_id=? ORDER BY id DESC LIMIT 1",rs->rs.next()?rs.getString(1):"NEVER_RUN",taskId);}
    private boolean isRunning(String status){return status!=null&&status.matches("(?i)RUNNING|STARTING|QUEUED|SUBMITTED");}
    private boolean isFailed(String status){return status!=null&&status.matches("(?i).*FAIL.*|ERROR|LOST|UNKNOWN");}
    private long integrationNodeId(long taskId){return -taskId;}
    private NodeType nodeType(DevFileView file){try{return NodeType.valueOf(file.fileType().toUpperCase());}catch(Exception ex){return NodeType.SQL;}}
    private String integrationConfig(long taskId){try{return mapper.writeValueAsString(Map.of("integrationTaskId",taskId));}catch(Exception ex){return "{\"integrationTaskId\":"+taskId+"}";}}
    private int number(Object value){return value==null?0:((Number)value).intValue();}
    private Long numberLong(Object value){return value==null?null:((Number)value).longValue();}
    private boolean bool(Object value){return value instanceof Boolean b?b:value instanceof Number n&&n.intValue()!=0;}
    private String string(Object value){return value==null?null:String.valueOf(value);}
    private LocalDateTime time(java.sql.Timestamp value){return value==null?null:value.toLocalDateTime();}
    private LocalDateTime timestamp(Object value){return value instanceof java.sql.Timestamp t?t.toLocalDateTime():null;}
    private String stripExtension(String name){return name==null?"":name.replaceFirst("(?i)\\.(sql|sh|py)$","");}
    private String message(Throwable error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}

    public record ProjectWorkflowDefinition(long projectId,String name,String description,int taskCount,int enabledSchedules,int runningTasks,int failedTasks) {}
    public record ImpactTask(long fileId,String name,String fileType,int level,String lifecycleStatus,String runtimeStatus) {}
    public record ImpactView(long projectId,Long sourceFileId,Long sourceIntegrationTaskId,String sourceName,boolean includeSource,List<ImpactTask> tasks) {}
    public record RerunRequest(long projectId,Long sourceFileId,Long sourceIntegrationTaskId,boolean includeSource,String businessDate) {
        public RerunRequest(long projectId,long sourceFileId,boolean includeSource,String businessDate){this(projectId,sourceFileId,null,includeSource,businessDate);}
    }
    public record RerunTaskView(long id,Long fileId,String name,String fileType,int sequenceNo,String status,String executionId,LocalDateTime startedAt,LocalDateTime finishedAt,String errorMessage,String outputLog) {}
    public record RerunBatchView(long id,long projectId,Long sourceFileId,Long sourceIntegrationTaskId,Long parentBatchId,String businessDate,String status,boolean cancelRequested,String sourceStatus,String sourceExecutionId,String sourceErrorMessage,int totalTasks,int successTasks,int failedTasks,int waitingTasks,String createdBy,LocalDateTime createdAt,LocalDateTime startedAt,LocalDateTime finishedAt,List<RerunTaskView> tasks) {}
}
