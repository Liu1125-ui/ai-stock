import {validCommand,type RefreshCommand} from '../../../api/adminMarketRefresh'
export interface PendingCommand{instrumentId:string;command:RefreshCommand}
const prefix='aistock:admin-market-refresh:'
const key=(owner:string,stock:string)=>prefix+encodeURIComponent(owner)+':'+stock
function valid(v:unknown,stock:string):v is PendingCommand{if(!v||typeof v!=='object')return false;const p=v as PendingCommand;return Object.keys(p).sort().join(',')==='command,instrumentId'&&p.instrumentId===stock&&/^(SH|SZ)[0-9]{6}$/.test(stock)&&validCommand(p.command)}
export function savePending(owner:string,p:PendingCommand){if(!owner||!valid(p,p.instrumentId))throw new Error('原请求恢复信息不完整，尚未提交');try{sessionStorage.setItem(key(owner,p.instrumentId),JSON.stringify(p))}catch{throw new Error('无法保存原操作编号，尚未提交')}}
export function loadPending(owner:string,stock:string):PendingCommand|null{let raw:string|null;try{raw=sessionStorage.getItem(key(owner,stock))}catch{throw new Error('无法读取原操作编号，暂不能提交新请求')}if(raw===null)return null;try{const v:unknown=JSON.parse(raw);if(!valid(v,stock))throw new Error();return v}catch{throw new Error('原请求记录损坏，请先核对操作历史')}}
export function clearPending(owner:string,stock:string){try{sessionStorage.removeItem(key(owner,stock))}catch{/* A remaining marker causes a read-only recovery on next mount. */}}
export function clearOwner(owner:string){try{const start=prefix+encodeURIComponent(owner)+':';for(let i=sessionStorage.length-1;i>=0;i--){const k=sessionStorage.key(i);if(k?.startsWith(start))sessionStorage.removeItem(k)}}catch{/* Account sign-out must remain available. */}}
