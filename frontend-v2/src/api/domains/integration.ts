import { api } from '../http'

export interface IntegrationTable { id:number; taskId:number; sourceDatabase:string; sourceTable:string; targetDatabase:string; targetTable:string; partitionColumn?:string }
export interface IntegrationTask { id:number; projectId?:number; name:string; sourceType:string; targetType:string; syncMode:string; status:string; lifecycleStatus:string; sourceConfigJson:string; targetConfigJson:string; transformConfigJson:string; seatunnelConfig:string; tables:IntegrationTable[]; downstreamFileIds:number[] }
export interface IntegrationProjectOption { id:number; name:string }
export interface IntegrationDownstreamOption { id:number; name:string; fileType:string; ownerName?:string; lifecycleStatus:string }
export interface IntegrationTaskSummary { createdAt?:string; createdBy:string; lastRunAt?:string; nextRunAt?:string; durationMs?:number; dataCount?:number }
export interface IntegrationTaskSchedule { taskId:number; cronExpression:string; timezone:string; enabled:boolean }
export interface IntegrationInstance { id:number; taskId:number; executionId:string; status:string; startedAt?:string; finishedAt?:string; message?:string }
export interface IntegrationBatch { id:number; taskId:number; batchCode:string; triggerType:string; status:string; clusterId?:number; parametersJson?:string; sourceBatchId?:number; createdBy:string; startedAt?:string; finishedAt?:string; errorMessage?:string; createdAt?:string }
export interface IntegrationAttempt { id:number; batchId:number; attemptNo:number; executionId?:string; status:string; startedAt?:string; finishedAt?:string; errorMessage?:string; createdAt?:string }
export interface IntegrationCursor { taskId:number; cursorColumn?:string; cursorValue?:string; updatedAt?:string }
export interface IntegrationTaskPayload {
  name:string; projectId?:number; downstreamFileIds:number[]; sourceType:string; targetType:string; syncMode:string; sourceDataSourceId:number; targetDataSourceId:number;
  source:{host:string;port:number;database:string;username:string;password:string;table:string};
  target:{host:string;port:number;database:string;username:string;password:string;table:string};
  mappings:Array<{source:string;target:string}>; options:Record<string,unknown>;
  tables:Array<{sourceDatabase:string;sourceTable:string;targetDatabase:string;targetTable:string;partitionColumn?:string}>
}
export const integrationApi = {
  list: () => api.get<IntegrationTask[]>('/integration/tasks'),
  projectOptions: () => api.get<IntegrationProjectOption[]>('/integration/tasks/project-options'),
  projectDownstreams: (projectId:number) => api.get<IntegrationDownstreamOption[]>('/integration/tasks/project-downstreams',{params:{projectId}}),
  get: (id:number) => api.get<IntegrationTask>(`/integration/tasks/${id}`),
  summary: (id:number) => api.get<IntegrationTaskSummary>(`/integration/tasks/${id}/summary`),
  schedule: (id:number) => api.get<IntegrationTaskSchedule>(`/integration/tasks/${id}/schedule`),
  previewSchedule: (payload:{cronExpression:string;timezone:string;enabled:boolean}) => api.post<string[]>('/integration/tasks/schedule/preview',payload),
  saveSchedule: (id:number,payload:{cronExpression:string;timezone:string;enabled:boolean}) => api.put<IntegrationTaskSchedule>(`/integration/tasks/${id}/schedule`,payload),
  create: (payload:IntegrationTaskPayload) => api.post<IntegrationTask>('/integration/tasks',payload),
  update: (id:number,payload:IntegrationTaskPayload) => api.put<IntegrationTask>(`/integration/tasks/${id}`,payload),
  online: (id:number) => api.post<IntegrationTask>(`/integration/tasks/${id}/online`),
  offline: (id:number) => api.post<IntegrationTask>(`/integration/tasks/${id}/offline`),
  remove: (id:number) => api.delete<void>(`/integration/tasks/${id}`),
  run: (id:number) => api.post<{executionId:string;status:string}>(`/integration/tasks/${id}/run`),
  runConfirmed: (id:number) => api.post<{executionId:string;status:string}>(`/integration/tasks/${id}/run-confirmed`),
  stop: (id:number) => api.post<void>(`/integration/tasks/${id}/stop`),
  validate: (id:number) => api.post<{valid:boolean;message:string}>(`/integration/tasks/${id}/validate`),
  instances: (id:number) => api.get<IntegrationInstance[]>(`/integration/tasks/${id}/instances`),
  batches: (id:number) => api.get<IntegrationBatch[]>(`/integration/tasks/${id}/batches`),
  attempts: (batchId:number) => api.get<IntegrationAttempt[]>(`/integration/tasks/batches/${batchId}/attempts`),
  retryBatch: (batchId:number) => api.post<IntegrationBatch>(`/integration/tasks/batches/${batchId}/retry`),
  reconcileBatch: (batchId:number) => api.post<IntegrationBatch>(`/integration/tasks/batches/${batchId}/reconcile`),
  backfill: (id:number,payload:{where:string;startLabel?:string;endLabel?:string}) => api.post<IntegrationBatch>(`/integration/tasks/${id}/backfill`,payload),
  cursor: (id:number) => api.get<IntegrationCursor>(`/integration/tasks/${id}/cursor`),
  saveCursor: (id:number,payload:{cursorColumn?:string;cursorValue?:string}) => api.put<IntegrationCursor>(`/integration/tasks/${id}/cursor`,payload),
  log: (executionId:string) => api.get<string>(`/integration/tasks/executions/${executionId}/log`),
  sourceDatabases: (dataSourceId:number) => api.get<Array<{name:string}>>('/integration/tasks/source-databases',{params:{dataSourceId}}),
  sourceTables: (dataSourceId:number,database:string) => api.get<Array<{name:string;comment?:string}>>('/integration/tasks/source-tables',{params:{dataSourceId,database}}),
  targetDatabases: (dataSourceId:number) => api.get<Array<{name:string;comment?:string}>>('/integration/tasks/target-databases',{params:{dataSourceId}}),
  previewConfig: (payload:IntegrationTaskPayload) => api.post<string>('/integration/tasks/preview-config',payload)
}
