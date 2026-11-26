import {request,type Profile} from './auth'
export interface UserPage{items:Profile[];total:number;page:number;size:number}
export const listUsers=(q:string,status:string,page:number,size=20)=>request<UserPage>('/admin/users?'+new URLSearchParams({q,page:String(page),size:String(size),...(status?{status}:{})}))
export const setUserStatus=(user:Profile)=>request<Profile>('/admin/users/'+user.userId+'/status','PATCH',{status:user.status==='ENABLED'?'DISABLED':'ENABLED',revision:user.revision})
export const setExperimentGrant=(user:Profile)=>request<Profile>('/admin/users/'+user.userId+'/experiment-grant','PUT',{enabled:!user.experimentAllowed,revision:user.revision})
