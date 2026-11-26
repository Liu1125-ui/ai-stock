import {ApiError,requestWithoutRetry} from './auth'
export interface RefreshCommand{operationId:string;baseVersionId:string;expectedHeadRevision:string;targetDate:string;reasonCode:'RECHECK_DATA'|'RECOVER_UPDATE'}
export interface RefreshOptions{queriedAt:string;actionsEnabled:boolean;allowed:boolean;reasonCode:string|null;instrumentId:string;provider:string|null;priceBasis:string|null;baseVersionId:string|null;headRevision:string|null;coverageStart:string|null;coverageEnd:string|null;minTargetDate:string|null;maxTargetDate:string|null;defaultTargetDate:string|null;cooldownUntil:string|null;activeTaskId:string|null;policyVersion:string}
export interface RefreshReceipt{operationId:string;instrumentId:string;actorUserId:string;outcome:'QUEUED'|'ATTACHED'|'REJECTED';resultCode:string;taskId:string|null;baseVersionId:string;headRevision:string;coverageStart:string|null;targetDate:string;reasonCode:string;requestedAt:string;finishedAt:string;replayed:boolean}
export interface RefreshTask{taskId:string;state:string;revision:string;attemptCount:number;versionId:string|null;errorCode:string|null;updatedAt:string}
export interface RefreshOperation{queriedAt:string;receipt:RefreshReceipt;task:RefreshTask|null}
export interface RefreshHistory{queriedAt:string;instrumentId:string;items:RefreshOperation[];nextCursor:string|null;hasMore:boolean}
const base='/admin/market-data/',uuid=/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/
export function validRevision(v:unknown):v is string{return typeof v==='string'&&/^(0|[1-9][0-9]{0,18})$/.test(v)&&BigInt(v)<=9223372036854775807n}
export function validDate(v:unknown):v is string{return typeof v==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(v)&&Number.isFinite(Date.parse(v+'T00:00:00Z'))&&new Date(v+'T00:00:00Z').toISOString().slice(0,10)===v&&v>='1990-01-01'}
const instant=(v:unknown)=>typeof v==='string'&&Number.isFinite(Date.parse(v))
const nullable=(v:unknown,check:(v:unknown)=>boolean)=>v===null||check(v)
const validId=(v:unknown)=>typeof v==='string'&&uuid.test(v)
const validStock=(v:unknown)=>typeof v==='string'&&/^(SH|SZ)[0-9]{6}$/.test(v)
function invalid():never{throw new ApiError('INVALID_RESPONSE','操作结果暂无法确认，请查询原操作编号',502)}
export function validCommand(v:unknown):v is RefreshCommand{if(!v||typeof v!=='object')return false;const c=v as RefreshCommand;return Object.keys(c).sort().join(',')===['operationId','baseVersionId','expectedHeadRevision','targetDate','reasonCode'].sort().join(',')&&validId(c.operationId)&&validId(c.baseVersionId)&&validRevision(c.expectedHeadRevision)&&validDate(c.targetDate)&&['RECHECK_DATA','RECOVER_UPDATE'].includes(c.reasonCode)}
function checkReceipt(v:RefreshReceipt):RefreshReceipt{
 if(!v||!validId(v.operationId)||!validStock(v.instrumentId)||!validId(v.actorUserId)||!validId(v.baseVersionId)||!validRevision(v.headRevision)||!validDate(v.targetDate)||!nullable(v.coverageStart,validDate)||!instant(v.requestedAt)||!instant(v.finishedAt)||typeof v.replayed!=='boolean'||!['RECHECK_DATA','RECOVER_UPDATE'].includes(v.reasonCode)||!['QUEUED','ATTACHED','REJECTED'].includes(v.outcome)||typeof v.resultCode!=='string')invalid()
 if(v.outcome!=='REJECTED'&&(!validId(v.taskId)||v.resultCode!==(v.outcome==='QUEUED'?'REFRESH_TASK_QUEUED':'REFRESH_TASK_ATTACHED')))invalid()
 if(v.outcome==='REJECTED'&&v.taskId!==null)invalid();return v
}
function checkOperation(v:RefreshOperation):RefreshOperation{
 if(!v||!instant(v.queriedAt))invalid();checkReceipt(v.receipt)
 if(v.task){const t=v.task;if(t.taskId!==v.receipt.taskId||!validId(t.taskId)||!validRevision(t.revision)||!Number.isInteger(t.attemptCount)||t.attemptCount<0||!instant(t.updatedAt)||!nullable(t.versionId,validId)||!nullable(t.errorCode,v=>typeof v==='string')||!['QUEUED','RUNNING','RECONCILING','RETRY_WAIT','SUCCEEDED','EMPTY','SKIPPED','FAILED','CANCELLED'].includes(t.state))invalid()}
 else if(v.receipt.outcome!=='REJECTED')invalid();return v
}
export async function getRefreshOptions(stock:string):Promise<RefreshOptions>{
 if(!validStock(stock))invalid();const v=await requestWithoutRetry<RefreshOptions>(base+stock+'/refresh-options','GET')
 if(!v||v.instrumentId!==stock||!instant(v.queriedAt)||typeof v.actionsEnabled!=='boolean'||typeof v.allowed!=='boolean'||v.policyVersion!=='admin-market-refresh-v1'||!nullable(v.baseVersionId,validId)||!nullable(v.headRevision,validRevision)||!nullable(v.activeTaskId,validId)||!nullable(v.cooldownUntil,instant))invalid()
 for(const d of [v.coverageStart,v.coverageEnd,v.minTargetDate,v.maxTargetDate,v.defaultTargetDate])if(!nullable(d,validDate))invalid()
 if(v.allowed&&(!v.actionsEnabled||v.provider!=='baostock'||v.priceBasis!=='RAW'||!v.baseVersionId||!v.headRevision||!v.coverageStart||!v.minTargetDate||!v.maxTargetDate||!v.defaultTargetDate||v.minTargetDate>v.maxTargetDate||v.defaultTargetDate<v.minTargetDate||v.defaultTargetDate>v.maxTargetDate))invalid();return v
}
export async function submitRefresh(stock:string,c:RefreshCommand):Promise<RefreshReceipt>{
 if(!validStock(stock)||!validCommand(c))invalid()
 function matching(v:RefreshReceipt){checkReceipt(v);if(v.instrumentId!==stock||v.operationId!==c.operationId||v.baseVersionId!==c.baseVersionId||v.headRevision!==c.expectedHeadRevision||v.targetDate!==c.targetDate||v.reasonCode!==c.reasonCode)invalid();return v}
 try{const v=matching(await requestWithoutRetry<RefreshReceipt>(base+stock+'/refresh-operations','POST',c,{'X-Aistock-Admin-Action':'1'}));if(v.outcome==='REJECTED')invalid();return v}
 catch(error){if(error instanceof ApiError&&error.status===409&&error.data!=null){const saved=matching(error.data as RefreshReceipt);if(saved.outcome!=='REJECTED'||saved.resultCode!==error.code)invalid();error.data=saved}throw error}
}
export async function getRefreshOperation(id:string):Promise<RefreshOperation>{if(!validId(id))invalid();const v=checkOperation(await requestWithoutRetry<RefreshOperation>(base+'refresh-operations/'+id,'GET'));if(v.receipt.operationId!==id)invalid();return v}
export async function getRefreshHistory(stock:string,cursor?:string,size=20):Promise<RefreshHistory>{
 if(!validStock(stock)||!Number.isInteger(size)||size<1||size>50)invalid();const query=new URLSearchParams({size:String(size)});if(cursor!==undefined){if(!/^[A-Za-z0-9_-]{1,768}$/.test(cursor))invalid();query.set('cursor',cursor)}
 const v=await requestWithoutRetry<RefreshHistory>(base+stock+'/refresh-operations?'+query,'GET');if(!v||v.instrumentId!==stock||!instant(v.queriedAt)||!Array.isArray(v.items)||v.items.length>size||typeof v.hasMore!=='boolean'||(v.hasMore?typeof v.nextCursor!=='string'||!/^[A-Za-z0-9_-]{1,768}$/.test(v.nextCursor):v.nextCursor!==null))invalid();for(const item of v.items){checkOperation(item);if(item.receipt.instrumentId!==stock)invalid()}return v
}
