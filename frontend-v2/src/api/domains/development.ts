import { api } from '../http'

export interface DevProject { id:number; name:string; description?:string; status:string; ownerName?:string }
export interface DevFolder { id:number; projectId:number; parentId?:number; name:string; createdAt?:string }
export interface DevFile { id:number; projectId:number; folderId?:number; name:string; fileType:string; content:string; description?:string; status:string; currentVersion:number; updatedAt?:string; lifecycleStatus:string; everOnline:boolean; ownerName:string }
export interface RecycledDevFile { id:number; projectId:number; folderId?:number; folderName?:string; name:string; fileType:string; description?:string; status:string; currentVersion:number; recycledAt?:string; recycledBy?:string }
export interface FileVersion { id:number; fileId:number; versionNo:number; content:string; checksum?:string; publishFlag:boolean }
export interface QueryResult { executionId:string; status:string; columns:string[]; rows:Array<Record<string,unknown>>; rowCount:number; elapsedMs:number; errorMessage?:string; columnComments?:Record<string,string> }
export interface QueryHistory { queryId:string; datasourceId?:number; databaseName?:string; sql:string; status:string; username:string; startedAt?:string; elapsedMs:number; errorMessage?:string }
export interface DevelopmentScheduleDependency { fileId:number; name:string }
export interface DevelopmentSchedule { fileId:number; currentVersion:number; publishedVersion:number; enabled:boolean; cycleType:string; executionTime:string; cronExpression:string; timezone:string; dataSourceId?:number; databaseName?:string; bizDateParam:string; retryTimes:number; retryIntervalMinutes:number; timeoutMinutes:number; dependencies:DevelopmentScheduleDependency[]; downstream:DevelopmentScheduleDependency[]; publishedSqlVersion:number; currentReleaseNo:number }
export interface DevelopmentBundle { fileId:number; sqlVersion:number; publishedSqlVersion:number; scheduleVersion:number; publishedScheduleVersion:number; releaseNo:number; sqlDirty:boolean; scheduleDirty:boolean }
export interface DevelopmentBundleRelease { releaseNo:number; sqlVersion:number; scheduleVersion:number; current:boolean; operatorName:string; remark?:string; releasedAt?:string }
export interface DevelopmentScheduleRuntime { fileId:number; status:string; plannedAt?:string; startedAt?:string; finishedAt?:string; executionId?:string; errorMessage?:string; nextPlannedAt?:string }
export interface DevelopmentSchedulePayload { enabled:boolean; cycleType:string; executionTime:string; cronExpression:string; timezone:string; dataSourceId?:number; databaseName:string; bizDateParam:string; retryTimes:number; retryIntervalMinutes:number; timeoutMinutes:number; upstreamFileIds:number[] }

export const developmentApi = {
  projects: () => api.get<DevProject[]>('/development/projects'),
  folders: (projectId:number) => api.get<DevFolder[]>('/development/folders', { params:{ projectId } }),
  createFolder: (payload:{projectId:number;parentId?:number;name:string}) => api.post<DevFolder>('/development/folders',payload),
  updateFolder: (id:number,payload:{name:string;parentId?:number;moveToRoot?:boolean}) => api.put<DevFolder>(`/development/folders/${id}`,payload),
  deleteFolder: (id:number) => api.delete<void>(`/development/folders/${id}`),
  files: (projectId:number) => api.get<DevFile[]>('/development/files', { params:{ projectId } }),
  visibleFiles: () => api.get<DevFile[]>('/development/files/visible'),
  recentFileIds: (projectId:number) => api.get<number[]>('/development/files/recent', { params:{ projectId } }),
  getFile: (id:number) => api.get<DevFile>(`/development/files/${id}`),
  createFile: (payload:{projectId:number;folderId?:number;name:string;fileType:string;content:string;description?:string}) => api.post<DevFile>('/development/files',payload),
  saveFile: (id:number,payload:{content:string;name?:string;description?:string;folderId?:number;moveToRoot?:boolean}) => api.put<DevFile>(`/development/files/${id}`,payload),
  onlineFile: (id:number) => api.post<DevFile>(`/development/files/${id}/online`),
  offlineFile: (id:number) => api.post<DevFile>(`/development/files/${id}/offline`),
  unpublishFile: (id:number) => api.post<DevFile>(`/development/files/${id}/unpublish`),
  deleteFile: (id:number) => api.delete<void>(`/development/files/${id}`),
  recycleBin: (projectId:number) => api.get<RecycledDevFile[]>('/development/files/recycle', { params:{ projectId } }),
  restoreFile: (id:number) => api.post<DevFile>(`/development/files/${id}/restore`),
  permanentlyDeleteFile: (id:number) => api.delete<void>(`/development/files/${id}/permanent`),
  versions: (id:number) => api.get<FileVersion[]>(`/development/files/${id}/versions`),
  createVersion: (id:number,content:string) => api.post<FileVersion>(`/development/files/${id}/versions`,{content}),
  publish: (id:number) => api.post<DevFile>(`/development/files/${id}/publish`),
  query: (sql:string,dataSourceId:number,databaseName?:string) => api.post<QueryResult>('/query/execute',{sql,selected:false,dataSourceId,databaseName}),
  history: () => api.get<QueryHistory[]>('/query/history'),
  schedule: (id:number) => api.get<DevelopmentSchedule>(`/development/files/${id}/schedule`),
  saveSchedule: (id:number,payload:DevelopmentSchedulePayload) => api.put<DevelopmentSchedule>(`/development/files/${id}/schedule`,payload),
  deleteSchedule: (id:number) => api.delete<DevelopmentSchedule>(`/development/files/${id}/schedule`),
  scheduleVersion: (id:number,versionNo:number) => api.get<DevelopmentSchedule>(`/development/files/${id}/schedule/versions/${versionNo}`),
  scheduleRuntime: (id:number) => api.get<DevelopmentScheduleRuntime>(`/development/files/${id}/schedule/runtime`),
  scheduleRuntimes: (ids:number[]) => api.post<DevelopmentScheduleRuntime[]>('/development/files/schedule/runtimes',ids),
  bundle: (id:number) => api.get<DevelopmentBundle>(`/development/files/${id}/bundle`),
  bundleReleases: (id:number) => api.get<DevelopmentBundleRelease[]>(`/development/files/${id}/bundle/releases`),
  rollbackBundle: (id:number,releaseNo:number) => api.post<DevelopmentBundle>(`/development/files/${id}/bundle/releases/${releaseNo}/rollback`)
}
