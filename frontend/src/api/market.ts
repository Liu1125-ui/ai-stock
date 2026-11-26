import {request} from './auth'
export type ForecastReadiness='READY'|'CHECK_ON_SUBMIT'|'NO_DATA'|'INSUFFICIENT_HISTORY'|'STALE'|'CALENDAR_UNAVAILABLE'|'UNAVAILABLE'
export interface Summary{instrumentId:string;state:'AVAILABLE'|'NO_DATA'|'UNAVAILABLE';versionId:string|null;provider:string|null;priceBasis:'RAW';lastTradeDate:string|null;close:string|null;freshness:'FRESH'|'STALE'|'UNKNOWN';lastUpdateError:string|null;dailyChangePercent?:string|null;displayName?:string|null;displayNameVerified?:boolean;listingStatus?:string;forecastReadiness?:ForecastReadiness|null}
export interface Instrument{instrumentId:string;ticker:string;displayName:string;displayNameVerified:boolean;listingStatus:string;forecastCapability:'NOT_EVALUATED'|'UNAVAILABLE';forecastReason:string;summary:Summary}
export interface InstrumentPage{items:Instrument[];page:number;size:number;total:number}
export interface DailyPoint{date:string;open:string;high:string;low:string;close:string;volumeShares:string|null;amountYuan:string|null;tradeStatus:number;ma5:string|null;ma10:string|null;ma20:string|null;dif:string|null;dea:string|null;macd:string|null;rsi14:string|null}
export interface DailyResponse{instrumentId:string;state:'AVAILABLE'|'EMPTY'|'NO_DATA';versionId:string|null;provider:string|null;priceBasis:'RAW';lastTradeDate:string|null;fetchedAt:string|null;checkedThrough:string|null;requestedRange:{from:string;to:string};actualRange:{from:string;to:string}|null;quality:string;freshness:'FRESH'|'STALE'|'UNKNOWN';lastUpdateError:string|null;formulaVersion:string;missingTradingDates:string[];explanation:string;bars:ReadonlyArray<DailyPoint>}
export const searchInstruments=(q:string,page=1,size=20)=>request<InstrumentPage>(`/instruments?q=${encodeURIComponent(q)}&page=${page}&size=${size}`)
export const instrument=(id:string)=>request<Instrument>(`/instruments/${encodeURIComponent(id)}`)
export const summaries=(ids:string[])=>request<Summary[]>(`/instruments/summaries?ids=${encodeURIComponent(ids.join(','))}`)
export const daily=(id:string,params:{from?:string;to?:string;basis?:'RAW';versionId?:string}={})=>{const q=new URLSearchParams();for(const [key,value] of Object.entries(params))if(value)q.set(key,value);return request<DailyResponse>(`/instruments/${encodeURIComponent(id)}/daily${q.size?'?'+q:''}`)}

export interface MarketIndex {
  indexId: 'SSE_COMPOSITE' | 'CSI_300'
  sourceCode: string
  date: string
  close: string
  previousDate: string
  previousClose: string
  changePercent: string
}
export interface MarketOverviewData {
  freshness: 'FRESH' | 'STALE' | 'UNKNOWN' | 'NO_DATA'
  expectedTradeDate: string | null
  snapshot: {version: number; provider: string; fetchedAt: string; indices: MarketIndex[]} | null
}
export const marketOverview = () => request<MarketOverviewData>('/instruments/market-overview')

export interface StockValuation {
  state: 'AVAILABLE' | 'NO_DATA'
  instrumentId: string
  versionId: string
  tradeDate: string | null
  provider: string | null
  peTtm: string | null
  pbMrq: string | null
}
export const stockValuation = (id: string, versionId: string) =>
  request<StockValuation>(`/instruments/${encodeURIComponent(id)}/valuation?versionId=${encodeURIComponent(versionId)}`)

export interface LiveQuote {
  state: 'LIVE' | 'WAITING' | 'CLOSED' | 'STALE' | 'DISCONNECTED' | 'DISABLED'
  price: string | null
  currency: 'HKD'
  quoteTime: string | null
  receivedAt: string | null
}
export const pilotLiveQuote = () => request<LiveQuote>('/instruments/HK00700/realtime')
