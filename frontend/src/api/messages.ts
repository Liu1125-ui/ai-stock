import {request} from './auth'
export interface Message {
 id:string;triggerId:string;ruleId:string;instrumentId:string;tradeDate:string;condition:string;threshold:string;
 rawClose:string|null;previousBackwardClose:string|null;currentBackwardClose:string|null;definition:string;
 evidenceId:string;rawVersionId:string;capturedAt:string;createdAt:string;readAt:string|null
}
export interface MessagePage {items:Message[];page:number;size:number;total:number;unreadCount:number}
export interface TriggerPage {items:Message[];page:number;size:number;total:number}
export const listMessages=(page=1,size=20,unreadOnly=false)=>request<MessagePage>(`/messages?page=${page}&size=${size}&unreadOnly=${unreadOnly}`)
export const unreadCount=()=>request<{unreadCount:number}>('/messages/unread-count')
export const markRead=(id:string)=>request<Message>(`/messages/${encodeURIComponent(id)}/read`,'PATCH',{})
export const markBatchRead=(ids:string[])=>request<{items:Message[]}>('/messages/read','POST',{ids})
export const listAlertTriggers=(id:string,page=1)=>request<TriggerPage>(`/alerts/${encodeURIComponent(id)}/triggers?page=${page}&size=20`)
export type EmailDeliveryState='PENDING'|'CHECKING'|'SENDING'|'RETRY_WAIT'|'SENT'|'FAILED'|'CANCELLED'|'EXPIRED'|'UNKNOWN'|'NOT_REQUESTED'|'NOT_RECORDED'
export interface EmailDeliveryView {deliveryId:string|null;state:EmailDeliveryState;reasonCode:string|null;attempts:number;updatedAt:string|null;sentAt:string|null}
export const getMessageEmailDelivery=(id:string)=>request<EmailDeliveryView>(`/messages/${encodeURIComponent(id)}/email-delivery`)
