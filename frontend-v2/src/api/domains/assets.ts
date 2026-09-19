import { api } from '../http'

export interface AssetItem { type:string; ref:string; name:string; description?:string; owner?:string; status:string; source?:string; detail?:string; favorite:boolean }
export interface DatasetView { id:number; datasetCode:string; datasetName:string; description?:string; sourceDataSourceId?:number; sourceDatabase?:string; sourceTable?:string; ownerName?:string; status:string; createdAt?:string; updatedAt?:string }
export const assetApi = {
  catalog: () => api.get<AssetItem[]>('/assets/catalog'),
  favorites: () => api.get<AssetItem[]>('/assets/favorites'),
  favorite: (assetType:string,assetRef:string) => api.post<void>('/assets/favorites',{assetType,assetRef}),
  unfavorite: (assetRef:string) => api.delete<void>('/assets/favorites',{params:{assetRef}}),
  datasets: () => api.get<DatasetView[]>('/assets/datasets'),
  createDataset: (payload:Record<string,unknown>) => api.post<DatasetView>('/assets/datasets',payload)
}
