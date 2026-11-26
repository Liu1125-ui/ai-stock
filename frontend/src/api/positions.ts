import {request} from './auth'
export interface PositionView{positionId:string;instrumentId:string;quantity:string;averageCost:string;revision:string;createdAt:string;updatedAt:string}
export interface PositionBook{bookRevision:string;items:PositionView[]}
export interface PositionReceipt{requestId:string;action:string;state:string;httpStatus:number;code:string;message:string;positionId:string|null;bookRevision:string;position:PositionView|null}
export interface CreatePosition{requestId:string;instrumentId:string;quantity:string;averageCost:string}
export interface UpdatePosition{requestId:string;expectedRevision:string;quantity:string;averageCost:string}
export interface DeletePosition{requestId:string;expectedRevision:string}
const path='/users/me/positions'
export const listPositions=()=>request<PositionBook>(path)
export const createPosition=(input:CreatePosition)=>request<PositionReceipt>(path,'POST',input)
export const updatePosition=(id:string,input:UpdatePosition)=>request<PositionReceipt>(path+'/'+encodeURIComponent(id),'PUT',input)
export const deletePosition=(id:string,input:DeletePosition)=>request<PositionReceipt>(path+'/'+encodeURIComponent(id),'DELETE',input)
export const positionOperation=(id:string)=>request<PositionReceipt>(path+'/operations/'+encodeURIComponent(id))
