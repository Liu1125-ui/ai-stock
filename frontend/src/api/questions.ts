import {request} from './auth'
export interface QuestionInput {operationId:string;analysisId:string;question:string}
export interface QuestionAttempt extends QuestionInput {state:'RUNNING'|'SUCCEEDED'|'FAILED';answerId:string|null;errorCode:string|null;leaseUntil:string;expired:boolean}
export interface QuestionContext {facts:Record<string,string>;sourceAnalysisId:string;sourceContextHash:string;sourceCreatedAt:string;promptVersion:string;knowledgeVersion:string;providerModel:string;sourceContext:{instrumentId:string;marketVersionId:string;provider:string;from:string;to:string;prediction?:{originDate:string;verificationVersionId:string|null}}}
export interface QuestionAnswer {answerId:string;analysisId:string;question:string;createdAt:string;status:'ANSWERED'|'OUT_OF_SCOPE';topic:string;contextHash:string;context:QuestionContext;paragraphs:Array<{kind:string;text:string;refs:string[]}>}
export interface QuestionList {items:QuestionAnswer[];total:number;page:number;latestAttempt:QuestionAttempt|null;configured:boolean}
export const askQuestion=(input:QuestionInput)=>request<QuestionAttempt>('/stock-questions','POST',input)
export const questions=(analysisId:string,page=1)=>request<QuestionList>('/stock-questions?'+new URLSearchParams({analysisId,page:String(page)}))
export const question=(id:string)=>request<QuestionAnswer>('/stock-questions/'+encodeURIComponent(id))
export const questionOperation=(id:string)=>request<QuestionAttempt>('/stock-questions/operations/'+encodeURIComponent(id))
