import { api } from '../http'

export interface MetricOverview { total:number; certified:number; published:number; pendingApproval:number; draft:number; rejected:number; dimensions:number; lineageRelations:number; domains:number; themes:number }
export interface DimensionView { id:number; dimensionCode:string; dimensionName:string; description?:string; sourceDataSourceId?:number; sourceDatabase?:string; sourceTable?:string; sourceField?:string; ownerName?:string }
export interface MetricDomainView { id:number; domainCode:string; domainName:string; description?:string; ownerName?:string; status:string; sortOrder:number; themeCount:number; metricCount:number; updatedAt?:string }
export interface MetricThemeView { id:number; domainId:number; domainName:string; themeCode:string; themeName:string; description?:string; ownerName?:string; status:string; sortOrder:number; metricCount:number; updatedAt?:string }
export interface MetricVersionView { id:number; metricId:number; versionNo:number; definitionJson:string; createdBy:string; createdAt?:string }
export interface MetricView { id:number; metricCode:string; metricName:string; metricType:string; description?:string; businessDomain?:string; domainId?:number; themeId?:number; domainName?:string; themeName?:string; ownerName?:string; status:string; currentVersion:number; sourceDataSourceId?:number; sourceDatabase?:string; sourceTable?:string; sourceField?:string; aggregation?:string; filterExpression?:string; timeField?:string; expressionText?:string; dimensions:DimensionView[]; createdAt?:string; updatedAt?:string }
export interface MetricLineage { id:number; metricId:number; metricCode:string; metricName:string; upstreamType:string; upstreamRef:string; downstreamType?:string; downstreamRef?:string }
export const metricApi = {
  overview: () => api.get<MetricOverview>('/metrics/overview'),
  list: () => api.get<MetricView[]>('/metrics'),
  create: (payload:Record<string,unknown>) => api.post<MetricView>('/metrics',payload),
  update: (id:number,payload:Record<string,unknown>) => api.put<MetricView>(`/metrics/${id}`,payload),
  remove: (id:number) => api.delete<void>(`/metrics/${id}`),
  versions: (id:number) => api.get<MetricVersionView[]>(`/metrics/${id}/versions`),
  certify: (id:number) => api.post<MetricView>(`/metrics/${id}/certify`),
  uncertify: (id:number) => api.post<MetricView>(`/metrics/${id}/uncertify`),
  domains: () => api.get<MetricDomainView[]>('/metrics/domains'),
  createDomain: (payload:Record<string,unknown>) => api.post<MetricDomainView>('/metrics/domains',payload),
  updateDomain: (id:number,payload:Record<string,unknown>) => api.put<MetricDomainView>(`/metrics/domains/${id}`,payload),
  removeDomain: (id:number) => api.delete<void>(`/metrics/domains/${id}`),
  themes: (domainId?:number) => api.get<MetricThemeView[]>('/metrics/themes',{params:domainId?{domainId}:undefined}),
  createTheme: (payload:Record<string,unknown>) => api.post<MetricThemeView>('/metrics/themes',payload),
  updateTheme: (id:number,payload:Record<string,unknown>) => api.put<MetricThemeView>(`/metrics/themes/${id}`,payload),
  removeTheme: (id:number) => api.delete<void>(`/metrics/themes/${id}`),
  dimensions: () => api.get<DimensionView[]>('/metrics/dimensions'),
  createDimension: (payload:Record<string,unknown>) => api.post<DimensionView>('/metrics/dimensions',payload),
  updateDimension: (id:number,payload:Record<string,unknown>) => api.put<DimensionView>(`/metrics/dimensions/${id}`,payload),
  removeDimension: (id:number) => api.delete<void>(`/metrics/dimensions/${id}`),
  lineage: () => api.get<MetricLineage[]>('/metrics/lineage')
}
