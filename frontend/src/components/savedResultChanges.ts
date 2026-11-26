import type {ComparisonResult} from '../api/comparisons'
import type {PortfolioAnalysis} from '../api/portfolioAnalyses'
import type {PredictionRecord} from '../api/predictions'

export interface ChangeField {
 key:string
 label:string
 value:string|null
 group:'记录与口径'|'持仓调整'|'行情依据'|'结果数值'
}
export interface ChangeSnapshot {
 id:string
 createdAt:string
 metricBasis:string|null
 fields:ChangeField[]
}

export function comparisonChanges(record:ComparisonResult|null):ChangeSnapshot|null {
 if(!record)return null
 const actual=record.actualRange
 const fields:ChangeField[]=[
  {key:'period',label:'请求区间',value:record.requestedRange.from+' 至 '+record.requestedRange.to,group:'记录与口径'},
  {key:'actual',label:'共同有效区间',value:actual?actual.from+' 至 '+actual.to:null,group:'记录与口径'},
  {key:'formula',label:'计算口径',value:[record.provider,record.basis,record.formulaVersion,record.annualizationDays+' 个交易日年化'].join(' · '),group:'记录与口径'},
  {key:'stocks',label:'对比股票',value:record.stocks.map(stock=>stock.instrumentId).sort().join(' / '),group:'记录与口径'},
 ]
 for(const source of record.sourceRefs)fields.push({key:'source:'+source.instrumentId,label:source.instrumentId+' 行情证据',value:source.rawVersionId+' · '+source.evidenceId,group:'行情依据'})
 const metrics=[['intervalReturn','区间涨跌幅'],['annualizedVolatility','年化波动率'],['maxDrawdown','最大回撤'],['meanVolume','日均成交量']] as const
 for(const stock of record.stocks){
  for(const [key,label] of metrics){
   const metric=stock[key]
   fields.push({key:stock.instrumentId+':'+key,label:stock.instrumentId+' '+label,value:metric.status==='AVAILABLE'&&metric.value!==null?metric.value+' '+metric.unit:null,group:'结果数值'})
  }
 }
 const metricBasis=actual?JSON.stringify(fields.slice(1,4).map(field=>field.value)):null
 return {id:record.comparisonId,createdAt:record.createdAt,fields,metricBasis}
}

export function portfolioChanges(record:PortfolioAnalysis|null):ChangeSnapshot|null {
 if(!record)return null
 const prices=[...new Set(record.lines.filter(line=>line.quote.status==='AVAILABLE').map(line=>line.quote.provider+' / '+line.quote.priceBasis))].sort()
 const fields:ChangeField[]=[
  {key:'period',label:'估值截止日',value:record.asOfDate,group:'记录与口径'},
  {key:'revision',label:'持仓版本',value:record.bookRevision,group:'记录与口径'},
  {key:'formula',label:'估值口径',value:[record.formulaVersion,...prices].join(' · '),group:'记录与口径'},
  {key:'coverage',label:'估值覆盖',value:record.valuedCount+' / '+record.positionCount+' 只'+(record.mixedDates?' · 价格日期不一致':''),group:'记录与口径'},
 ]
 for(const line of record.lines){
  const id=line.instrumentId
  fields.push(
   {key:'quantity:'+id,label:id+' 股数',value:line.quantity,group:'持仓调整'},
   {key:'cost:'+id,label:id+' 平均成本',value:line.averageCost+' 元',group:'持仓调整'},
   {key:'quote:'+id,label:id+' 报价及日期',value:line.quote.status==='AVAILABLE'?(line.quote.close??'报价暂无')+' 元 · '+(line.quote.priceDate??'日期暂无')+' · '+(line.quote.versionId??'版本暂无'):'缺少可用报价',group:'行情依据'},
  )
 }
 const totals=record.totals
 if(totals)for(const [key,label] of [['costAmount','成本'],['marketValue','市值'],['floatingProfit','浮动盈亏']] as const){
  const amount=totals[key]
  fields.push({key,label,value:amount===null?null:amount+' 元',group:'结果数值'})
 }
 const metricBasis=record.state==='COMPLETE'&&totals?JSON.stringify([record.formulaVersion,prices]):null
 return {id:record.analysisId,createdAt:record.createdAt,fields,metricBasis}
}

export function predictionChanges(record:PredictionRecord|null):ChangeSnapshot|null {
 if(record?.state!=='SUCCEEDED'||!record.snapshot)return null
 const {input,prediction}=record.snapshot
 const dates=prediction.points.map(point=>point.targetDate)
 const fields:ChangeField[]=[
  {key:'stock',label:'股票',value:record.instrumentId,group:'记录与口径'},
  {key:'origin',label:'预测原点',value:input.originDate,group:'记录与口径'},
  {key:'targets',label:'目标交易日',value:dates.join('、'),group:'记录与口径'},
  {key:'basis',label:'价格口径',value:prediction.referenceBasis==='ORIGIN_SHARE_BASIS'?'原点股本口径':prediction.referenceBasis,group:'记录与口径'},
  {key:'model',label:'模型与发布版本',value:record.modelSnapshot.artifactId+' / '+prediction.publicationRevision,group:'记录与口径'},
  {key:'source',label:'行情与输入版本',value:input.rawVersionId+' / '+input.inputVersionId,group:'行情依据'},
  {key:'close',label:'原点收盘价',value:input.originRawClose+' 元',group:'行情依据'},
  ...prediction.points.map(point=>({key:'target:'+point.targetDate,label:point.targetDate+' 参考价格',value:point.referencePrice+' 元',group:'结果数值' as const})),
 ]
 const metricBasis=prediction.referenceBasis&&dates.length===record.horizon
  ?JSON.stringify([record.instrumentId,input.originDate,input.originRawClose,prediction.referenceBasis,dates]):null
 return {id:record.predictionId,createdAt:record.createdAt,fields,metricBasis}
}
