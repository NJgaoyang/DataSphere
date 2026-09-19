package com.company.platform.workflow;

import com.company.platform.common.PlatformStore;
import com.company.platform.scheduler.SchedulerGateway;
import com.company.platform.lineage.LineageService;
import com.company.platform.lineage.SqlLineageParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowPublishServiceTest {
    @Test
    void publishesThroughGatewayWithoutExternalService() {
        PlatformStore store = new PlatformStore();
        WorkflowService workflowService = new WorkflowService(store, new DagValidator());
        var file = store.files.values().iterator().next();
        var version = store.versions.values().iterator().next();
        store.versions.put(version.id(), new com.company.platform.development.FileVersionView(version.id(), version.fileId(), version.versionNo(), version.content(), version.checksum(), true));
        // Intentionally send the wrong UI node type. The bound development file is .sql/SQL,
        // so publishing must derive the scheduler task type from the file instead of trusting UI state.
        WorkflowView workflow = workflowService.create(new WorkflowRequests.WorkflowRequest("sales_daily", "demo",
                List.of(new WorkflowRequests.NodeRequest("daily sales", NodeType.SHELL, file.id(), null, 0, 0)), List.of()));
        AtomicReference<SchedulerGateway.PublishRequest> published = new AtomicReference<>();
        AtomicBoolean releasedOnline = new AtomicBoolean(false);
        SchedulerGateway gateway = new SchedulerGateway() {
            @Override public PublishResult publish(PublishRequest request) {
                published.set(request);
                return new PublishResult("test-process-1", request.version(), "PUBLISHED");
            }
            @Override public void release(String processCode, boolean online) {
                if ("test-process-1".equals(processCode) && online) releasedOnline.set(true);
            }
            @Override public RunResult run(String processCode) { return new RunResult("test-instance-1", "RUNNING"); }
            @Override public InstanceStatus status(String instanceId) { return new InstanceStatus(instanceId, "RUNNING", ""); }
            @Override public void stop(String instanceId) { }
            @Override public RunResult rerun(String instanceId) { return new RunResult(instanceId, "RUNNING"); }
            @Override public RunResult backfill(String processCode, String start, String end, int parallelism) {
                return new RunResult("test-instance-1", "RUNNING");
            }
        };
        WorkflowPublishService service = new WorkflowPublishService(workflowService, gateway, store,
                new LineageService(store, new SqlLineageParser()));
        assertEquals("PUBLISHED", service.publish(workflow.id()).status());
        assertTrue(published.get().definitionJson().contains("\"type\":\"SQL\""));
        assertTrue(releasedOnline.get(), "publish must release the DolphinScheduler definition ONLINE");
    }
    @Test
    void conditionNodeRequiresTrueAndFalseBranchesAndPublishesBranchTypes() {
        PlatformStore store = new PlatformStore();
        WorkflowService workflows = new WorkflowService(store, new DagValidator());
        WorkflowView workflow = workflows.create(new WorkflowRequests.WorkflowRequest("condition_flow", "demo", List.of(
                new WorkflowRequests.NodeRequest("条件", NodeType.CONDITION, null, "{\"expression\":\"true\"}", 0, 0, "cond"),
                new WorkflowRequests.NodeRequest("成功分支", NodeType.SEATUNNEL, null, "{}", 100, 0, "yes"),
                new WorkflowRequests.NodeRequest("失败分支", NodeType.SEATUNNEL, null, "{}", 100, 100, "no")
        ), List.of(
                new WorkflowRequests.EdgeRequest(null,null,"cond","yes","TRUE"),
                new WorkflowRequests.EdgeRequest(null,null,"cond","no","FALSE")
        )));
        AtomicReference<SchedulerGateway.PublishRequest> published = new AtomicReference<>();
        SchedulerGateway gateway = gateway(published);
        WorkflowPublishService service = new WorkflowPublishService(workflows, gateway, store, new LineageService(store, new SqlLineageParser()));

        service.publish(workflow.id());
        assertTrue(published.get().definitionJson().contains("\"branchType\":\"TRUE\""));
        assertTrue(published.get().definitionJson().contains("\"branchType\":\"FALSE\""));

        WorkflowView invalid = workflows.create(new WorkflowRequests.WorkflowRequest("invalid_condition", "demo", List.of(
                new WorkflowRequests.NodeRequest("条件", NodeType.CONDITION, null, "{\"expression\":\"true\"}", 0, 0, "cond2"),
                new WorkflowRequests.NodeRequest("only", NodeType.SEATUNNEL, null, "{}", 100, 0, "only")
        ), List.of(new WorkflowRequests.EdgeRequest(null,null,"cond2","only","TRUE"))));
        assertThrows(com.company.platform.common.BadRequestException.class, () -> service.publish(invalid.id()));
    }

    private static SchedulerGateway gateway(AtomicReference<SchedulerGateway.PublishRequest> published) {
        return new SchedulerGateway() {
            @Override public PublishResult publish(PublishRequest request) { published.set(request); return new PublishResult("condition-process", request.version(), "PUBLISHED"); }
            @Override public void release(String processCode, boolean online) { }
            @Override public RunResult run(String processCode) { return new RunResult("instance", "RUNNING"); }
            @Override public InstanceStatus status(String instanceId) { return new InstanceStatus(instanceId, "RUNNING", ""); }
            @Override public void stop(String instanceId) { }
            @Override public RunResult rerun(String instanceId) { return new RunResult(instanceId, "RUNNING"); }
            @Override public RunResult backfill(String processCode, String start, String end, int parallelism) { return new RunResult("instance", "RUNNING"); }
        };
    }

}
