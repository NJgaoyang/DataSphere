import { api } from '../http'

export interface ReleasePolicy { id:number; policyKey:string; approvalRequired:boolean; updatedBy:string; updatedAt:string }
export interface ReleaseRequestView { id:number; resourceType:string; resourceId:number; resourceName?:string; requestedVersion?:number; status:string; requestedBy:string; reviewedBy?:string; reviewComment?:string; requestedAt:string; reviewedAt?:string }
export interface ReleaseRecord { id:number; requestId?:number; resourceType:string; resourceId:number; resourceName?:string; releasedVersion?:number; resultStatus:string; detail?:string; operatorName:string; releasedAt:string }
export const releaseApi = {
  policy: () => api.get<ReleasePolicy>('/release/policy'),
  updatePolicy: (approvalRequired:boolean) => api.put<ReleasePolicy>('/release/policy',{approvalRequired}),
  requests: (status?:string) => api.get<ReleaseRequestView[]>('/release/requests',{params:status?{status}:undefined}),
  request: (payload:{resourceType:string;resourceId:number;resourceName?:string;requestedVersion?:number;payload?:Record<string,unknown>}) => api.post<ReleaseRequestView>('/release/requests',payload),
  approve: (id:number,comment='') => api.post<ReleaseRequestView>(`/release/requests/${id}/approve`,{comment}),
  reject: (id:number,comment='') => api.post<ReleaseRequestView>(`/release/requests/${id}/reject`,{comment}),
  records: () => api.get<ReleaseRecord[]>('/release/records')
}
