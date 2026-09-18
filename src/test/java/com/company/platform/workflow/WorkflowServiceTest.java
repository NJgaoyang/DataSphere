package com.company.platform.workflow;

import com.company.platform.common.PlatformStore;
import com.company.platform.development.DevFileView;
import com.company.platform.development.DevelopmentScheduleService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowServiceTest {
    @Test
    void mapsCanvasNodeCodesToPersistedIds() {
        PlatformStore store = new PlatformStore();
        WorkflowService service = new WorkflowService(store, new DagValidator());
        WorkflowView workflow = service.create(new WorkflowRequests.WorkflowRequest(
                "canvas", "x6", List.of(
                new WorkflowRequests.NodeRequest("source", NodeType.SQL, null, null, 10, 20, "source"),
                new WorkflowRequests.NodeRequest("target", NodeType.SQL, null, null, 30, 20, "target")),
                List.of(new WorkflowRequests.EdgeRequest(null, null, "source", "target"))));

        assertEquals(2, workflow.nodes().size());
        assertEquals(workflow.nodes().get(0).id(), workflow.edges().get(0).sourceNodeId());
        assertEquals(workflow.nodes().get(1).id(), workflow.edges().get(0).targetNodeId());
    }
    @Test
    void developmentGraphProjectsSharedScheduleDependencies() {
        PlatformStore store = new PlatformStore();
        store.files.clear();
        store.files.put(11L, new DevFileView(11, 1, null, "dim_city.sql", "SQL", "select 1", "",
                "PUBLISHED", 2, null, "ONLINE", true, "admin"));
        store.files.put(22L, new DevFileView(22, 1, null, "dwd_city.sql", "SQL", "select 2", "",
                "PUBLISHED", 3, null, "ONLINE", true, "admin"));
        DevelopmentScheduleService schedules = mock(DevelopmentScheduleService.class);
        when(schedules.get(11L)).thenReturn(schedule(11L, List.of()));
        when(schedules.get(22L)).thenReturn(schedule(22L, List.of(new DevelopmentScheduleService.DependencyView(11L, "dim_city.sql"))));

        WorkflowService service = new WorkflowService(store, new DagValidator());
        service.setDevelopmentSchedules(schedules);
        WorkflowView graph = service.developmentGraph();

        assertEquals(2, graph.nodes().size());
        assertEquals(1, graph.edges().size());
        assertEquals(11L, graph.edges().getFirst().sourceNodeId());
        assertEquals(22L, graph.edges().getFirst().targetNodeId());
        assertTrue(graph.nodes().stream().allMatch(node -> node.devFileId() != null));
    }

    private DevelopmentScheduleService.ScheduleView schedule(long fileId, List<DevelopmentScheduleService.DependencyView> dependencies) {
        return new DevelopmentScheduleService.ScheduleView(fileId, 1, 1, true, "DAILY", "02:00",
                "0 0 2 * * ?", "Asia/Shanghai", null, "ods", "${system.biz.date-1}", 3, 5, 120,
                dependencies, List.of(), 1, 1);
    }

}
