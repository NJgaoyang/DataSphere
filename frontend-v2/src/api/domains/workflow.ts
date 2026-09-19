import { api } from '../http'

export interface WorkflowNode { id:number; name:string; nodeType:'SQL'|'PYTHON'|'SHELL'|'SEATUNNEL'|'CONDITION'; devFileId?:number; configJson?:string; x:number; y:number; nodeCode:string }
export interface WorkflowEdge { id:number; sourceNodeId:number; targetNodeId:number; branchType?:'NORMAL'|'TRUE'|'FALSE' }
export interface WorkflowView { id:number; name:string; workflowCode:string; description?:string; status:string; publishedVersion:number; nodes:WorkflowNode[]; edges:WorkflowEdge[]; dsProcessCode?:string; updatedAt?:string }
export interface DevelopmentWorkflowDefinition { fileId:number; name:string; workflowCode:string; status:string; lifecycleStatus:string; currentVersion:number; ownerName:string; scheduleEnabled:boolean; cycleType:string; executionTime:string; cronExpression:string; timezone:string; upstreamCount:number; downstreamCount:number; runtimeStatus?:string; plannedAt?:string; startedAt?:string; finishedAt?:string; nextPlannedAt?:string; updatedAt?:string }
export interface ProjectWorkflowDefinition { projectId:number; name:string; description?:string; taskCount:number; enabledSchedules:number; runningTasks:number; failedTasks:number }
export interface ProjectImpactTask { fileId:number; name:string; fileType:string; level:number; lifecycleStatus:string; runtimeStatus:string }
export interface ProjectImpactView { projectId:number; sourceFileId?:number; sourceIntegrationTaskId?:number; sourceName:string; includeSource:boolean; tasks:ProjectImpactTask[] }
export interface ProjectRerunTask { id:number; fileId?:number; name:string; fileType?:string; sequenceNo:number; status:string; executionId?:string; startedAt?:string; finishedAt?:string; errorMessage?:string; outputLog?:string }
export interface ProjectRerunBatch { id:number; projectId:number; sourceFileId?:number; sourceIntegrationTaskId?:number; parentBatchId?:number; businessDate:string; status:string; cancelRequested:boolean; sourceStatus?:string; sourceExecutionId?:string; sourceErrorMessage?:string; totalTasks:number; successTasks:number; failedTasks:number; waitingTasks:number; createdBy:string; createdAt?:string; startedAt?:string; finishedAt?:string; tasks:ProjectRerunTask[] }
export interface WorkflowPayload { name:string; description?:string; nodes:Array<{name:string;nodeType:string;devFileId?:number;configJson?:string;x:number;y:number;nodeCode:string}>; edges:Array<{sourceNodeCode:string;targetNodeCode:string;branchType?:'NORMAL'|'TRUE'|'FALSE'}> }
export interface ScheduleConfig { id:number; workflowId:number; cronExpression:string; timezone:string; enabled:boolean; failureStrategy:string; parallelism:number; workerGroup?:string; alertGroup?:string }
export const workflowApi = {
  list: () => api.get<WorkflowView[]>('/workflows'),
  developmentDefinitions: () => api.get<DevelopmentWorkflowDefinition[]>('/workflows/development-definitions'),
  projectDefinitions: () => api.get<ProjectWorkflowDefinition[]>('/workflows/project-definitions'),
  projectGraph: (projectId:number) => api.get<WorkflowView>(`/workflows/project-graph/${projectId}`),
  projectImpact: (projectId:number,fileId:number,includeSource=false) => api.get<ProjectImpactView>(`/workflows/project-impact/${projectId}/${fileId}`,{params:{includeSource}}),
  projectImpactIntegration: (projectId:number,taskId:number,includeSource=false) => api.get<ProjectImpactView>(`/workflows/project-impact-integration/${projectId}/${taskId}`,{params:{includeSource}}),
  startProjectRerun: (payload:{projectId:number;sourceFileId?:number;sourceIntegrationTaskId?:number;includeSource:boolean;businessDate:string}) => api.post<ProjectRerunBatch>('/workflows/project-reruns',payload),
  projectReruns: (projectId:number,limit=30) => api.get<ProjectRerunBatch[]>('/workflows/project-reruns',{params:{projectId,limit}}),
  projectRerun: (batchId:number) => api.get<ProjectRerunBatch>(`/workflows/project-reruns/${batchId}`),
  cancelProjectRerun: (batchId:number) => api.post<ProjectRerunBatch>(`/workflows/project-reruns/${batchId}/cancel`),
  retryProjectRerun: (batchId:number) => api.post<ProjectRerunBatch>(`/workflows/project-reruns/${batchId}/retry`),
  developmentGraph: (fileId:number) => api.get<WorkflowView>(`/workflows/development-graph/${fileId}`),
  saveDevelopmentGraph: (fileId:number,payload:WorkflowPayload) => api.put<WorkflowView>(`/workflows/development-graph/${fileId}`,payload),
  get: (id:number) => api.get<WorkflowView>(`/workflows/${id}`),
  create: (payload:WorkflowPayload) => api.post<WorkflowView>('/workflows',payload),
  update: (id:number,payload:WorkflowPayload) => api.put<WorkflowView>(`/workflows/${id}`,payload),
  remove: (id:number) => api.delete<void>(`/workflows/${id}`),
  run: (id:number) => api.post<{instanceId:string;status:string;message:string}>(`/workflows/${id}/run`),
  schedule: (id:number) => api.get<ScheduleConfig>(`/workflows/${id}/schedule`),
  saveSchedule: (id:number,payload:ScheduleConfig) => api.put<ScheduleConfig>(`/workflows/${id}/schedule`,payload),
  online: (id:number) => api.post<ScheduleConfig>(`/workflows/${id}/schedule/online`),
  offline: (id:number) => api.post<ScheduleConfig>(`/workflows/${id}/schedule/offline`)
}
