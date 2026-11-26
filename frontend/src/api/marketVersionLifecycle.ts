import {ApiError,requestWithoutRetry} from './auth'

export type UseState='ENABLED'|'DRAINING'|'DISABLED'
export interface LifecycleStatus {
  instrumentId:string
  versionId:string
  useState:UseState
  revision:string
  activeUses:number
  currentHead:boolean
  actionsReady:boolean
}
export interface LifecycleCommand {
  operationId:string
  action:'DISABLE'|'RESTORE'
  expectedRevision:string
  reason:string
}
export interface LifecycleOperation extends LifecycleCommand {
  operationId:string
  instrumentId:string
  versionId:string
  state:'WAITING'|'COMPLETED'|'REJECTED'|'SUPERSEDED'
  createdAt:string
  errorCode:string|null
}
export interface LifecycleHistory {
  items:LifecycleOperation[]
  nextCursor:string|null
}

const uuid=/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/
const pageSize=20
function invalid():never {
  throw new ApiError('INVALID_RESPONSE','版本使用信息无法核对，请稍后刷新',502)
}

function path(stock:string,version:string) {
  if(!/^(SH|SZ)[0-9]{6}$/.test(stock)||!uuid.test(version))invalid()
  return '/admin/market-data/'+stock+'/versions/'+version+'/lifecycle'
}

export async function getLifecycleStatus(stock:string,version:string):Promise<LifecycleStatus> {
  const data=await requestWithoutRetry<LifecycleStatus>(path(stock,version),'GET')
  if(!data||data.instrumentId!==stock||data.versionId!==version||
    !['ENABLED','DRAINING','DISABLED'].includes(data.useState)||
    typeof data.revision!=='string'||!/^(0|[1-9][0-9]*)$/.test(data.revision)||
    !Number.isSafeInteger(data.activeUses)||data.activeUses<0||
    typeof data.currentHead!=='boolean'||typeof data.actionsReady!=='boolean')invalid()
  return data
}

export async function getLifecycleHistory(stock:string,version:string,cursor:string|null):Promise<LifecycleHistory> {
  const base=path(stock,version)
  if(cursor!==null&&!uuid.test(cursor))invalid()
  const query=new URLSearchParams({limit:String(pageSize)})
  if(cursor)query.set('cursor',cursor)
  const data=await requestWithoutRetry<LifecycleHistory>(base+'/history?'+query,'GET')
  if(!data||!Array.isArray(data.items)||data.items.length>pageSize)invalid()
  const ids=new Set<string>()
  for(const item of data.items) {
    validateOperation(item,stock,version)
    if(ids.has(item.operationId)||item.operationId===cursor)invalid()
    ids.add(item.operationId)
  }
  if(data.nextCursor!==null&&(typeof data.nextCursor!=='string'||!uuid.test(data.nextCursor)||
    data.nextCursor!==data.items.at(-1)?.operationId))invalid()
  return data
}

function validateOperation(item:LifecycleOperation,stock:string,version:string) {
  if(!item||item.instrumentId!==stock||item.versionId!==version||
    typeof item.operationId!=='string'||!uuid.test(item.operationId)||
    !['DISABLE','RESTORE'].includes(item.action)||
    typeof item.expectedRevision!=='string'||!/^(0|[1-9][0-9]*)$/.test(item.expectedRevision)||
    !['WAITING','COMPLETED','REJECTED','SUPERSEDED'].includes(item.state)||
    typeof item.reason!=='string'||typeof item.createdAt!=='string'||!Number.isFinite(Date.parse(item.createdAt))||
    !(item.errorCode===null||typeof item.errorCode==='string'))invalid()
}

export async function submitLifecycleAction(stock:string,version:string,command:LifecycleCommand) {
  const result=await requestWithoutRetry<LifecycleOperation>(path(stock,version)+'/actions','POST',command,{'X-Aistock-Admin-Action':'1'})
  validateOperation(result,stock,version)
  if(result.operationId!==command.operationId||result.action!==command.action||
    result.expectedRevision!==command.expectedRevision||result.reason!==command.reason)invalid()
  return result
}

export async function getLifecycleOperation(stock:string,version:string,operation:string) {
  if(!uuid.test(operation))invalid()
  const result=await requestWithoutRetry<LifecycleOperation>(path(stock,version)+'/actions/'+operation,'GET')
  validateOperation(result,stock,version)
  if(result.operationId!==operation)invalid()
  return result
}
