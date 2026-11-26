import {request,ApiError} from './auth'
export interface ListFilter {q?:string|null;provider?:string|null;headState?:string|null;page:number;size:number}
export interface HistoryFilter {provider?:string|null;cursor?:string|null;size?:number}
export interface DetailFilter {from:string;to:string}
export interface Meta {queriedAt:string;definitionVersion:string}
export interface HeadRow {instrumentId:string;ticker:string;displayName:string;nameVerified:boolean;listingStatus:string;provider:string|null;priceBasis:string|null;headPresent:boolean;headState:string;headVersionId:string|null;headRevision:string|null;versionStatus:string|null;quality:string|null;coverageStart:string|null;coverageEnd:string|null;checkedAt:string|null;fetchedAt:string|null;publishedAt:string|null;lastErrorCode:string|null}
export interface HeadPage extends Meta {query:ListFilter;items:HeadRow[];hasMore:boolean}
export interface VersionSummary {versionId:string;provider:string;priceBasis:string;status:string;quality:string;coverageStart:string;coverageEnd:string;fetchedAt:string;publishedAt:string;isCurrent:boolean}
export interface Receipt {operationId:string;state:string;versionId:string|null;errorCode:string|null;createdAt:string;finishedAt:string|null}
export interface RefreshReceipt extends Receipt {targetDate:string}
export interface VersionPage extends Meta {instrumentId:string;provider:string|null;items:VersionSummary[];nextCursor:string|null;recentIngestions:Receipt[];recentRefreshes:RefreshReceipt[];ingestionsTruncated:boolean;refreshesTruncated:boolean}
export interface Finding {count:string;sampleDates:string[];truncated:boolean}
export interface Diagnostics {state:string;calendarEvidence:string;effectiveFrom:string|null;effectiveTo:string|null;outsideCoverageDays:string;barDays:string;tradedRowDays:string;suspendedRowDays:string;knownOpenDays:string;unknownCalendarDays:string;suspectedMissingOpenDays:Finding;barsOnClosedDays:Finding;invalidRowDays:Finding}
export interface VersionDetail extends Meta {instrumentId:string;version:VersionSummary&{contentHash:string|null;sourcePayloadHash:string|null;headRevision:string|null};window:DetailFilter&{timeZone:string};diagnostics:Diagnostics}
const DEFINITION='admin-market-readonly-v1',UUID=/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/
function check(ok:unknown):asserts ok {if(!ok)throw new ApiError('INVALID_RESPONSE','行情管理响应不完整，请重新查询',502)}
function object(v:unknown):Record<string,unknown>{check(v!==null&&typeof v==='object'&&!Array.isArray(v));return v as Record<string,unknown>}
function text(v:unknown,nullable=false){check(nullable&&v===null||typeof v==='string'&&v.length>0&&v.length<=512)}
function count(v:unknown,nullable=false){check(nullable&&v===null||typeof v==='string'&&/^(0|[1-9][0-9]*)$/.test(v)&&v.length<=30)}
function date(v:unknown,nullable=false){if(nullable&&v===null)return;check(typeof v==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(v)&&Number.isFinite(Date.parse(v+'T00:00:00Z'))&&new Date(v+'T00:00:00Z').toISOString().slice(0,10)===v)}
function time(v:unknown,nullable=false){check(nullable&&v===null||typeof v==='string'&&/^\d{4}-\d{2}-\d{2}T/.test(v)&&Number.isFinite(Date.parse(v)))}
function array(v:unknown,max:number){check(Array.isArray(v)&&v.length<=max);return v.map(object)}
function meta(v:unknown){const r=object(v);check(r.definitionVersion===DEFINITION);time(r.queriedAt);return r}
function version(v:Record<string,unknown>){check(typeof v.versionId==='string'&&UUID.test(v.versionId));for(const k of ['provider','priceBasis','status','quality'])text(v[k]);for(const k of ['coverageStart','coverageEnd'])date(v[k]);check((v.coverageStart as string)<=(v.coverageEnd as string));for(const k of ['fetchedAt','publishedAt'])time(v[k]);check(typeof v.isCurrent==='boolean')}
function receipt(v:Record<string,unknown>){for(const k of ['operationId','state'])text(v[k]);for(const k of ['versionId','errorCode'])text(v[k],true);time(v.createdAt);time(v.finishedAt,true)}
function query(v:object){const q=new URLSearchParams();Object.entries(v).forEach(([k,val])=>{if(val!==null&&val!==undefined&&val!=='')q.set(k,String(val))});return q.toString()}
function stock(id:string){check(/^(SH|SZ)[0-9]{6}$/.test(id));return id}
export async function getHeads(filter:ListFilter):Promise<HeadPage>{
 const r=meta(await request<unknown>('/admin/market-data?'+query(filter))),q=object(r.query);check(q.page===filter.page&&q.size===filter.size&&q.q===(filter.q?.trim()||null)&&q.provider===(filter.provider||null)&&q.headState===(filter.headState||null)&&typeof r.hasMore==='boolean');
 for(const x of array(r.items,filter.size)){stock(String(x.instrumentId));for(const k of ['ticker','displayName','listingStatus','headState'])text(x[k]);for(const k of ['provider','priceBasis','headVersionId','versionStatus','quality','lastErrorCode'])text(x[k],true);count(x.headRevision,true);for(const k of ['coverageStart','coverageEnd'])date(x[k],true);for(const k of ['checkedAt','fetchedAt','publishedAt'])time(x[k],true);check(typeof x.headPresent==='boolean'&&typeof x.nameVerified==='boolean');if(!x.headPresent)check(x.headState==='NO_VERSION'&&x.headVersionId===null&&x.headRevision===null)}
 return r as unknown as HeadPage
}
export async function getVersions(id:string,filter:HistoryFilter):Promise<VersionPage>{
 const r=meta(await request<unknown>('/admin/market-data/'+stock(id)+'/versions?'+query(filter)));check(r.instrumentId===id&&r.provider===(filter.provider||null));array(r.items,filter.size||20).forEach(version);text(r.nextCursor,true);if(r.nextCursor!==null)check(/^[A-Za-z0-9_-]{1,512}$/.test(r.nextCursor as string));for(const k of ['recentIngestions','recentRefreshes'])array(r[k],10).forEach(receipt);array(r.recentRefreshes,10).forEach(x=>date(x.targetDate));check(typeof r.ingestionsTruncated==='boolean'&&typeof r.refreshesTruncated==='boolean');return r as unknown as VersionPage
}
export async function getVersionDetail(id:string,versionId:string,filter:DetailFilter):Promise<VersionDetail>{
 check(UUID.test(versionId));const r=meta(await request<unknown>('/admin/market-data/'+stock(id)+'/versions/'+versionId+'?'+query(filter))),v=object(r.version),w=object(r.window),d=object(r.diagnostics);check(r.instrumentId===id&&v.versionId===versionId&&w.from===filter.from&&w.to===filter.to&&w.timeZone==='Asia/Shanghai');version(v);for(const k of ['contentHash','sourcePayloadHash'])check(v[k]===null||typeof v[k]==='string'&&/^[a-fA-F0-9]{64}$/.test(v[k] as string));count(v.headRevision,true);
 check(['OUTSIDE_COVERAGE','ISSUES','NEEDS_REVIEW','INCOMPLETE_EVIDENCE','NO_RECORDED_ISSUES'].includes(d.state as string)&&d.calendarEvidence==='VERSION_ATTACHED');date(d.effectiveFrom,true);date(d.effectiveTo,true);check((d.effectiveFrom===null)===(d.effectiveTo===null));for(const k of ['outsideCoverageDays','barDays','tradedRowDays','suspendedRowDays','knownOpenDays','unknownCalendarDays'])count(d[k]);
 for(const k of ['suspectedMissingOpenDays','barsOnClosedDays','invalidRowDays']){const f=object(d[k]);count(f.count);check(Array.isArray(f.sampleDates)&&f.sampleDates.length<=20&&typeof f.truncated==='boolean');let last='';for(const day of f.sampleDates){date(day);check(d.effectiveFrom!==null&&(day as string)>=(d.effectiveFrom as string)&&(day as string)<=(d.effectiveTo as string)&&(day as string)>last);last=day as string}const n=BigInt(f.count as string);check(n>=BigInt(f.sampleDates.length)&&f.truncated===(n>BigInt(f.sampleDates.length)))}
 check(BigInt(d.barDays as string)===BigInt(d.tradedRowDays as string)+BigInt(d.suspendedRowDays as string));return r as unknown as VersionDetail
}
