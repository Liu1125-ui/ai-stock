import {request} from './auth'
export interface AnalysisInput {operationId:string;instrumentId:string;from:string;to:string;marketVersionId:string;predictionId?:string|null}
export interface AnalysisAttempt {operationId:string;state:'RUNNING'|'SUCCEEDED'|'FAILED';analysisId:string|null;contextHash:string|null;errorCode:string|null;leaseUntil:string;executionGeneration?:number;fencingRevision?:number;contextReady?:boolean;expired?:boolean;instrumentId?:string;marketVersionId?:string;from?:string;to?:string;predictionId?:string|null}
export interface AnalysisResult {analysisId:string;createdAt:string;contextHash:string;context:{instrumentId:string;marketVersionId:string;from:string;to:string;provider:string;priceBasis:string;formulaVersion:string;promptVersion:string;providerModel:string;prediction?:{predictionId:string;originDate:string;verificationVersionId:string|null;artifactId:string;inputVersionId:string;rawVersionId:string;manifestHash:string;checkpointHash:string;facts:Record<string,string>};facts:Record<string,string>};paragraphs:{kind:string;text:string;refs:string[]}[]}
export interface AnalysisList {configured:boolean;items:AnalysisResult[];total:number;page:number;latestAttempt:AnalysisAttempt|null}
export const analyses=(id:string,from:string,to:string,page=1)=>request<AnalysisList>('/stock-analyses?'+new URLSearchParams({instrumentId:id,from,to,page:String(page)}))
export const generateAnalysis=(input:AnalysisInput)=>request<AnalysisAttempt>('/stock-analyses','POST',input)

export interface PredictionOption {predictionId:string;originDate:string;createdAt:string;artifactId:string;targetFrom:string;targetTo:string}
export interface PredictionOptions {items:PredictionOption[];total:number;page:number;size:number}
export const predictionOptions=(id:string,page=1)=>request<PredictionOptions>('/stock-analyses/prediction-options?'+new URLSearchParams({instrumentId:id,page:String(page)}))
