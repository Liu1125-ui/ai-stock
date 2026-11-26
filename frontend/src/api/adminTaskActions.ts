import {ApiError,requestWithoutRetry} from './auth'
export type TaskAction='RETRY'|'CANCEL'
export interface TaskActionCommand{operationId:string;expectedRevision:string;reasonCode:string}
export interface ActionDecision{allowed:boolean;reasonCode:string;message:string;retryAvailableAt:string|null}
export interface ActionOptions{taskId:string;revision:string;queriedAt:string;actionsEnabled:boolean;retry:ActionDecision;cancel:ActionDecision}
export interface ActionReceipt{operationId:string;taskId:string;action:TaskAction;outcome:'APPLIED'|'REJECTED';resultCode:string;message:string;beforeState:string;afterState:string;beforeRevision:string;afterRevision:string;requestedAt:string;finishedAt:string}
export interface OperationHistoryItem{actorUserId:string;reasonCode:string;receipt:ActionReceipt}
export interface OperationPage{items:OperationHistoryItem[];nextCursor:string|null;hasMore:boolean;queriedAt:string}
const base='/admin/tasks/'
const uuid=/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/
function invalid():never{throw new ApiError('INVALID_RESPONSE','操作结果暂无法确认，请查询原操作编号',502)}
function version(value:unknown):boolean{return typeof value==='string'&&/^(0|[1-9][0-9]{0,18})$/.test(value)&&BigInt(value)<=9223372036854775807n}
function instant(value:unknown):boolean{return typeof value==='string'&&Number.isFinite(Date.parse(value))}
function receipt(value:ActionReceipt,task:string,op?:string,action?:TaskAction):ActionReceipt{
 if(!value||value.taskId!==task||!uuid.test(value.operationId)||(op!==undefined&&value.operationId!==op)||(action!==undefined&&value.action!==action)||!['RETRY','CANCEL'].includes(value.action)||!['APPLIED','REJECTED'].includes(value.outcome)||!version(value.beforeRevision)||!version(value.afterRevision)||!instant(value.requestedAt)||!instant(value.finishedAt))invalid()
 for(const key of ['resultCode','message','beforeState','afterState'] as const)if(typeof value[key]!=='string')invalid()
 return value
}
export async function getTaskActionOptions(taskId:string):Promise<ActionOptions>{
 const value=await requestWithoutRetry<ActionOptions>(base+encodeURIComponent(taskId)+'/action-options','GET')
 if(!value||value.taskId!==taskId||!version(value.revision)||typeof value.actionsEnabled!=='boolean'||!instant(value.queriedAt))invalid()
 for(const d of [value.retry,value.cancel])if(!d||typeof d.allowed!=='boolean'||typeof d.reasonCode!=='string'||typeof d.message!=='string'||(d.retryAvailableAt!==null&&!instant(d.retryAvailableAt)))invalid()
 return value
}
export async function submitTaskAction(taskId:string,action:TaskAction,command:TaskActionCommand):Promise<ActionReceipt>{
 try{
  const value=receipt(await requestWithoutRetry<ActionReceipt>(base+encodeURIComponent(taskId)+'/'+action.toLowerCase(),'POST',command,{'X-Aistock-Admin-Action':'1'}),taskId,command.operationId,action)
  if(value.outcome!=='APPLIED')invalid();return value
 }catch(error){
  if(error instanceof ApiError&&error.status===409&&error.data!==null&&error.data!==undefined){
   const saved=receipt(error.data as ActionReceipt,taskId,command.operationId,action)
   if(saved.outcome!=='REJECTED'||saved.resultCode!==error.code)invalid();error.data=saved
  }
  throw error
 }
}
export async function getTaskActionReceipt(taskId:string,operationId:string):Promise<ActionReceipt>{return receipt(await requestWithoutRetry<ActionReceipt>(base+encodeURIComponent(taskId)+'/operations/'+encodeURIComponent(operationId),'GET'),taskId,operationId)}
export async function listTaskOperations(taskId:string,limit=20,cursor?:string):Promise<OperationPage>{
 const q=new URLSearchParams({limit:String(limit)});if(cursor!==undefined)q.set('cursor',cursor)
 const value=await requestWithoutRetry<OperationPage>(base+encodeURIComponent(taskId)+'/operations?'+q,'GET')
 if(!value||!Array.isArray(value.items)||typeof value.hasMore!=='boolean'||!instant(value.queriedAt)||(value.hasMore?typeof value.nextCursor!=='string'||!value.nextCursor:value.nextCursor!==null))invalid()
 for(const item of value.items){if(!item||typeof item.actorUserId!=='string'||!uuid.test(item.actorUserId)||typeof item.reasonCode!=='string')invalid();receipt(item.receipt,taskId)}
 return value
}
