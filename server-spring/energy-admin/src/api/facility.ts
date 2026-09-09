import http from './http'

export type FacilitySystemType = 'WATER_SUPPLY' | 'DRAINAGE' | 'OIL_EXHAUST' | 'FRESH_AIR'

export interface FacilityNode {
  id: number
  systemType: FacilitySystemType
  parentId?: number
  parentName?: string
  nodeCode: string
  nodeName: string
  nodeType: string
  nodeTypeName: string
  lineSpecCode?: string
  lineSpecName?: string
  equipmentSpecCode?: string
  equipmentSpecName?: string
  mediumType?: string
  mediumTypeName?: string
  designFlow?: number
  designPressure?: number
  slopePercent?: number
  elevationM?: number
  outletQuantity?: number
  controlComponent?: string
  location?: string
  stallId?: number
  stallCode?: string
  stallName?: string
  platformDeviceId?: string
  operatingStatus: 'NORMAL' | 'STOPPED' | 'FAULT' | 'MAINTENANCE'
  sortOrder: number
  status: number
  remark?: string
  children: FacilityNode[]
}

export type FacilityNodePayload = Omit<FacilityNode,
  'id' | 'systemType' | 'parentName' | 'nodeTypeName' | 'lineSpecName' |
  'equipmentSpecName' | 'mediumTypeName' | 'stallCode' | 'stallName' | 'children'>

const base = (systemType: FacilitySystemType) => `/api/v1/facility-topologies/${systemType}/nodes`

export const facilityTopologyApi = {
  tree: (systemType: FacilitySystemType) => http.get<FacilityNode[]>(base(systemType)),
  create: (systemType: FacilitySystemType, data: Partial<FacilityNodePayload>) => http.post(base(systemType), data),
  update: (systemType: FacilitySystemType, id: number, data: Partial<FacilityNodePayload>) => http.put(`${base(systemType)}/${id}`, data),
  remove: (systemType: FacilitySystemType, id: number) => http.delete(`${base(systemType)}/${id}`),
  qr: (systemType: FacilitySystemType, id: number) => http.get<Blob>(`${base(systemType)}/${id}/qr`, { responseType: 'blob' })
}
