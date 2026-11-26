export interface Profile {userId:string;email:string;nickname:string;role:'USER'|'ADMIN';status:'ENABLED'|'DISABLED';emailVerified:boolean;experimentAllowed:boolean;revision:number;avatarStyle?:string|null}
export interface Challenge {challengeId:string;expiresAt:string;resendAfter:string;state:'PENDING'|'ACCEPTED'|'SENT'|'FAILED'|'EXPIRED'}
export class ApiError extends Error {constructor(public code:string,message:string,public status:number,public data?:unknown){super(message)}}
let authenticationGeneration=0
function requireAuthenticationGeneration(generation:number){if(generation!==authenticationGeneration)throw new ApiError('AUTH_CONTEXT_CHANGED','账号状态已变更，旧请求已停止，请重新操作',0)}
export function invalidateAuthenticatedRequests(clearToken=true){authenticationGeneration++;csrfPending=undefined;if(clearToken)csrf=undefined}
let csrf:string|undefined
let csrfPending:Promise<void>|undefined
export function resetCsrf(){csrf=undefined;csrfPending=undefined}
export async function request<T>(path:string,method='GET',body?:unknown,retried=false,generation=authenticationGeneration):Promise<T>{
 requireAuthenticationGeneration(generation)
 if(method!=='GET'&&!csrf)await refreshCsrf()
 requireAuthenticationGeneration(generation)
 const headers=new Headers({'Accept':'application/json'})
 if(body!==undefined)headers.set('Content-Type','application/json')
 if(method!=='GET'&&csrf)headers.set('X-XSRF-TOKEN',csrf)
 let response:Response
 try{response=await fetch('/api/v1'+path,{method,credentials:'include',cache:'no-store',headers,body:body===undefined?undefined:JSON.stringify(body)})}
 catch{requireAuthenticationGeneration(generation);throw new ApiError('NETWORK_UNAVAILABLE','连接失败，请稍后重试',0)}
 requireAuthenticationGeneration(generation)
 let payload:{code:string;message?:string;data:T}
 try{payload=await response.json()}catch{throw new ApiError('INVALID_RESPONSE','服务响应异常，请稍后重试',response.status)}
 requireAuthenticationGeneration(generation)
 if(!response.ok){
  if(response.status===403&&payload.code==='ACCESS_DENIED'&&method!=='GET'&&!retried){requireAuthenticationGeneration(generation);resetCsrf();await refreshCsrf();requireAuthenticationGeneration(generation);return request<T>(path,method,body,true,generation)}
  if(response.status===401){csrf=undefined;if(payload.code==='AUTH_REQUIRED'&&(path.startsWith('/messages')||path.startsWith('/alerts')||path.startsWith('/notification-preferences')||path.startsWith('/users/')||path.startsWith('/admin/')||path.startsWith('/instruments')||path.startsWith('/predictions')||path.startsWith('/training-submissions')||path.startsWith('/stock-questions')||path.startsWith('/stock-analyses')||path.startsWith('/comparison-explanations')||path.startsWith('/comparisons')||path.startsWith('/portfolio-questions')||path.startsWith('/portfolio-analyses')||path==='/models'))window.dispatchEvent(new Event('aistock:auth-required'))}
  throw new ApiError(payload.code,payload.message||'操作未完成',response.status)
 }
 return payload.data
}
export async function refreshCsrf(){
 if(!csrfPending){const generation=authenticationGeneration;const pending=request<{token:string}>('/auth/csrf','GET',undefined,false,generation).then(result=>{requireAuthenticationGeneration(generation);csrf=result.token}).finally(()=>{if(csrfPending===pending)csrfPending=undefined});csrfPending=pending}
 await csrfPending
}
export const requestEmailChange=(email:string,currentPassword:string)=>request<Challenge>('/users/me/email-change-challenges','POST',{email,currentPassword})
export const emailChangeStatus=(id:string)=>request<Challenge>('/users/me/email-change-challenges/'+encodeURIComponent(id))
export async function confirmEmailChange(challengeId:string,code:string){
 await request<void>('/users/me/email-change','POST',{challengeId,code})
 invalidateAuthenticatedRequests()
}
export const me=()=>request<Profile>('/users/me')
export const requestChallenge=(email:string)=>request<Challenge>('/auth/registration-challenges','POST',{email})
export const challengeStatus=(id:string)=>request<Challenge>('/auth/registration-challenges/'+encodeURIComponent(id))
export const register=(form:{registrationRequestId:string;challengeId:string;email:string;code:string;password:string;nickname:string})=>request<{userId:string;replayed:boolean}>('/auth/register','POST',form)
export async function login(email:string,password:string){invalidateAuthenticatedRequests(false);const result=await request('/auth/login','POST',{email,password});invalidateAuthenticatedRequests();await refreshCsrf();return result}
export async function logout(){invalidateAuthenticatedRequests(false);await request('/auth/logout','POST');invalidateAuthenticatedRequests();await refreshCsrf()}
export const rename=(nickname:string,revision:number,avatarStyle?:string)=>request<Profile>('/users/me','PATCH',{nickname,revision,avatarStyle})
export async function changePassword(currentPassword:string,newPassword:string){invalidateAuthenticatedRequests(false);await request('/users/me/password','PUT',{currentPassword,newPassword});invalidateAuthenticatedRequests();await refreshCsrf()}

/** Admin task transport only: no CSRF refresh or automatic retry; server enforces Origin + action header. */
export async function requestWithoutRetry<T>(path:string,method:'GET'|'POST',body?:unknown,extraHeaders?:Record<string,string>):Promise<T>{
 const generation=authenticationGeneration
 requireAuthenticationGeneration(generation)
 const headers=new Headers({'Accept':'application/json',...extraHeaders})
 if(body!==undefined)headers.set('Content-Type','application/json')
 let response:Response
 try{response=await fetch('/api/v1'+path,{method,headers,credentials:'include',cache:'no-store',body:body===undefined?undefined:JSON.stringify(body)})}
 catch{requireAuthenticationGeneration(generation);throw new ApiError('NETWORK_UNAVAILABLE','操作结果尚未确认，请查询原操作编号',0)}
 requireAuthenticationGeneration(generation)
 let payload:{code:string;message?:string;data:T}
 try{payload=await response.json()}catch{requireAuthenticationGeneration(generation);throw new ApiError('INVALID_RESPONSE','操作结果暂无法确认，请查询原操作编号',response.status)}
 requireAuthenticationGeneration(generation)
 if(!payload||typeof payload.code!=='string')throw new ApiError('INVALID_RESPONSE','服务响应格式不正确',response.status)
 if(!response.ok){
  if(response.status===401&&payload.code==='AUTH_REQUIRED'){csrf=undefined;window.dispatchEvent(new Event('aistock:auth-required'))}
  throw new ApiError(payload.code,typeof payload.message==='string'?payload.message:'操作未完成',response.status,payload.data)
 }
 if(payload.code!=='OK')throw new ApiError('INVALID_RESPONSE','服务响应格式不正确',response.status)
 return payload.data
}
