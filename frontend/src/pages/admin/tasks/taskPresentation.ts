const states:Record<string,string>={QUEUED:'等待执行',RUNNING:'执行中',RECONCILING:'结果核对中',RETRY_WAIT:'等待自动重试',SUCCEEDED:'成功',EMPTY:'合法无数据',SKIPPED:'本轮无需执行',FAILED:'失败',CANCELLED:'已取消'}
export const taskStates=Object.entries(states).map(([value,label])=>({value,label}))
export const taskTypes=[{value:'MARKET_REFRESH',label:'行情更新'},{value:'PREDICTION_VERIFY',label:'预测验证'},{value:'COMPARISON_UPDATE',label:'股票比较更新'}]
export const taskStateLabel=(state:string)=>states[state]??'状态待识别'
export const attemptStateLabel=(state:string)=>state==='UNKNOWN'?'结果待确认':state==='EXPIRED'?'执行租约已过期':taskStateLabel(state)
export const taskTypeLabel=(type:string)=>taskTypes.find(item=>item.value===type)?.label??'类型待识别'
export function formatTaskTime(value:string|null){if(!value)return '—';const date=new Date(value);if(!Number.isFinite(date.getTime()))return '时间不可用';return new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).format(date)}
export function formatElapsed(value:number|null,end:string|null){return value===null?(end?'耗时不可用':'尚未结束'):value+' 毫秒'}
export function defaultDateRange(now=new Date()){const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(now);const part=(type:string)=>parts.find(p=>p.type===type)!.value;const createdTo=part('year')+'-'+part('month')+'-'+part('day');const start=new Date(createdTo+'T00:00:00Z');start.setUTCDate(start.getUTCDate()-29);return {createdFrom:start.toISOString().slice(0,10),createdTo}}
