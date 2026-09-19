import { api } from '../http'

export interface OperationSummary { total:number; running:number; success:number; failed:number; stopped:number }
export interface OperationTask { type:string; id:number; name:string; engine:string; lifecycleStatus:string; runtimeStatus:string; owner:string; lastExecutionId?:string; lastStartedAt?:string; lastFinishedAt?:string; errorMessage?:string; canStart:boolean; canRerun:boolean; canKill:boolean }
export interface OperationTaskAction { type:string; id:number; executionId?:string; status:string }
export interface OperationInstance { type:string; id:string; externalId?:string; name:string; status:string; engine:string; createdBy?:string; startedAt?:string; finishedAt?:string; errorMessage?:string; createdAt?:string }
export interface FailureItem { type:string; id:string; parentInstanceId?:string; name:string; engine:string; status:string; attemptNo:number; errorMessage?:string; startedAt?:string }
export interface AlertItem { alertType:string; resourceType:string; resourceId:string; name:string; status:string; message?:string; occurredAt?:string; handlingState:string }
export interface SystemMetrics { cpuUsage:number; memoryUsage:number; diskUsage:number; totalMemoryBytes:number; usedMemoryBytes:number; totalDiskBytes:number; usedDiskBytes:number; sampledAt?:string }
export const operationsApi = {
  summary: () => api.get<OperationSummary>('/operations/summary'),
  tasks: () => api.get<OperationTask[]>('/operations/tasks'),
  startTask: (type:string,id:number) => api.post<OperationTaskAction>(`/operations/tasks/${type}/${id}/start`),
  rerunTask: (type:string,id:number) => api.post<OperationTaskAction>(`/operations/tasks/${type}/${id}/rerun`),
  killTask: (type:string,id:number) => api.post<void>(`/operations/tasks/${type}/${id}/kill`),
  taskLog: (type:string,id:number) => api.get<string>(`/operations/tasks/${type}/${id}/log`),
  instances: () => api.get<OperationInstance[]>('/operations/instances'),
  failures: () => api.get<FailureItem[]>('/operations/failures'),
  alerts: () => api.get<AlertItem[]>('/operations/alerts'),
  systemMetrics: () => api.get<SystemMetrics>('/operations/system-metrics'),
  stop: (type:string,id:string) => api.post<void>(`/operations/instances/${type}/${id}/stop`),
  log: (type:string,id:string) => api.get<string>(`/operations/instances/${type}/${id}/log`),
  rerun: (instanceId:string) => api.post<{instanceId:string;status:string}>(`/operations/workflow-instances/${instanceId}/rerun`)
}
