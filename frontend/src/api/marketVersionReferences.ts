import {ApiError,requestWithoutRetry} from './auth'
export type ReferenceOwner='prediction'|'scheduler'|'notification'
export interface ReferenceGroup{category:string;label:string;state:'COMPLETE'|'UNKNOWN';rows:string|null;reasonCode:string|null}
export interface ReferenceOwnerResult{owner:ReferenceOwner;versionId:string;queriedAt:string;state:'COMPLETE'|'PARTIAL'|'UNKNOWN';reasonCode:string|null;groups:ReferenceGroup[]}
export interface VersionReferenceReport{instrumentId:string;versionId:string;provider:string;priceBasis:string;versionState:string;currentHead:boolean;startedAt:string;finishedAt:string;state:'COMPLETE'|'PARTIAL';owners:ReferenceOwnerResult[]}
const uuid=/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/,owners=new Set(['prediction','scheduler','notification'])
const reasons=new Set(['REFERENCE_QUERY_BUSY','REFERENCE_QUERY_UNAVAILABLE','REFERENCE_QUERY_BUDGET','REFERENCE_ROW_LIMIT','REFERENCE_JSON_LIMIT','REFERENCE_JSON_INVALID','REFERENCE_SERVICE_UNAVAILABLE','REFERENCE_RESPONSE_INVALID'])
function invalid():never{throw new ApiError('INVALID_RESPONSE','版本引用返回的数据无法核对，请稍后刷新',502)}
function text(v:unknown,max:number):v is string{return typeof v==='string'&&v.length>0&&v.length<=max&&!/[\u0000-\u001f\u007f]/.test(v)}
function instant(v:unknown):v is string{return typeof v==='string'&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(v)&&Number.isFinite(Date.parse(v))}
function keys(v:object,names:string[]){return Object.keys(v).sort().join(',')===names.sort().join(',')}
function checkOwner(v:ReferenceOwnerResult,version:string){
 if(!v||typeof v!=='object'||!keys(v,['owner','versionId','queriedAt','state','reasonCode','groups'])||!owners.has(v.owner)||v.versionId!==version||!instant(v.queriedAt)||!Array.isArray(v.groups)||v.groups.length>32)invalid()
 if(v.state==='UNKNOWN'){if(v.groups.length!==0||!reasons.has(v.reasonCode||''))invalid();return}
 if(!['COMPLETE','PARTIAL'].includes(v.state)||v.groups.length===0)invalid();let complete=true;const seen=new Set<string>()
 for(const g of v.groups){if(!g||!keys(g,['category','label','state','rows','reasonCode'])||!text(g.category,64)||!/^[a-z][a-z0-9_]*$/.test(g.category)||seen.has(g.category)||!text(g.label,80))invalid();seen.add(g.category)
  if(g.state==='COMPLETE'){if(typeof g.rows!=='string'||!/^(0|[1-9][0-9]{0,3})$/.test(g.rows)||Number(g.rows)>1000||g.reasonCode!==null)invalid()}
  else if(g.state==='UNKNOWN'){complete=false;if(g.rows!==null||!reasons.has(g.reasonCode||''))invalid()}else invalid()
 }
 if(complete?(v.state!=='COMPLETE'||v.reasonCode!==null):(v.state!=='PARTIAL'||v.reasonCode!=='REFERENCE_PARTIAL'))invalid()
}
export async function getMarketVersionReferences(stock:string,version:string):Promise<VersionReferenceReport>{
 if(!/^(SH|SZ)[0-9]{6}$/.test(stock)||!uuid.test(version))invalid()
 const v=await requestWithoutRetry<VersionReferenceReport>('/admin/market-data/'+stock+'/versions/'+version+'/references','GET')
 if(!v||typeof v!=='object'||!keys(v,['instrumentId','versionId','provider','priceBasis','versionState','currentHead','startedAt','finishedAt','state','owners'])||v.instrumentId!==stock||v.versionId!==version||!text(v.provider,32)||!text(v.priceBasis,32)||!['AVAILABLE','STAGING','REJECTED','UNAVAILABLE'].includes(v.versionState)||typeof v.currentHead!=='boolean'||!instant(v.startedAt)||!instant(v.finishedAt)||Date.parse(v.finishedAt)<Date.parse(v.startedAt)||!Array.isArray(v.owners)||v.owners.length!==3)invalid()
 const seen=new Set<string>();let complete=true;for(const owner of v.owners){checkOwner(owner,version);if(seen.has(owner.owner))invalid();seen.add(owner.owner);complete&&=owner.state==='COMPLETE'}
 if(seen.size!==3||v.state!==(complete?'COMPLETE':'PARTIAL'))invalid();return v
}
