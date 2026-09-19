package com.company.platform.workflow;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.development.DevFileView;
import com.company.platform.development.DevProjectView;
import com.company.platform.development.DevelopmentScheduleService;
import com.company.platform.development.DevelopmentAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ProjectWorkflowService {
    private final PlatformStore store;
    private final DevelopmentScheduleService schedules;
    private final JdbcTemplate jdbc;
    private DevelopmentAccessService developmentAccess;
    public ProjectWorkflowService(PlatformStore store, DevelopmentScheduleService schedules, JdbcTemplate jdbc) {
        this.store = store;
        this.schedules = schedules;
        this.jdbc = jdbc;
    }

    @Autowired(required = false)
    public void setDevelopmentAccess(DevelopmentAccessService developmentAccess) { this.developmentAccess = developmentAccess; }

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
        int enabled = 0, running = 0, failed = 0;
        for (DevFileView file : files) {
            DevelopmentScheduleService.ScheduleView schedule = schedules.get(file.id());
            if (schedule.enabled()) enabled++;
            try {
                String status = schedules.runtime(file.id()).status();
                if (isRunning(status)) running++;
                if (isFailed(status)) failed++;
            } catch (RuntimeException ignored) { }
        }
        return new ProjectWorkflowDefinition(project.id(), project.name(), project.description(),
                files.size(), enabled, running, failed);
    }
    public WorkflowView graph(long projectId) { return graph(projectId, "admin"); }

    public WorkflowView graph(long projectId, String operator) {
        requireProjectView(projectId, operator);
        DevProjectView project = requireProject(projectId);
        List<DevFileView> files = projectFiles(projectId);
        Map<Long, Set<Long>> upstreamByTarget = upstreamMap(files);
        Map<Long, Integer> levels = dependencyLevels(files, upstreamByTarget);
        Map<Integer, Integer> rowByLevel = new HashMap<>();
        List<WorkflowNodeView> nodes = new ArrayList<>();
        for (DevFileView file : files) {
            int level = levels.getOrDefault(file.id(), 0);
            int row = rowByLevel.merge(level, 1, Integer::sum) - 1;
            nodes.add(new WorkflowNodeView(file.id(), stripExtension(file.name()), NodeType.SQL,
                    file.id(), "{}", 70 + level * 252, 88 + row * 112, "task_" + file.id()));
        }
        List<WorkflowEdgeView> edges = new ArrayList<>();
        long edgeId = -1;
        for (Map.Entry<Long, Set<Long>> entry : upstreamByTarget.entrySet()) {
            for (Long upstream : entry.getValue()) edges.add(new WorkflowEdgeView(edgeId--, upstream, entry.getKey()));
        }
        return new WorkflowView(0, project.name(), "project_" + project.id(),
                "项目全部数据开发任务依赖图", "SYNCED", 1, nodes, edges, null, LocalDateTime.now());
    }

    public ImpactView impact(long projectId, long sourceFileId, boolean includeSource) { return impact(projectId, sourceFileId, includeSource, "admin"); }

    public ImpactView impact(long projectId, long sourceFileId, boolean includeSource, String operator) {
        requireProjectView(projectId, operator);
        requireProject(projectId);
        DevFileView source = requireProjectFile(projectId, sourceFileId);
        List<DevFileView> files = projectFiles(projectId);
        Map<Long, Set<Long>> upstreamByTarget = upstreamMap(files);
        Set<Long> impacted = descendants(sourceFileId, upstreamByTarget);
        if (includeSource) impacted.add(sourceFileId);
        Map<Long, Integer> levels = levelsFromSource(sourceFileId, impacted, upstreamByTarget);
        Map<Long, DevFileView> byId = files.stream().collect(Collectors.toMap(DevFileView::id, x -> x));
        List<ImpactTask> tasks = impacted.stream()
                .map(byId::get).filter(Objects::nonNull)
                .sorted(Comparator.comparingInt((DevFileView f) -> levels.getOrDefault(f.id(), 0))
                        .thenComparing(DevFileView::name, String.CASE_INSENSITIVE_ORDER))
                .map(f -> new ImpactTask(f.id(), stripExtension(f.name()), levels.getOrDefault(f.id(), 0),
                        f.lifecycleStatus(), safeRuntime(f.id())))
                .toList();
        return new ImpactView(projectId, sourceFileId, stripExtension(source.name()), includeSource, tasks);
    }

    public RerunBatchView startRerun(RerunRequest request, String operator) {
        requireProjectEdit(request.projectId(), operator);
        ImpactView impact = impact(request.projectId(), request.sourceFileId(), request.includeSource(), operator);
        if (impact.tasks().isEmpty()) throw new BadRequestException("当前任务没有可重跑的下游任务");
        LocalDate businessDate = request.businessDate() == null || request.businessDate().isBlank()
                ? LocalDate.now() : LocalDate.parse(request.businessDate().trim());
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO workflow_rerun_batch(project_id,source_file_id,business_date,scope,include_source,status,total_tasks,created_by) VALUES(?,?,?,?,?,'PENDING',?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, request.projectId()); ps.setLong(2, request.sourceFileId());
            ps.setDate(3, Date.valueOf(businessDate)); ps.setString(4, "ALL_DOWNSTREAM");
            ps.setBoolean(5, request.includeSource()); ps.setInt(6, impact.tasks().size()); ps.setString(7, operator);
            return ps;
        }, key);
        long batchId = Objects.requireNonNull(key.getKey()).longValue();
        int sequence = 1;
        for (ImpactTask task : impact.tasks()) {
            jdbc.update("INSERT INTO workflow_rerun_task(batch_id,file_id,sequence_no,status) VALUES(?,?,?,'PENDING')",
                    batchId, task.fileId(), sequence++);
        }
        CompletableFuture.runAsync(() -> executeBatch(batchId, request.projectId(), request.sourceFileId(), businessDate, operator));
        return batch(batchId, operator);
    }

    public RerunBatchView batch(long batchId) { return batch(batchId, "admin"); }

    public RerunBatchView batch(long batchId, String operator) {
        Map<String,Object> b = jdbc.queryForMap("SELECT * FROM workflow_rerun_batch WHERE id=?", batchId);
        requireProjectView(((Number)b.get("project_id")).longValue(), operator);
        List<RerunTaskView> tasks = jdbc.query("SELECT t.*,f.name FROM workflow_rerun_task t LEFT JOIN dev_file f ON f.id=t.file_id WHERE t.batch_id=? ORDER BY t.sequence_no",
                (rs,n) -> new RerunTaskView(rs.getLong("id"), rs.getObject("file_id") == null ? null : rs.getLong("file_id"),
                        stripExtension(rs.getString("name")), rs.getInt("sequence_no"), rs.getString("status"),
                        rs.getString("execution_id"), time(rs.getTimestamp("started_at")), time(rs.getTimestamp("finished_at")), rs.getString("error_message")), batchId);
        return new RerunBatchView(((Number)b.get("id")).longValue(), ((Number)b.get("project_id")).longValue(),
                ((Number)b.get("source_file_id")).longValue(), String.valueOf(b.get("business_date")),
                String.valueOf(b.get("status")), ((Number)b.get("total_tasks")).intValue(),
                ((Number)b.get("success_tasks")).intValue(), ((Number)b.get("failed_tasks")).intValue(),
                ((Number)b.get("waiting_tasks")).intValue(), String.valueOf(b.get("created_by")),
                timestamp(b.get("created_at")), timestamp(b.get("started_at")), timestamp(b.get("finished_at")), tasks);
    }
    private void executeBatch(long batchId, long projectId, long sourceFileId, LocalDate businessDate, String operator) {
        jdbc.update("UPDATE workflow_rerun_batch SET status='RUNNING',started_at=CURRENT_TIMESTAMP WHERE id=?", batchId);
        List<DevFileView> files = projectFiles(projectId);
        Map<Long, Set<Long>> upstreamByTarget = upstreamMap(files);
        Map<Long, String> taskStatus = new LinkedHashMap<>();
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT file_id,sequence_no FROM workflow_rerun_task WHERE batch_id=? ORDER BY sequence_no", batchId);
        Set<Long> batchFiles = rows.stream().map(r -> ((Number)r.get("file_id")).longValue()).collect(Collectors.toCollection(LinkedHashSet::new));
        for (Map<String,Object> row : rows) {
            long fileId = ((Number) row.get("file_id")).longValue();
            boolean blocked = upstreamByTarget.getOrDefault(fileId, Set.of()).stream()
                    .filter(batchFiles::contains).anyMatch(up -> !"SUCCESS".equals(taskStatus.get(up)));
            if (blocked) {
                taskStatus.put(fileId, "WAITING_DEPENDENCY");
                jdbc.update("UPDATE workflow_rerun_task SET status='WAITING_DEPENDENCY' WHERE batch_id=? AND file_id=?", batchId, fileId);
                continue;
            }
            String status = runOne(batchId, fileId, businessDate, operator);
            taskStatus.put(fileId, status);
        }
        refreshBatchCounters(batchId, true);
    }

    private String runOne(long batchId, long fileId, LocalDate businessDate, String operator) {
        try {
            jdbc.update("UPDATE workflow_rerun_task SET status='RUNNING',started_at=CURRENT_TIMESTAMP,error_message=NULL WHERE batch_id=? AND file_id=?", batchId, fileId);
            DevelopmentScheduleService.ScheduleRuntimeView started = schedules.runNowForBusinessDate(fileId, businessDate.toString(), operator);
            if (started.executionId() != null) jdbc.update("UPDATE workflow_rerun_task SET execution_id=? WHERE batch_id=? AND file_id=?", started.executionId(), batchId, fileId);
            while (true) {
                DevelopmentScheduleService.ScheduleRuntimeView current = schedules.runtime(fileId);
                String status = current.status() == null ? "RUNNING" : current.status().toUpperCase(Locale.ROOT);
                if (isRunning(status)) { Thread.sleep(500L); continue; }
                boolean success = "SUCCESS".equals(status);
                jdbc.update("UPDATE workflow_rerun_task SET status=?,finished_at=CURRENT_TIMESTAMP,error_message=? WHERE batch_id=? AND file_id=?",
                        success ? "SUCCESS" : "FAILED", current.errorMessage(), batchId, fileId);
                refreshBatchCounters(batchId, false);
                return success ? "SUCCESS" : "FAILED";
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failTask(batchId, fileId, "重跑线程被中断");
        } catch (RuntimeException ex) {
            return failTask(batchId, fileId, ex.getMessage());
        }
    }
    private String failTask(long batchId, long fileId, String message) {
        jdbc.update("UPDATE workflow_rerun_task SET status='FAILED',finished_at=CURRENT_TIMESTAMP,error_message=? WHERE batch_id=? AND file_id=?",
                message, batchId, fileId);
        refreshBatchCounters(batchId, false);
        return "FAILED";
    }

    private void refreshBatchCounters(long batchId, boolean finish) {
        Map<String,Object> c = jdbc.queryForMap("SELECT COUNT(*) total,SUM(status='SUCCESS') success_count,SUM(status='FAILED') failed_count,SUM(status='WAITING_DEPENDENCY') waiting_count FROM workflow_rerun_task WHERE batch_id=?", batchId);
        int total=((Number)c.get("total")).intValue(), success=number(c.get("success_count")), failed=number(c.get("failed_count")), waiting=number(c.get("waiting_count"));
        String status = finish ? (failed > 0 || waiting > 0 ? "FAILED" : "SUCCESS") : "RUNNING";
        jdbc.update("UPDATE workflow_rerun_batch SET status=?,total_tasks=?,success_tasks=?,failed_tasks=?,waiting_tasks=?,finished_at=? WHERE id=?",
                status,total,success,failed,waiting,finish?java.sql.Timestamp.valueOf(LocalDateTime.now()):null,batchId);
    }

    private List<DevFileView> projectFiles(long projectId) {
        return store.files.values().stream()
                .filter(f -> f.projectId() == projectId && "SQL".equalsIgnoreCase(f.fileType()))
                .sorted(Comparator.comparing(DevFileView::name, String.CASE_INSENSITIVE_ORDER).thenComparingLong(DevFileView::id))
                .toList();
    }

    private Map<Long, Set<Long>> upstreamMap(List<DevFileView> files) {
        Set<Long> ids = files.stream().map(DevFileView::id).collect(Collectors.toSet());
        Map<Long, Set<Long>> result = new LinkedHashMap<>();
        for (DevFileView file : files) {
            LinkedHashSet<Long> ups = schedules.get(file.id()).dependencies().stream().map(DevelopmentScheduleService.DependencyView::fileId)
                    .filter(ids::contains).collect(Collectors.toCollection(LinkedHashSet::new));
            result.put(file.id(), ups);
        }
        return result;
    }
    private Set<Long> descendants(long source, Map<Long, Set<Long>> upstreamByTarget) {
        Map<Long, Set<Long>> downstream = new HashMap<>();
        upstreamByTarget.forEach((target, ups) -> ups.forEach(up -> downstream.computeIfAbsent(up, x -> new LinkedHashSet<>()).add(target)));
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>(downstream.getOrDefault(source, Set.of()));
        while (!queue.isEmpty()) {
            long id = queue.removeFirst();
            if (!result.add(id)) continue;
            queue.addAll(downstream.getOrDefault(id, Set.of()));
        }
        return result;
    }

    private Map<Long,Integer> levelsFromSource(long source, Set<Long> impacted, Map<Long, Set<Long>> upstreamByTarget) {
        Map<Long,Integer> levels = new HashMap<>();
        levels.put(source, 0);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Long target : impacted) {
                int level = upstreamByTarget.getOrDefault(target, Set.of()).stream()
                        .mapToInt(up -> levels.getOrDefault(up, up == source ? 0 : -1)).max().orElse(-1) + 1;
                if (level > levels.getOrDefault(target, -1)) { levels.put(target, level); changed = true; }
            }
        }
        impacted.forEach(id -> levels.putIfAbsent(id, id == source ? 0 : 1));
        return levels;
    }

    private Map<Long,Integer> dependencyLevels(List<DevFileView> files, Map<Long, Set<Long>> upstreamByTarget) {
        Map<Long,Integer> levels = new HashMap<>();
        for (DevFileView file : files) levels.put(file.id(), 0);
        for (int i=0;i<files.size();i++) for (DevFileView file : files) {
            int level = upstreamByTarget.getOrDefault(file.id(), Set.of()).stream().mapToInt(up -> levels.getOrDefault(up,0)).max().orElse(-1)+1;
            levels.put(file.id(), Math.max(levels.getOrDefault(file.id(),0), Math.max(0,level)));
        }
        return levels;
    }
    private boolean canViewProject(long projectId, String operator) {
        return developmentAccess == null || developmentAccess.canProjectView(projectId, operator);
    }

    private void requireProjectView(long projectId, String operator) {
        if (developmentAccess != null) developmentAccess.requireProjectView(projectId, operator);
    }

    private void requireProjectEdit(long projectId, String operator) {
        if (developmentAccess != null) developmentAccess.requireProjectEdit(projectId, operator);
    }

    private DevProjectView requireProject(long projectId) {
        DevProjectView project = store.projects.get(projectId);
        if (project == null) throw new NotFoundException("开发项目不存在：" + projectId);
        return project;
    }

    private DevFileView requireProjectFile(long projectId, long fileId) {
        DevFileView file = store.files.get(fileId);
        if (file == null || file.projectId() != projectId || !"SQL".equalsIgnoreCase(file.fileType()))
            throw new NotFoundException("项目内开发任务不存在：" + fileId);
        return file;
    }

    private String safeRuntime(long fileId) {
        try { return schedules.runtime(fileId).status(); } catch (RuntimeException ex) { return "NEVER_RUN"; }
    }
    private boolean isRunning(String status) { return status != null && status.matches("(?i)RUNNING|STARTING|QUEUED|SUBMITTED"); }
    private boolean isFailed(String status) { return status != null && status.matches("(?i).*FAIL.*|ERROR"); }
    private int number(Object value) { return value == null ? 0 : ((Number)value).intValue(); }
    private LocalDateTime time(java.sql.Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private LocalDateTime timestamp(Object value) { return value instanceof java.sql.Timestamp t ? t.toLocalDateTime() : null; }
    private String stripExtension(String name) { return name == null ? "" : name.replaceFirst("(?i)\\.(sql|sh|py)$", ""); }

    public record ProjectWorkflowDefinition(long projectId,String name,String description,int taskCount,int enabledSchedules,int runningTasks,int failedTasks) {}
    public record ImpactTask(long fileId,String name,int level,String lifecycleStatus,String runtimeStatus) {}
    public record ImpactView(long projectId,long sourceFileId,String sourceName,boolean includeSource,List<ImpactTask> tasks) {}
    public record RerunRequest(long projectId,long sourceFileId,boolean includeSource,String businessDate) {}
    public record RerunTaskView(long id,Long fileId,String name,int sequenceNo,String status,String executionId,LocalDateTime startedAt,LocalDateTime finishedAt,String errorMessage) {}
    public record RerunBatchView(long id,long projectId,long sourceFileId,String businessDate,String status,int totalTasks,int successTasks,int failedTasks,int waitingTasks,String createdBy,LocalDateTime createdAt,LocalDateTime startedAt,LocalDateTime finishedAt,List<RerunTaskView> tasks) {}
}
