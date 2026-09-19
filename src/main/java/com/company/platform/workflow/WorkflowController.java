package com.company.platform.workflow;

import com.company.platform.common.Result;
import com.company.platform.scheduler.ScheduleRequests;
import com.company.platform.scheduler.SchedulerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowService service;
    private final WorkflowPublishService publishService;
    private final SchedulerService schedulerService;
    private final ProjectWorkflowService projectWorkflowService;
    public WorkflowController(WorkflowService service, WorkflowPublishService publishService, SchedulerService schedulerService, ProjectWorkflowService projectWorkflowService) {
        this.service = service; this.publishService = publishService; this.schedulerService = schedulerService; this.projectWorkflowService = projectWorkflowService;
    }
    @GetMapping public Result<List<WorkflowView>> list(HttpServletRequest request) { return Result.ok(service.list(operator(request))); }
    @GetMapping("/development-definitions") public Result<List<WorkflowService.DevelopmentWorkflowDefinition>> developmentDefinitions(HttpServletRequest request) { return Result.ok(service.developmentDefinitions(operator(request))); }
    @GetMapping("/project-definitions") public Result<List<ProjectWorkflowService.ProjectWorkflowDefinition>> projectDefinitions(HttpServletRequest request) { return Result.ok(projectWorkflowService.definitions(operator(request))); }
    @GetMapping("/project-graph/{projectId}") public Result<WorkflowView> projectGraph(@PathVariable long projectId, HttpServletRequest request) { return Result.ok(projectWorkflowService.graph(projectId, operator(request))); }
    @GetMapping("/project-impact/{projectId}/{fileId}") public Result<ProjectWorkflowService.ImpactView> projectImpact(@PathVariable long projectId, @PathVariable long fileId, @RequestParam(defaultValue="false") boolean includeSource, HttpServletRequest request) { return Result.ok(projectWorkflowService.impact(projectId,fileId,includeSource,operator(request))); }
    @GetMapping("/project-impact-integration/{projectId}/{taskId}") public Result<ProjectWorkflowService.ImpactView> projectIntegrationImpact(@PathVariable long projectId, @PathVariable long taskId, @RequestParam(defaultValue="false") boolean includeSource, HttpServletRequest request) { return Result.ok(projectWorkflowService.impactIntegration(projectId,taskId,includeSource,operator(request))); }
    @PostMapping("/project-reruns") public Result<ProjectWorkflowService.RerunBatchView> startProjectRerun(@RequestBody ProjectWorkflowService.RerunRequest request, HttpServletRequest servletRequest) { return Result.ok(projectWorkflowService.startRerun(request,operator(servletRequest)), "下游重跑批次已创建"); }
    @GetMapping("/project-reruns") public Result<List<ProjectWorkflowService.RerunBatchView>> projectReruns(@RequestParam long projectId, @RequestParam(defaultValue="30") int limit, HttpServletRequest request) { return Result.ok(projectWorkflowService.history(projectId,limit,operator(request))); }
    @GetMapping("/project-reruns/{batchId}") public Result<ProjectWorkflowService.RerunBatchView> projectRerun(@PathVariable long batchId, HttpServletRequest request) { return Result.ok(projectWorkflowService.batch(batchId, operator(request))); }
    @PostMapping("/project-reruns/{batchId}/cancel") public Result<ProjectWorkflowService.RerunBatchView> cancelProjectRerun(@PathVariable long batchId, HttpServletRequest request) { return Result.ok(projectWorkflowService.cancel(batchId,operator(request)), "重跑批次已请求取消"); }
    @PostMapping("/project-reruns/{batchId}/retry") public Result<ProjectWorkflowService.RerunBatchView> retryProjectRerun(@PathVariable long batchId, HttpServletRequest request) { return Result.ok(projectWorkflowService.retryFailed(batchId,operator(request)), "失败分支重试批次已创建"); }
    @GetMapping("/development-graph/{fileId}") public Result<WorkflowView> developmentGraph(@PathVariable long fileId, HttpServletRequest request) { return Result.ok(service.developmentGraph(fileId, operator(request))); }
    @PutMapping("/development-graph/{fileId}") public Result<WorkflowView> updateDevelopmentGraph(@PathVariable long fileId, @Valid @RequestBody WorkflowRequests.WorkflowRequest request, HttpServletRequest servletRequest) {
        return Result.ok(service.updateDevelopmentGraph(fileId, request, operator(servletRequest)), "开发任务依赖已同步");
    }
    @PostMapping public Result<WorkflowView> create(@Valid @RequestBody WorkflowRequests.WorkflowRequest request, HttpServletRequest servletRequest) { return Result.ok(service.create(request, operator(servletRequest))); }
    @PutMapping("/{id}") public Result<WorkflowView> update(@PathVariable long id, @Valid @RequestBody WorkflowRequests.WorkflowRequest request, HttpServletRequest servletRequest) { return Result.ok(service.update(id, request, operator(servletRequest)), "工作流已保存"); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id, HttpServletRequest request) { service.delete(id, operator(request)); return Result.ok(null, "工作流已删除"); }
    @PutMapping("/{id}/graph") public Result<WorkflowView> updateGraph(@PathVariable long id, @Valid @RequestBody WorkflowRequests.WorkflowRequest request, HttpServletRequest servletRequest) { return Result.ok(service.update(id, request, operator(servletRequest)), "工作流画布已保存"); }
    @GetMapping("/{id}") public Result<WorkflowView> get(@PathVariable long id, HttpServletRequest request) { return Result.ok(service.get(id, operator(request))); }
    @PostMapping("/{id}/validate") public Result<DagValidator.ValidationResult> validate(@PathVariable long id, HttpServletRequest request) { return Result.ok(service.validate(id, operator(request))); }
    @PostMapping("/{id}/publish") public Result<WorkflowPublishService.PublishResult> publish(@PathVariable long id, HttpServletRequest request) { return Result.ok(publishService.publish(id, operator(request))); }
    @PostMapping("/{id}/run") public Result<WorkflowPublishService.RunResult> run(@PathVariable long id, HttpServletRequest request) { return Result.ok(publishService.run(id, operator(request))); }
    @PostMapping("/{id}/backfill") public Result<com.company.platform.scheduler.SchedulerGateway.RunResult> backfill(@PathVariable long id, @Valid @RequestBody ScheduleRequests.BackfillRequest request, HttpServletRequest servletRequest) { return Result.ok(schedulerService.backfill(id, request, operator(servletRequest)), "补数据任务已提交"); }

    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
}
