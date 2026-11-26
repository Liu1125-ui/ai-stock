import {request} from './auth'
export interface PublishedModel{modelId:string;artifactId:string;window:number;horizon:number;instruments:string[];recommended:boolean;featureDefinition:string;trainingCutoff:string}
// Shared display eligibility; the prediction service still validates publication and fresh inputs on submit.
export const supportsFiveDayPrediction=(model:PublishedModel,instrumentId:string)=>model.horizon===5&&model.instruments.includes(instrumentId)
export interface PredictionInput{operationId:string;modelId:string;instrumentId:string;horizon:number}
export interface PredictionPoint{step:number;targetDate:string;ratio:string;referencePrice:string}
export interface PredictionRecord{predictionId:string;operationId:string;modelId:string;instrumentId:string;horizon:number;state:'RUNNING'|'SUCCEEDED'|'FAILED';errorCode:string|null;hidden:boolean;createdAt:string;expiresAt:string;modelSnapshot:{artifactId:string;manifestHash:string;checkpointHash:string;revision:number;manifest:{trainingCutoff:string}};snapshot:null|{input:{provider?:string;inputVersionId:string;rawVersionId:string;inputHash:string;rawContentHash:string;originDate:string;originRawClose:string};prediction:{points:PredictionPoint[];publicationRevision:number;referenceBasis:string}}}
export interface PredictionHistory{items:PredictionRecord[];total:number;page:number;size:number}
export const publishedModels=()=>request<{items:PublishedModel[];headRevision:number}>('/models')
export const createPrediction=(body:PredictionInput)=>request<PredictionRecord>('/predictions','POST',body)
export const prediction=(id:string)=>request<PredictionRecord>('/predictions/'+encodeURIComponent(id))
export interface LatestStockPrediction {instrumentId:string;predictionId:string;state:PredictionRecord['state'];horizon:number;createdAt:string}
export const latestStockPredictions=(ids:string[])=>request<LatestStockPrediction[]>('/predictions/latest-by-instrument?ids='+encodeURIComponent(ids.join(',')))
export const predictionHistory=(page=1)=>request<PredictionHistory>('/predictions?page='+page)
export const hidePrediction=(id:string)=>request<PredictionRecord>('/predictions/'+encodeURIComponent(id)+'/hide','POST',{})
export function predictionFailure(code:string|null){const labels:Record<string,string>={FORECAST_INPUT_NOT_AVAILABLE:'暂无可用于预测的行情版本，请等待行情更新后再试',FORECAST_CALENDAR_INVALID:'交易日历无法确认后续五个有效交易日，请等待日历数据补齐',MODEL_INPUT_INCOMPATIBLE:'当前模型与所选股票的预测输入不兼容，请重新检查已发布模型',FORECAST_INPUT_STALE:'行情缺少应有交易日，请等待行情更新成功后再发起预测；历史结果仍可查看',FORECAST_INPUT_FRESHNESS_UNKNOWN:'交易日历不足，尚不能确认行情是否最新，请等待更新核验后再试',FORECAST_WINDOW_INCOMPLETE:'可用历史样本不足60个交易日，暂不能发起预测',SOURCE_TIMEOUT:'行情源响应超时，本次未生成预测；请稍后再试，历史结果仍可查看',SOURCE_UNAVAILABLE:'行情源暂不可用，本次未生成预测；请等待数据源恢复',SOURCE_INVALID:'行情源返回的数据未通过校验，本次未生成预测',MARKET_VERSION_UNAVAILABLE:'所选行情版本不可用，请等待行情更新完成后再发起预测',FORECAST_INPUT_INVALID:'预测输入未通过校验，本次未生成预测',MODEL_NOT_PUBLISHED:'模型尚未发布或已下线',MODEL_INPUT_NOT_CURRENT:'行情版本已更新，请重新发起预测',PREDICTION_INTERRUPTED:'处理已中断或超时，请重新发起',MODEL_VALIDATION_BUSY:'模型正在处理其他请求，请稍后重新发起',SOURCE_BUSY:'数据采集正在进行，请稍后再试',PREDICTION_RESULT_INVALID:'结果校验未通过，请稍后重新发起'};return labels[code||'']||'预测未完成，原行情和历史结果仍可查看'}

export interface FusionSelection {predictionId:string;weightBps:number}
interface FusionMetrics {ratioMae:string|null;ratioRmse:string|null;directionAccuracy:string|null;baselineRatioMae:string|null}
export interface FusionAssessment {
  state:'NO_SHARED_EVIDENCE'|'EVIDENCE_UNAVAILABLE'|'PENDING'|'PARTIAL'|'COMPLETE'
  fusion:null|{verifiedCount:number;evidenceVersionId:string;originRawRevised:boolean;metrics:FusionMetrics}
  members:{predictionId:string;metrics:FusionMetrics}[]
}
export interface FusionPreview {
  instrumentId:string
  originDate:string
  inputVersionId:string
  rawVersionId:string
  baselinePrice:string
  sources:{predictionId:string;modelId:string;artifactId:string;publicationRevision:number;weightBps:number;referencePrices:string[]}[]
  points:PredictionPoint[]
  assessment:FusionAssessment
}
export const previewPredictionFusion=(items:FusionSelection[])=>
  request<FusionPreview>('/predictions/fusion-preview','POST',{items})
