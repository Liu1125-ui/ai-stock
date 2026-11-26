import {request} from './auth'
export type RuleTopic='PROFIT_FORMULA'|'RETURN_LIMIT'|'COVERAGE_LIMIT'|'CONCENTRATION_RULE'
export interface QuestionRequest {operationId:string;analysisId:string;question:string;disclosureAccepted:boolean}
export interface QuestionReceipt {operationId:string;analysisId:string;question:string;state:'RUNNING'|'SUCCEEDED'|'FAILED';answerId:string|null;errorCode:string|null;deadlineAt:string}
export interface QuestionAnswer {answerId:string;analysisId:string;question:string;status:'ANSWERED'|'OUT_OF_SCOPE';topic:string;createdAt:string;contextHash:string;context:{sourceAnalysisId:string;sourceHash?:string;facts:Record<string,string>;promptVersion?:string;providerModel?:string};paragraphs:{text:string;refs:string[]}[]}
export interface QuestionPage {items:QuestionAnswer[];upper:string|null;nextBefore:string|null;latestAttempt:QuestionReceipt|null;configured:boolean}
export interface PortfolioRule {source:'RULE';analysisId:string;topic:RuleTopic;ruleVersion:string;asOfDate:string;text:string;limits:string[];facts:Record<string,string>}
const root='/portfolio-questions'
export const createQuestion=(input:QuestionRequest)=>request<QuestionReceipt>(root,'POST',input)
export const getQuestionOperation=(id:string)=>request<QuestionReceipt>(root+'/operations/'+encodeURIComponent(id))
export const getQuestionAnswer=(id:string)=>request<QuestionAnswer>(root+'/'+encodeURIComponent(id))
export function listQuestions(analysisId:string,before?:string,upper?:string){const q=new URLSearchParams({analysisId});if(before)q.set('before',before);if(upper)q.set('upper',upper);return request<QuestionPage>(root+'?'+q.toString())}
export function getPortfolioRule(analysisId:string,topic:RuleTopic){return request<PortfolioRule>(root+'/rules?'+new URLSearchParams({analysisId,topic}).toString())}
