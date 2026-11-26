import {request} from './auth'
export type AlertCondition='PRICE_ABOVE'|'PRICE_BELOW'|'DAILY_RISE'|'DAILY_FALL'
export interface AlertRule {id:string;instrumentId:string;condition:string;threshold:string;enabled:boolean;revision:number;effectiveAfterTradeDate:string;boundaryRawVersionId:string;createdAt:string;updatedAt:string;evaluationAvailability:'NOT_ENABLED'|'ENABLED'}
export interface AlertCheck {state:string;checkedAt:string;tradeDate:string|null}
export interface AlertPage {items:AlertRule[];page:number;size:number;total:number;recentChecks?:Record<string,AlertCheck>}
export interface AlertCreate {requestId:string;instrumentId:string;condition:AlertCondition;threshold:string}
export interface NotificationPreference {emailEnabled:boolean;revision:number;emailVerified:boolean;deliveryAvailability:'NOT_ENABLED'|'NOT_CONFIGURED'|'ENABLED'}
export const listAlerts=(page=1)=>request<AlertPage>(`/alerts?page=${page}&size=20`)
export const createAlert=(body:AlertCreate)=>request<AlertRule>('/alerts','POST',body)
export const patchAlert=(id:string,body:{revision:number;condition?:AlertCondition;threshold?:string;enabled?:boolean})=>request<AlertRule>('/alerts/'+encodeURIComponent(id),'PATCH',body)
export const getPreferences=()=>request<NotificationPreference>('/notification-preferences')
export const patchPreferences=(revision:number,emailEnabled:boolean)=>request<NotificationPreference>('/notification-preferences','PATCH',{revision,emailEnabled})
