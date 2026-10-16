import http from './http'
export const accountAnomalyApi={
 overview:(date?:string)=>http.get('/api/v1/energy/account-anomalies/overview',{params:{date}}),
 results:(params:Record<string,any>)=>http.get('/api/v1/energy/account-anomalies',{params}),
 accounts:(keyword?:string)=>http.get('/api/v1/energy/account-anomalies/accounts',{params:{keyword}}),
 trend:(params:Record<string,any>)=>http.get('/api/v1/energy/account-anomalies/trend',{params}),
 sync:()=>http.post('/api/v1/energy/account-anomalies/sync'),
 rules:(keyword?:string)=>http.get('/api/v1/energy/account-anomalies/rules',{params:{keyword}}),
 defaultRule:()=>http.get('/api/v1/energy/account-anomalies/rules/default'),
 saveRule:(accountNumber:string,data:any)=>http.put(`/api/v1/energy/account-anomalies/rules/${encodeURIComponent(accountNumber)}`,data),
 handle:(id:number,data:any)=>http.put(`/api/v1/energy/account-anomalies/${id}/handle`,data),
 handleHistory:(id:number)=>http.get(`/api/v1/energy/account-anomalies/${id}/handle-history`),
 disableMonitoring:(id:number,data:any)=>http.put(`/api/v1/energy/account-anomalies/${id}/disable-monitoring`,data),
 runs:()=>http.get('/api/v1/energy/account-anomalies/runs')
}
