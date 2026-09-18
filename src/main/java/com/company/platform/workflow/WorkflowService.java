package com.company.platform.workflow;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.development.DevelopmentScheduleService;
import com.company.platform.development.DevFileView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class WorkflowService {
    private final PlatformStore store;
    private final DagValidator validator;
    private DevelopmentScheduleService developmentSchedules;

    public WorkflowService(PlatformStore store, DagValidator validator) {
        this.store = store;
        this.validator = validator;
    }

    @Autowired(required = false)
    public void setDevelopmentSchedules(DevelopmentScheduleService developmentSchedules) {
        this.developmentSchedules = developmentSchedules;
    }

    public List<WorkflowView> list() {
        return store.workflows.values().stream().map(this::withSharedTaskDependencies).toList();
    }

    /**
     * System shared DAG for SQL development tasks. The graph is projected directly from
     * dev_file_schedule_dependency, so Data Development and Workflow always show one source of truth.
     */
    public WorkflowView developmentGraph() {
        List<DevFileView> files = store.files.values().stream()
                .filter(file -> "SQL".equalsIgnoreCase(file.fileType()))
                .sorted(Comparator.comparing(DevFileView::name, String.CASE_INSENSITIVE_ORDER).thenComparingLong(DevFileView::id))
                .toList();
        if (files.isEmpty()) return new WorkflowView(0, "开发任务依赖图", "development_task_graph",
                "由数据开发调度依赖自动生成", "SYNCED", 0, List.of(), List.of());

        Set<Long> fileIds = new LinkedHashSet<>();
        files.forEach(file -> fileIds.add(file.id()));
        Map<Long, Set<Long>> upstreamByTarget = new LinkedHashMap<>();
        List<WorkflowEdgeView> edges = new ArrayList<>();
        long edgeId = -1L;
        if (developmentSchedules != null) {
            for (DevFileView target : files) {
                LinkedHashSet<Long> upstreams = new LinkedHashSet<>();
                for (DevelopmentScheduleService.DependencyView dependency : developmentSchedules.get(target.id()).dependencies()) {
                    if (!fileIds.contains(dependency.fileId())) continue;
                    upstreams.add(dependency.fileId());
                    edges.add(new WorkflowEdgeView(edgeId--, dependency.fileId(), target.id()));
                }
                upstreamByTarget.put(target.id(), upstreams);
            }
        }

        Map<Long, Integer> levels = dependencyLevels(files, upstreamByTarget);
        Map<Integer, Integer> rowByLevel = new HashMap<>();
        List<WorkflowNodeView> nodes = new ArrayList<>();
        for (DevFileView file : files) {
            int level = levels.getOrDefault(file.id(), 0);
            int row = rowByLevel.merge(level, 1, Integer::sum) - 1;
            nodes.add(new WorkflowNodeView(file.id(), stripExtension(file.name()), NodeType.SQL, file.id(), "{}",
                    90 + level * 270, 75 + row * 115, "task_" + file.id()));
        }
        return new WorkflowView(0, "开发任务依赖图", "development_task_graph",
                "由数据开发调度依赖自动生成，修改依赖后会同步回数据开发", "SYNCED", 0, nodes, edges);
    }

    @Transactional
    public WorkflowView updateDevelopmentGraph(WorkflowRequests.WorkflowRequest request, String operator) {
        if (developmentSchedules == null) return developmentGraph();
        Map<String, Long> fileByCode = new LinkedHashMap<>();
        store.files.values().stream().filter(file -> "SQL".equalsIgnoreCase(file.fileType()))
                .forEach(file -> fileByCode.put("task_" + file.id(), file.id()));
        Map<Long, LinkedHashSet<Long>> desired = new LinkedHashMap<>();
        fileByCode.values().forEach(id -> desired.put(id, new LinkedHashSet<>()));
        if (request.edges() != null) {
            for (WorkflowRequests.EdgeRequest edge : request.edges()) {
                Long source = edge.sourceNodeCode() == null ? null : fileByCode.get(edge.sourceNodeCode());
                Long target = edge.targetNodeCode() == null ? null : fileByCode.get(edge.targetNodeCode());
                if (source == null || target == null) throw new BadRequestException("开发任务依赖图只能连接数据开发 SQL 任务");
                if (Objects.equals(source, target)) throw new BadRequestException("任务不能依赖自身");
                desired.get(target).add(source);
            }
        }
        for (Map.Entry<Long, LinkedHashSet<Long>> entry : desired.entrySet()) {
            developmentSchedules.replaceUpstreamsFromWorkflow(entry.getKey(), new ArrayList<>(entry.getValue()), operator);
        }
        return developmentGraph();
    }

    private Map<Long, Integer> dependencyLevels(List<DevFileView> files, Map<Long, Set<Long>> upstreamByTarget) {
        Map<Long, Integer> indegree = new LinkedHashMap<>();
        Map<Long, List<Long>> downstream = new HashMap<>();
        files.forEach(file -> indegree.put(file.id(), 0));
        for (Map.Entry<Long, Set<Long>> entry : upstreamByTarget.entrySet()) {
            for (Long upstream : entry.getValue()) {
                if (!indegree.containsKey(upstream) || !indegree.containsKey(entry.getKey())) continue;
                indegree.put(entry.getKey(), indegree.get(entry.getKey()) + 1);
                downstream.computeIfAbsent(upstream, ignored -> new ArrayList<>()).add(entry.getKey());
            }
        }
        Deque<Long> queue = new ArrayDeque<>();
        indegree.forEach((id, degree) -> { if (degree == 0) queue.add(id); });
        Map<Long, Integer> levels = new HashMap<>();
        queue.forEach(id -> levels.put(id, 0));
        while (!queue.isEmpty()) {
            long id = queue.removeFirst();
            int level = levels.getOrDefault(id, 0);
            for (Long target : downstream.getOrDefault(id, List.of())) {
                levels.put(target, Math.max(levels.getOrDefault(target, 0), level + 1));
                int next = indegree.get(target) - 1;
                indegree.put(target, next);
                if (next == 0) queue.addLast(target);
            }
        }
        files.forEach(file -> levels.putIfAbsent(file.id(), 0));
        return levels;
    }

    private String stripExtension(String name) {
        return name == null ? "" : name.replaceFirst("(?i)\\.(sql|sh|py)$", "");
    }

    public WorkflowView create(WorkflowRequests.WorkflowRequest request) { return create(request, "admin"); }

    @Transactional
    public WorkflowView create(WorkflowRequests.WorkflowRequest request, String operator) {
        long workflowId = store.nextId();
        GraphDraft graph = buildGraph(request, null);
        validateGraph(graph);
        synchronizeTaskDependencies(graph.nodes(), graph.edges(), operator);
        WorkflowView view = new WorkflowView(workflowId, request.name(), "wf_" + UUID.randomUUID().toString().replace("-", ""),
                request.description(), "DRAFT", 0, graph.nodes(), orchestrationEdges(graph.nodes(), graph.edges()), null, LocalDateTime.now());
        store.persistWorkflow(view);
        store.workflows.put(workflowId, view);
        return withSharedTaskDependencies(view);
    }

    public WorkflowView update(long id, WorkflowRequests.WorkflowRequest request) { return update(id, request, "admin"); }

    @Transactional
    public WorkflowView update(long id, WorkflowRequests.WorkflowRequest request, String operator) {
        WorkflowView current = rawGet(id);
        GraphDraft graph = buildGraph(request, current);
        validateGraph(graph);
        synchronizeTaskDependencies(graph.nodes(), graph.edges(), operator);
        WorkflowView view = new WorkflowView(id, request.name(), current.workflowCode(), request.description(), "DRAFT",
                current.publishedVersion(), graph.nodes(), orchestrationEdges(graph.nodes(), graph.edges()), current.dsProcessCode(), LocalDateTime.now());
        store.persistWorkflow(view);
        store.workflows.put(id, view);
        return withSharedTaskDependencies(view);
    }

    @Transactional
    public void delete(long id) {
        WorkflowView workflow = rawGet(id);
        if ("PUBLISHED".equals(workflow.status())) throw new BadRequestException("已发布工作流不能直接删除，请先下线");
        store.deleteWorkflow(id);
        store.workflows.remove(id);
    }

    public WorkflowView get(long id) { return withSharedTaskDependencies(rawGet(id)); }

    private WorkflowView rawGet(long id) {
        WorkflowView view = store.workflows.get(id);
        if (view == null) throw new NotFoundException("工作流不存在：" + id);
        return view;
    }

    public DagValidator.ValidationResult validate(long id) {
        WorkflowView view = get(id);
        return validator.validate(view.nodes(), view.edges());
    }

    private void validateGraph(GraphDraft graph) {
        DagValidator.ValidationResult validation = validator.validate(graph.nodes(), graph.edges());
        if (!validation.valid()) throw new BadRequestException(validation.message());
    }

    private GraphDraft buildGraph(WorkflowRequests.WorkflowRequest request, WorkflowView current) {
        Map<String, Long> nodeIds = new HashMap<>();
        List<WorkflowNodeView> nodes = request.nodes() == null ? List.of() : request.nodes().stream()
                .map(node -> {
                    String nodeCode = node.nodeCode() == null || node.nodeCode().isBlank()
                            ? "node_" + UUID.randomUUID().toString().replace("-", "") : node.nodeCode();
                    long nodeId = current == null ? store.nextId() : current.nodes().stream()
                            .filter(existing -> nodeCode.equals(existing.nodeCode())).map(WorkflowNodeView::id)
                            .findFirst().orElseGet(store::nextId);
                    if (nodeIds.putIfAbsent(nodeCode, nodeId) != null) throw new BadRequestException("工作流节点编码重复：" + nodeCode);
                    Long devFileId = node.devFileId();
                    if (devFileId != null && !store.files.containsKey(devFileId)) throw new BadRequestException("节点“" + node.name() + "”绑定的开发任务不存在");
                    return new WorkflowNodeView(nodeId, node.name(), node.nodeType() == null ? NodeType.SQL : node.nodeType(),
                            devFileId, node.configJson(), node.x(), node.y(), nodeCode);
                }).toList();
        List<WorkflowEdgeView> edges = request.edges() == null ? List.of() : request.edges().stream()
                .map(edge -> new WorkflowEdgeView(store.nextId(), resolveNodeId(edge.sourceNodeId(), edge.sourceNodeCode(), nodeIds),
                        resolveNodeId(edge.targetNodeId(), edge.targetNodeCode(), nodeIds))).toList();
        return new GraphDraft(nodes, edges);
    }

    private long resolveNodeId(Long nodeId, String nodeCode, Map<String, Long> nodeIds) {
        if (nodeCode != null && !nodeCode.isBlank()) return nodeIds.getOrDefault(nodeCode, 0L);
        return nodeId == null ? 0L : nodeId;
    }

    private boolean sharedTaskNode(WorkflowNodeView node) {
        return node != null && node.devFileId() != null && node.nodeType() == NodeType.SQL;
    }

    private List<WorkflowEdgeView> orchestrationEdges(List<WorkflowNodeView> nodes, List<WorkflowEdgeView> edges) {
        Map<Long, WorkflowNodeView> byId = new HashMap<>();
        nodes.forEach(node -> byId.put(node.id(), node));
        return edges.stream().filter(edge -> !(sharedTaskNode(byId.get(edge.sourceNodeId())) && sharedTaskNode(byId.get(edge.targetNodeId())))).toList();
    }

    private void synchronizeTaskDependencies(List<WorkflowNodeView> nodes, List<WorkflowEdgeView> edges, String operator) {
        if (developmentSchedules == null) return;
        Map<Long, WorkflowNodeView> byNodeId = new HashMap<>();
        Map<Long, WorkflowNodeView> byFileId = new HashMap<>();
        for (WorkflowNodeView node : nodes) {
            byNodeId.put(node.id(), node);
            if (sharedTaskNode(node)) byFileId.put(node.devFileId(), node);
        }
        Set<Long> managedFileIds = byFileId.keySet();
        Map<Long, Set<Long>> desiredInternal = new HashMap<>();
        for (WorkflowEdgeView edge : edges) {
            WorkflowNodeView source = byNodeId.get(edge.sourceNodeId());
            WorkflowNodeView target = byNodeId.get(edge.targetNodeId());
            if (sharedTaskNode(source) && sharedTaskNode(target)) {
                desiredInternal.computeIfAbsent(target.devFileId(), ignored -> new LinkedHashSet<>()).add(source.devFileId());
            }
        }
        for (WorkflowNodeView downstream : byFileId.values()) {
            DevelopmentScheduleService.ScheduleView current = developmentSchedules.get(downstream.devFileId());
            LinkedHashSet<Long> next = current.dependencies().stream().map(DevelopmentScheduleService.DependencyView::fileId)
                    .filter(id -> !managedFileIds.contains(id)).collect(LinkedHashSet::new, Set::add, Set::addAll);
            next.addAll(desiredInternal.getOrDefault(downstream.devFileId(), Set.of()));
            developmentSchedules.replaceUpstreamsFromWorkflow(downstream.devFileId(), new ArrayList<>(next), operator);
        }
    }

    private WorkflowView withSharedTaskDependencies(WorkflowView view) {
        if (developmentSchedules == null || view.nodes() == null || view.nodes().isEmpty()) return view;
        Map<Long, WorkflowNodeView> byFileId = new HashMap<>();
        Map<Long, WorkflowNodeView> byNodeId = new HashMap<>();
        for (WorkflowNodeView node : view.nodes()) {
            byNodeId.put(node.id(), node);
            if (sharedTaskNode(node)) byFileId.put(node.devFileId(), node);
        }
        List<WorkflowEdgeView> merged = new ArrayList<>();
        for (WorkflowEdgeView edge : view.edges()) {
            WorkflowNodeView source = byNodeId.get(edge.sourceNodeId());
            WorkflowNodeView target = byNodeId.get(edge.targetNodeId());
            if (!(sharedTaskNode(source) && sharedTaskNode(target))) merged.add(edge);
        }
        long syntheticId = -1L;
        Set<String> seen = new HashSet<>();
        for (WorkflowNodeView target : byFileId.values()) {
            DevelopmentScheduleService.ScheduleView schedule = developmentSchedules.get(target.devFileId());
            for (DevelopmentScheduleService.DependencyView dependency : schedule.dependencies()) {
                WorkflowNodeView source = byFileId.get(dependency.fileId());
                if (source == null) continue;
                String key = source.id() + ":" + target.id();
                if (seen.add(key)) merged.add(new WorkflowEdgeView(syntheticId--, source.id(), target.id()));
            }
        }
        return new WorkflowView(view.id(), view.name(), view.workflowCode(), view.description(), view.status(),
                view.publishedVersion(), view.nodes(), merged, view.dsProcessCode(), view.updatedAt());
    }

    private record GraphDraft(List<WorkflowNodeView> nodes, List<WorkflowEdgeView> edges) { }
}
