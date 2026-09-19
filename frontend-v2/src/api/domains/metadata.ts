import { api } from '../http'

export interface DatabaseView { name: string; comment?: string }
export interface TableView { database: string; name: string; comment?: string; type?: string }
export interface ColumnView { name: string; dataType: string; nullable?: boolean; comment?: string; ordinalPosition?: number }
export interface TableProfile { dataSourceId:number; database:string; table:string; rowCount?:number; estimatedSizeBytes?:number; owner?:string; createTime?:string; updateTime?:string; ownerEditable:boolean }
export interface LineageView { id:number; sourceTable:string; targetTable:string; relationType:string; fileId?:number; fileVersionId?:number }
export interface TablePreview { dataSourceId:number; database:string; table:string; columns:string[]; rows:string[][]; limit:number }

export const metadataApi = {
  databases: (dataSourceId:number, type='STARROCKS') => api.get<DatabaseView[]>('/metadata/databases', { params:{ dataSourceId, type } }),
  tables: (dataSourceId:number, database:string) => api.get<TableView[]>('/metadata/tables', { params:{ dataSourceId, database } }),
  columns: (dataSourceId:number, database:string, table:string) => api.get<ColumnView[]>('/metadata/columns', { params:{ dataSourceId, database, table } }),
  profile: (dataSourceId:number, database:string, table:string) => api.get<TableProfile>('/metadata/table-profile', { params:{ dataSourceId, database, table } }),
  preview: (dataSourceId:number, database:string, table:string, limit=50) => api.get<TablePreview>('/metadata/table-preview', { params:{ dataSourceId, database, table, limit } }),
  updateOwner: (dataSourceId:number, database:string, table:string, owner:string) => api.put<TableProfile>('/metadata/table-profile/owner', { dataSourceId, database, table, owner }),
  lineage: (name:string) => api.get<LineageView[]>('/lineage/table', { params:{ name } })
}
