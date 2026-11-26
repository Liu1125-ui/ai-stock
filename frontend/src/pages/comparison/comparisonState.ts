import type {ComparisonInput} from '../../api/comparisons'
const prefix='aistock:comparison-pending:'
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export const validateSelection=(ids:string[])=>ids.length>=2&&ids.length<=3&&new Set(ids).size===ids.length&&ids.every(id=>/^(SH|SZ)[0-9]{6}$/.test(id))
function key(owner:string){return prefix+encodeURIComponent(owner)}
function valid(input:ComparisonInput){return input&&Object.keys(input).sort().join(',')==='evidenceIds,from,instrumentIds,requestId,to'&&uuid.test(input.requestId)&&Array.isArray(input.instrumentIds)&&validateSelection(input.instrumentIds)&&Array.isArray(input.evidenceIds)&&input.evidenceIds.length===input.instrumentIds.length&&new Set(input.evidenceIds).size===input.evidenceIds.length&&input.evidenceIds.every(id=>typeof id==='string'&&uuid.test(id))&&typeof input.from==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(input.from)&&typeof input.to==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(input.to)}
export function savePending(owner:string,input:ComparisonInput){if(!valid(input))throw new Error('原请求格式不正确');try{sessionStorage.setItem(key(owner),JSON.stringify(input))}catch{throw new Error('无法保存恢复信息，请检查浏览器存储后再计算')}}
export function readPending(owner:string):ComparisonInput|null{try{const raw=sessionStorage.getItem(key(owner));if(!raw)return null;const input=JSON.parse(raw) as ComparisonInput;if(!valid(input))throw new Error();return input}catch{clearPending(owner);throw new Error('原请求恢复记录损坏或存储不可用，未自动发送请求')}}
export function clearPending(owner:string){try{sessionStorage.removeItem(key(owner))}catch{/* Never send a new request during cleanup. */}}
export function reasonText(reason:string|null){return ({CALENDAR_CONFLICT:'交易日历不一致，暂不可比较',NO_COMPARISON_EVIDENCE:'暂无可用对比证据',INSUFFICIENT_COVERAGE:'数据未覆盖所选周期',NO_COMMON_ENDPOINTS:'没有两个共同有效端点',PRICE_GAP_OR_SUSPENSION:'价格缺失或停牌',VOLUME_GAP_OR_SUSPENSION:'成交量缺失或停牌',INSUFFICIENT_RETURNS:'日收益观测不足'} as Record<string,string>)[reason??'']??'依据暂不可用'}
