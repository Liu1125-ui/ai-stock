import {request} from './auth'
export interface ComparisonAiInput {operationId:string;comparisonId:string}
export interface ComparisonAiOperation extends ComparisonAiInput {state:'RUNNING'|'SUCCEEDED'|'FAILED';explanationId:string|null;errorCode:string|null;leaseUntil:string;expired:boolean}
export interface ComparisonAiExplanation {explanationId:string;comparisonId:string;createdAt:string;contextHash:string;context:{comparisonId:string;sourceHash:string;formulaVersion:string;promptVersion:string;providerModel:string;facts:Record<string,string>};paragraphs:{kind:'overview'|'readout'|'risk';text:string;refs:string[]}[]}
export interface ComparisonAiView {configured:boolean;latest:ComparisonAiExplanation|null;latestAttempt:ComparisonAiOperation|null}
const base='/comparison-explanations'
export const comparisonAiView=(id:string)=>request<ComparisonAiView>(base+'?'+new URLSearchParams({comparisonId:id}))
export const createComparisonAi=(input:ComparisonAiInput)=>request<ComparisonAiOperation>(base,'POST',input)
export const comparisonAiOperation=(id:string)=>request<ComparisonAiOperation>(base+'/operations/'+encodeURIComponent(id))
export const comparisonAiExplanation=(id:string)=>request<ComparisonAiExplanation>(base+'/'+encodeURIComponent(id))
