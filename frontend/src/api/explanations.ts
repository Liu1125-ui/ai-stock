import {request} from './auth'
export interface ExplanationAttempt{operationId:string;state:'RUNNING'|'SUCCEEDED'|'FAILED';contextHash:string;explanationId:string|null;errorCode:string|null;leaseUntil:string;expired?:boolean}
export interface ExplanationView{configured:boolean;currentContextHash:string;stale:boolean;latest:null|{explanationId:string;contextHash:string;createdAt:string;needsReview?:boolean;context:{facts:Record<string,string>;providerModel:string;promptVersion:string;[key:string]:unknown};paragraphs:{kind:string;text:string;refs:string[]}[]};latestAttempt:ExplanationAttempt|null}
export const explanation=(id:string)=>request<ExplanationView>('/predictions/'+encodeURIComponent(id)+'/explanation')
export const generateExplanation=(id:string,operationId:string)=>request<ExplanationAttempt>('/predictions/'+encodeURIComponent(id)+'/explanation','POST',{operationId})
