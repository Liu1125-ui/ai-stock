import type {ComparisonAiInput} from '../../api/comparisonAi'
const prefix='aistock:comparison-ai-pending:'
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const ownerPrefix=(owner:string)=>prefix+encodeURIComponent(owner)+':'
const key=(owner:string,id:string)=>ownerPrefix(owner)+id
function valid(input:ComparisonAiInput){return input&&Object.keys(input).sort().join(',')==='comparisonId,operationId'&&typeof input.operationId==='string'&&uuid.test(input.operationId)&&typeof input.comparisonId==='string'&&uuid.test(input.comparisonId)}
export function savePending(owner:string,input:ComparisonAiInput){if(!owner||!valid(input))throw Error('解释恢复信息不正确');try{sessionStorage.setItem(key(owner,input.comparisonId),JSON.stringify(input))}catch{throw Error('无法保存解释恢复信息，请检查浏览器存储后再生成')}}
export function clearPending(owner:string,id:string){try{sessionStorage.removeItem(key(owner,id))}catch{/* cleanup never sends */}}
export function readPending(owner:string,id:string):ComparisonAiInput|null{try{const raw=sessionStorage.getItem(key(owner,id));if(!raw)return null;const value=JSON.parse(raw) as ComparisonAiInput;if(!valid(value)||value.comparisonId!==id)throw Error();return value}catch{clearPending(owner,id);throw Error('解释恢复记录损坏或存储不可用，未自动发送')}}
export function clearOwner(owner:string){try{const keys=[];for(let i=0;i<sessionStorage.length;i++){const name=sessionStorage.key(i);if(name?.startsWith(ownerPrefix(owner)))keys.push(name)}keys.forEach(name=>sessionStorage.removeItem(name))}catch{/* no new requests */}}
