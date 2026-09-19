import { api } from '../http'

export interface FlinkEnvironment { id:number; name:string; engineType:string; deploymentMode:string; submitterType:string; restUrl?:string; flinkHome?:string; flinkCdcHome?:string; javaHome?:string; flinkVersion?:string; flinkCdcVersion?:string; sshHost?:string; sshPort:number; sshUsername?:string; enabled:boolean; defaultEnvironment:boolean }
export interface RealtimeJob { id:number; name:string; description?:string; runtimeEnvironmentId?:number; releaseState:string; desiredState:string; observedState:string; definitionVersion:number; publishedVersion?:number; spec:Record<string,any>; configDigest?:string; lastError?:string; createdBy:string; createdAt?:string; updatedAt?:string; publishedUpdateAvailable:boolean }
export interface RealtimeExecution { id:number; jobId:number; definitionVersion:number; engineJobId?:string; runtimeRevision?:string; status:string; resultUncertain?:boolean; errorMessage?:string; startedAt?:string; finishedAt?:string; createdAt?:string }
export interface RealtimeRuntime { job:RealtimeJob; execution?:RealtimeExecution; environment?:FlinkEnvironment }
export interface RealtimePreCheckItem { level:'ERROR'|'WARNING'|'INFO'; code:string; message:string; detail:string; blocking:boolean }
export interface RealtimeManagementRow { id:number; name:string; sourceDataSourceId?:string; sourceDatabase?:string; sinkDataSourceId?:string; sinkDatabase?:string; tableCount:number; syncScope:string; releaseState:string; observedState:string; definitionVersion:number; runtimeEnvironmentId?:number; environmentName?:string; engineJobId?:string; lagMs?:number; checkpointStatus:string; checkpointAt?:string; owner:string; updatedAt?:string; lastError?:string }
export interface RealtimeTableOption { name:string; comment?:string; estimatedRows:number; primaryKey:boolean; cdcStatus:string }
export interface RealtimeColumnOption { name:string; mysqlType:string; primaryKey:boolean; nullable:boolean; starRocksType:string; comment?:string; ordinalPosition:number }
export interface RealtimeEventRow { id:number; executionId?:number; eventType:string; detail?:string; createdAt?:string }
export interface RealtimeCheckpointRow { id:number; executionId?:number; checkpointId:number; status:string; durationMs:number; stateSizeBytes:number; completedAt?:string; createdAt?:string }
export interface RealtimeSchemaChangeRow { id:number; sourceTable:string; changeType:string; ddlText?:string; policyAction?:string; targetResult?:string; status:string; detail?:string; occurredAt?:string }
export interface RealtimeValidationRow { id:number; validationType:string; sourceTable?:string; sourceValue?:string; targetValue?:string; status:string; detail?:string; checkedAt?:string }
export interface RealtimeVersionRow { versionNo:number; current:boolean; published:boolean; changeType:string; tables:string[]; addedTables:string[]; restoreSavepoint?:string; sourceExecutionId?:number; executionId?:number; engineJobId?:string; status?:string; createdBy:string; createdAt?:string }
export const realtimeApi = {
  list: () => api.get<RealtimeJob[]>('/realtime/jobs'),
  management: () => api.get<RealtimeManagementRow[]>('/realtime/jobs/management'),
  get: (id:number) => api.get<RealtimeJob>(`/realtime/jobs/${id}`),
  create: (payload:{name:string;description?:string;runtimeEnvironmentId?:number;spec:Record<string,unknown>}) => api.post<RealtimeJob>('/realtime/jobs',payload),
  previewValidate: (payload:{name:string;description?:string;runtimeEnvironmentId?:number;spec:Record<string,unknown>}) => api.post<{valid:boolean;message:string;warnings:string[];yamlPreview:string;items:RealtimePreCheckItem[]}>('/realtime/jobs/preview-validate',payload),
  draft: (id:number,payload:{name:string;description?:string;runtimeEnvironmentId?:number;spec:Record<string,unknown>}) => api.put<RealtimeJob>(`/realtime/jobs/${id}/draft`,payload),
  validate: (id:number) => api.post<{valid:boolean;message:string;warnings:string[];yamlPreview:string;items:RealtimePreCheckItem[]}>(`/realtime/jobs/${id}/validate`),
  publish: (id:number) => api.post<RealtimeJob>(`/realtime/jobs/${id}/publish`),
  start: (id:number) => api.post<RealtimeRuntime>(`/realtime/jobs/${id}/start`),
  stop: (id:number) => api.post<RealtimeRuntime>(`/realtime/jobs/${id}/stop`),
  remove: (id:number) => api.delete<void>(`/realtime/jobs/${id}`),
  restart: (id:number) => api.post<RealtimeRuntime>(`/realtime/jobs/${id}/restart`),
  runtime: (id:number) => api.get<RealtimeRuntime>(`/realtime/jobs/${id}/runtime`),
  checkpoints: (id:number) => api.get<any>(`/realtime/jobs/${id}/checkpoints`),
  metrics: (id:number) => api.get<any>(`/realtime/jobs/${id}/metrics`),
  logs: (id:number) => api.get<any>(`/realtime/jobs/${id}/logs`),
  yaml: (id:number) => api.get<string>(`/realtime/jobs/${id}/yaml`),
  versions: (id:number) => api.get<RealtimeVersionRow[]>(`/realtime/jobs/${id}/versions`),
  tables: (dataSourceId:number,database:string) => api.get<RealtimeTableOption[]>('/realtime/jobs/metadata/tables',{params:{dataSourceId,database}}),
  columns: (dataSourceId:number,database:string,table:string) => api.get<RealtimeColumnOption[]>('/realtime/jobs/metadata/columns',{params:{dataSourceId,database,table}}),
  executions: (id:number) => api.get<RealtimeExecution[]>(`/realtime/jobs/${id}/executions`),
  events: (id:number) => api.get<RealtimeEventRow[]>(`/realtime/jobs/${id}/events`),
  checkpointHistory: (id:number) => api.get<RealtimeCheckpointRow[]>(`/realtime/jobs/${id}/checkpoint-history`),
  schemaChanges: (id:number) => api.get<RealtimeSchemaChangeRow[]>(`/realtime/jobs/${id}/schema-changes`),
  scanSchemaChanges: (id:number) => api.post<RealtimeSchemaChangeRow[]>(`/realtime/jobs/${id}/schema-changes/scan`),
  validationResults: (id:number) => api.get<RealtimeValidationRow[]>(`/realtime/jobs/${id}/validation-results`),
  dataValidation: (id:number) => api.post<RealtimeValidationRow[]>(`/realtime/jobs/${id}/data-validation`)
}
export const flinkApi = {
  list: () => api.get<FlinkEnvironment[]>('/flink/environments'),
  create: (payload:Record<string,unknown>) => api.post<FlinkEnvironment>('/flink/environments',payload),
  update: (id:number,payload:Record<string,unknown>) => api.put<FlinkEnvironment>(`/flink/environments/${id}`,payload),
  remove: (id:number) => api.delete<void>(`/flink/environments/${id}`),
  test: (id:number) => api.post<unknown>(`/flink/environments/${id}/test`)
}

export interface SeaTunnelEnvironment { id:number; name:string; host:string; port:number; sshUsername?:string; sshPort:number; seatunnelHome:string; description?:string; healthStatus:string; createdAt?:string }
export const seaTunnelEnvironmentApi = {
  list: () => api.get<SeaTunnelEnvironment[]>('/system/clusters'),
  create: (payload:Record<string,unknown>) => api.post<SeaTunnelEnvironment>('/system/clusters',payload),
  update: (id:number,payload:Record<string,unknown>) => api.put<SeaTunnelEnvironment>(`/system/clusters/${id}`,payload),
  remove: (id:number) => api.delete<void>(`/system/clusters/${id}`),
  check: (id:number) => api.post<SeaTunnelEnvironment>(`/system/clusters/${id}/check`)
}
