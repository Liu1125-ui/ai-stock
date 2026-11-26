import {reactive} from 'vue'
import {vi} from 'vitest'
import type {TaskSummary,Detail,Page,Attempt} from '../../../api/adminTasks'
export const auth=reactive({profile:{userId:'admin-test',role:'ADMIN' as 'ADMIN'|'USER'},get isAdmin(){return this.profile.role==='ADMIN'},refresh:vi.fn().mockResolvedValue(undefined)})
export function summary(n=1):TaskSummary{return {taskId:'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),taskType:'MARKET_REFRESH',instrumentId:'SH600030',targetDate:'2026-10-20',state:'SUCCEEDED',totalAttempts:110,createdAt:'2026-10-20T01:00:00Z',updatedAt:'2026-10-20T01:01:00Z'}}
export function detail(n=1):Detail{return {task:{summary:summary(n),cycleAttempts:0,nextAt:'2026-10-20T01:00:00Z',resultVersionId:null,predictionId:null,ruleVersion:null,reason:{code:null,message:'暂无原因记录'}},queriedAt:'2026-10-20T02:00:00Z'}}
export function page<T>(items:T[],nextCursor:string|null=null):Page<T>{return {items,nextCursor,hasMore:nextCursor!==null,queriedAt:'2026-10-20T02:00:00Z'}}
export function attempt(ordinal=1):Attempt{return {ordinal,state:'UNKNOWN',startedAt:'2026-10-20T01:00:00Z',finishedAt:null,durationMillis:null,resultVersionId:null,reason:{code:'RESULT_UNKNOWN',message:'正在核对上次执行结果'}}}
export function deferred<T>(){let resolve!:(value:T)=>void;let reject!:(reason:unknown)=>void;const promise=new Promise<T>((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
