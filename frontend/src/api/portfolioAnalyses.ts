import {request} from './auth'
export interface PortfolioQuote {instrumentId:string;status:'AVAILABLE'|'UNAVAILABLE';reason:string|null;close:string|null;priceDate:string|null;versionId:string|null;contentHash:string|null;provider:string;priceBasis:string;freshness:'FRESH'|'STALE'|'UNKNOWN';calendarVersionId:string|null;calendarHash:string|null;checkedThrough:string|null;retainedPrice:boolean}
export interface PortfolioAmounts {costAmount:string|null;marketValue:string|null;floatingProfit:string|null;returnPercent:string|null}
export interface PortfolioMoneyLine {positionId:string;instrumentId:string;quantity:string;averageCost:string;positionRevision:string;quote:PortfolioQuote;costAmount:string;marketValue:string|null;floatingProfit:string|null;returnPercent:string|null;weightPercent:string|null;concentrationTriggered:boolean|null}
export interface PortfolioAnalysis {analysisId:string;createdAt:string;formulaVersion:string;bookRevision:string;asOfDate:string;capturedAt:string;state:'COMPLETE'|'PARTIAL'|'MIXED_DATES'|'EMPTY';freshness:'FRESH'|'STALE'|'UNKNOWN';mixedDates:boolean;positionCount:number;valuedCount:number;lines:PortfolioMoneyLine[];subtotal:PortfolioAmounts;totals:PortfolioAmounts|null;concentrationThresholdPercent:string;sourceHash:string}
export type PortfolioAnalysisSummary=Omit<PortfolioAnalysis,'lines'>
export interface PortfolioAnalysisPage {items:PortfolioAnalysisSummary[];upper:string|null;nextBefore:string|null}
export interface PortfolioAnalysisOperation {requestId:string;state:'RUNNING'|'SUCCEEDED'|'FAILED';analysisId:string|null;errorCode:string|null;acceptedAt:string;expiresAt:string}
const path='/portfolio-analyses'
export const createPortfolioAnalysis=(requestId:string)=>request<PortfolioAnalysisOperation>(path,'POST',{requestId})
export const portfolioAnalysisOperation=(id:string)=>request<PortfolioAnalysisOperation>(path+'/operations/'+encodeURIComponent(id))
export const portfolioAnalysis=(id:string)=>request<PortfolioAnalysis>(path+'/'+encodeURIComponent(id))
export function listPortfolioAnalyses(before?:string,upper?:string){const query=new URLSearchParams();if(before)query.set('before',before);if(upper)query.set('upper',upper);return request<PortfolioAnalysisPage>(path+(query.size?'?'+query.toString():''))}
