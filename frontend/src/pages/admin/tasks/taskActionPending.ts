import type {TaskAction} from '../../../api/adminTaskActions'
export interface PendingTaskAction{taskId:string;operationId:string;action:TaskAction;expectedRevision:string;reasonCode:string;createdAt:string}
const prefix='aistock:admin-task-operation:'
const uuid=/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/
function key(owner:string,task:string){return prefix+encodeURIComponent(owner)+':'+task}
function valid(value:unknown,task:string):value is PendingTaskAction{
 if(!value||typeof value!=='object')return false
 const r=value as PendingTaskAction
 if(Object.keys(r).sort().join(',')!==['taskId','operationId','action','expectedRevision','reasonCode','createdAt'].sort().join(','))return false
 if(r.taskId!==task||!uuid.test(r.taskId)||!uuid.test(r.operationId)||typeof r.expectedRevision!=='string'||!/^(0|[1-9][0-9]{0,18})$/.test(r.expectedRevision)||BigInt(r.expectedRevision)>9223372036854775807n||typeof r.createdAt!=='string'||!Number.isFinite(Date.parse(r.createdAt)))return false
 return r.action==='RETRY'?['RETRY_TRANSIENT_FAILURE','RETRY_DEPENDENCY_RECOVERED'].includes(r.reasonCode):r.action==='CANCEL'&&['CANCEL_NOT_NEEDED','CANCEL_DUPLICATE_REQUEST'].includes(r.reasonCode)
}
export function savePending(owner:string,marker:PendingTaskAction){
 if(!owner||!valid(marker,marker.taskId))throw new Error('原操作恢复信息不完整，尚未提交')
 try{sessionStorage.setItem(key(owner,marker.taskId),JSON.stringify(marker))}catch{throw new Error('无法保存原操作编号，尚未提交，请检查浏览器存储设置')}
}
export function loadPending(owner:string,task:string):PendingTaskAction|null{
 let raw:string|null
 try{raw=sessionStorage.getItem(key(owner,task))}catch{throw new Error('无法读取原操作恢复信息，暂不能提交新操作')}
 if(raw===null)return null
 try{const marker:unknown=JSON.parse(raw);if(!valid(marker,task))throw new Error();return marker}catch{throw new Error('原操作恢复信息损坏，请先核对管理员操作记录')}
}
export function clearPending(owner:string,task:string){try{sessionStorage.removeItem(key(owner,task))}catch{/* No credential or authorization depends on browser storage. */}}
export function clearTaskActionPendingOwner(owner:string){try{const start=prefix+encodeURIComponent(owner)+':';for(let i=sessionStorage.length-1;i>=0;i--){const k=sessionStorage.key(i);if(k?.startsWith(start))sessionStorage.removeItem(k)}}catch{/* Sign-out must still proceed when storage is unavailable. */}}
