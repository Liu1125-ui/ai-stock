import {request,ApiError} from './auth'
export type TaskType='MARKET_REFRESH'|'PREDICTION_VERIFY'|'COMPARISON_UPDATE'
export interface Reason{code:string|null;message:string}
export interface TaskSummary{taskId:string;taskType:string;instrumentId:string;targetDate:string;state:string;totalAttempts:number;createdAt:string;updatedAt:string}
export interface TaskDetail{summary:TaskSummary;cycleAttempts:number;nextAt:string|null;resultVersionId:string|null;predictionId:string|null;ruleVersion:string|null;reason:Reason}
export interface Attempt{ordinal:number;state:string;startedAt:string;finishedAt:string|null;durationMillis:number|null;resultVersionId:string|null;reason:Reason}
export interface Detail{task:TaskDetail;queriedAt:string}
export interface Page<T>{items:T[];nextCursor:string|null;hasMore:boolean;queriedAt:string}
export interface TaskQuery{type?:TaskType;state?:string;instrumentId?:string;createdFrom:string;createdTo:string;limit:number}
function invalid():never{throw new ApiError('INVALID_RESPONSE','任务服务响应异常，请稍后重试',502)}
function count(n:unknown){if(typeof n!=='number'||!Number.isSafeInteger(n)||n<0)invalid()}
function reason(value:Reason){if(!value||typeof value.message!=='string'||(value.code!==null&&typeof value.code!=='string'))invalid()}
function fields(value:object,keys:string[]){const record=value as unknown as Record<string,unknown>;for(const key of keys)if(typeof record[key]!=='string')invalid()}
function summary(value:TaskSummary){if(!value||typeof value.taskId!=='string'||typeof value.state!=='string')invalid();fields(value,['taskId','taskType','instrumentId','targetDate','state','createdAt','updatedAt']);count(value.totalAttempts)}
function checkedPage<T>(value:Page<T>,check:(item:T)=>void):Page<T>{if(!value||!Array.isArray(value.items)||typeof value.hasMore!=='boolean'||typeof value.queriedAt!=='string'||(value.hasMore?typeof value.nextCursor!=='string'||!value.nextCursor:value.nextCursor!==null))invalid();value.items.forEach(check);return value}
export async function listAdminTasks(query:TaskQuery,cursor?:string):Promise<Page<TaskSummary>>{const params=new URLSearchParams();for(const [key,value] of Object.entries(query))if(value!==undefined&&value!=='')params.set(key,String(value));if(cursor!==undefined)params.set('cursor',cursor);return checkedPage(await request<Page<TaskSummary>>('/admin/tasks?'+params),summary)}
export async function getAdminTask(taskId:string):Promise<Detail>{const value=await request<Detail>('/admin/tasks/'+encodeURIComponent(taskId));if(!value?.task||typeof value.queriedAt!=='string')invalid();summary(value.task.summary);count(value.task.cycleAttempts);reason(value.task.reason);for(const key of ['nextAt','resultVersionId','predictionId','ruleVersion'] as const)if(value.task[key]!==null&&typeof value.task[key]!=='string')invalid();return value}
export async function listAdminTaskAttempts(taskId:string,limit=20,cursor?:string):Promise<Page<Attempt>>{const params=new URLSearchParams({limit:String(limit)});if(cursor!==undefined)params.set('cursor',cursor);return checkedPage(await request<Page<Attempt>>('/admin/tasks/'+encodeURIComponent(taskId)+'/attempts?'+params),item=>{if(!item)invalid();fields(item,['state','startedAt']);reason(item.reason);if(item.finishedAt!==null&&typeof item.finishedAt!=='string')invalid();if(item.resultVersionId!==null&&typeof item.resultVersionId!=='string')invalid();count(item.ordinal);if(item.ordinal<1)invalid();if(item.durationMillis!==null)count(item.durationMillis)})}
