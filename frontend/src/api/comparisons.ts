import {request} from './auth'
export type Period='3M'|'6M'|'1Y'
export interface Range {from:string;to:string}
export interface ComparisonInput extends Range {requestId:string;instrumentIds:string[];evidenceIds:string[]}
export interface SourceOption {instrumentId:string;evidenceId:string|null;sourceFrom:string|null;sourceTo:string|null;capturedAt:string|null;rawHeadDate:string|null;reason:string|null}
export interface ComparisonOptions {available:boolean;period:Period;from:string|null;to:string|null;stocks:SourceOption[]}
export interface Metric {status:'AVAILABLE'|'UNAVAILABLE';reason:string|null;observationCount:number;value:string|null;unit:string}
export interface ChartPoint {date:string;normalizedClose:string|null}
export interface StockResult {instrumentId:string;intervalReturn:Metric;annualizedVolatility:Metric;maxDrawdown:Metric;meanVolume:Metric;chart:ChartPoint[];dataIssues?:{date:string;field:string;state:string}[]}
export interface SourceRef {instrumentId:string;evidenceId:string;rawVersionId:string;contentHash:string;from:string;to:string;capturedAt:string}
export interface ComparisonResult {comparisonId:string;createdAt:string;snapshotLabel:string;requestedRange:Range;actualRange:Range|null;formulaVersion:string;provider:string;basis:string;annualizationDays:number;sourceHash:string;stocks:StockResult[];sourceRefs:SourceRef[]}
export interface Operation {requestId:string;state:'RUNNING'|'SUCCEEDED'|'FAILED';comparisonId:string|null;errorCode:string|null;leaseUntil:string|null;expired:boolean}
export interface HistoryItem {comparisonId:string;createdAt:string;requestedRange:Range;actualRange:Range|null;stocks:Omit<StockResult,'chart'|'dataIssues'>[]}
export interface HistoryPage {items:HistoryItem[];upper:string|null;nextBefore:string|null}
export const comparisonOptions=(ids:string[],period:Period)=>request<ComparisonOptions>('/comparisons/options?'+new URLSearchParams({ids:ids.join(','),period}))
export const createComparison=(input:ComparisonInput)=>request<Operation>('/comparisons','POST',input)
export const comparisonOperation=(id:string)=>request<Operation>('/comparisons/operations/'+encodeURIComponent(id))
export const comparisonResult=(id:string)=>request<ComparisonResult>('/comparisons/'+encodeURIComponent(id))
export const comparisonHistory=(before?:string,upper?:string)=>{const query=new URLSearchParams();if(before)query.set('before',before);if(upper)query.set('upper',upper);return request<HistoryPage>('/comparisons'+(query.size?'?'+query:''))}
