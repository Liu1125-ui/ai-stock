import {priceBasisLabel} from './priceBasisLabel'
import type {ComparisonResult} from '../api/comparisons'
import type {PortfolioAnalysis} from '../api/portfolioAnalyses'
import type {PredictionRecord} from '../api/predictions'

type Cell=string|number|null|undefined
type Row=readonly Cell[]
export type ExportRecord=ComparisonResult|PortfolioAnalysis|PredictionRecord
const header:Row=['类别','股票','日期','字段','数值','单位','状态或说明']

export function toCsv(rows:readonly Row[]){
 const encode=(value:Cell)=>{
  const text=String(value??'')
  const mayBeFormula=/^[\s\u0000-\u001f\u007f\uFEFF]*[=+\-@]/u.test(text)||/^[\t\r\n]/u.test(text)
  const neutralized=mayBeFormula&&!/^-\d+(?:\.\d+)?$/.test(text)?"'"+text:text
  return '"'+neutralized.replaceAll('"','""')+'"'
 }
 return '\uFEFF'+rows.map(row=>row.map(encode).join(',')).join('\r\n')+'\r\n'
}

function metadata(id:string,savedAt:string,label:string,timeLabel='保存时间'):Row[]{
 return [header,['记录','','','记录类型',label],['记录','','','记录编号',id],['记录','','',timeLabel,savedAt,'','ISO 8601']]
}
function details(group:string,stock:string,date:string,fields:readonly (readonly [string,Cell])[]):Row[]{
 return fields.map(([label,value])=>[group,stock,date,label,value])
}

export function comparisonCsv(record:ComparisonResult):Row[]{
 const rows=metadata(record.comparisonId,record.createdAt,'股票对比')
 rows.push(...details('口径与期间','','',[
  ['请求区间开始',record.requestedRange.from],['请求区间结束',record.requestedRange.to],
  ['共同有效区间开始',record.actualRange?.from],['共同有效区间结束',record.actualRange?.to],
  ['数据来源',record.provider],['价格口径',priceBasisLabel(record.basis)],['计算版本',record.formulaVersion],
  ['年化交易日数',record.annualizationDays],['来源校验',record.sourceHash],
 ]))
 const metrics=[['intervalReturn','区间涨跌幅'],['annualizedVolatility','年化波动率'],['maxDrawdown','最大回撤'],['meanVolume','日均成交量']] as const
 for(const stock of record.stocks){
  for(const [key,label] of metrics){
   const metric=stock[key]
   rows.push(['指标',stock.instrumentId,record.actualRange?.to,label,metric.status==='AVAILABLE'?metric.value:null,metric.unit,metric.reason??metric.status])
   rows.push(['指标',stock.instrumentId,record.actualRange?.to,label+'有效观测数',metric.observationCount])
  }
  for(const point of stock.chart)rows.push(['走势',stock.instrumentId,point.date,'归一化收盘价',point.normalizedClose,'共同起点＝100',point.normalizedClose===null?'价格缺失或停牌':''])
 }
 for(const source of record.sourceRefs)rows.push(...details('行情依据',source.instrumentId,source.to,[
  ['覆盖开始',source.from],['覆盖结束',source.to],['采集时间',source.capturedAt],['行情证据',source.evidenceId],
  ['原始行情版本',source.rawVersionId],['内容校验',source.contentHash],
 ]))
 return rows
}

export function predictionCsv(record:PredictionRecord):Row[]|null{
 if(record.state!=='SUCCEEDED'||!record.snapshot)return null
 const {input,prediction}=record.snapshot
 const rows=metadata(record.predictionId,record.createdAt,'五交易日预测','提交时间')
 rows.push(...details('预测依据',record.instrumentId,input.originDate,[
  ['预测原点',input.originDate],['原点收盘价（元）',input.originRawClose],['数据来源',input.provider],
  ['预测价格口径',priceBasisLabel(prediction.referenceBasis)],['原始行情版本',input.rawVersionId],['预测输入版本',input.inputVersionId],
  ['输入校验',input.inputHash],['模型制品',record.modelSnapshot.artifactId],['模型发布版本',prediction.publicationRevision],
  ['模型训练截止日',record.modelSnapshot.manifest.trainingCutoff],['模型权重校验',record.modelSnapshot.checkpointHash],
 ]))
 for(const point of prediction.points)rows.push(['预测',record.instrumentId,point.targetDate,'第'+point.step+'个目标交易日参考价格',point.referencePrice,'元','已保存预测值'])
 return rows
}

export function portfolioCsv(record:PortfolioAnalysis):Row[]{
 const rows=metadata(record.analysisId,record.createdAt,'持仓估值')
 rows.push(...details('估值依据','','',[
  ['估值截止日',record.asOfDate],['数据采集时间',record.capturedAt],['持仓版本',record.bookRevision],
  ['计算版本',record.formulaVersion],['估值状态',record.state],['已估值股票数',record.valuedCount],
  ['持仓股票数',record.positionCount],['来源校验',record.sourceHash],
 ]))
 const amounts=record.totals??record.subtotal
 const amountFields=[['costAmount','成本','元'],['marketValue','市值','元'],['floatingProfit','浮动盈亏','元'],['returnPercent','成本收益率','%']] as const
 for(const [key,label,unit] of amountFields)rows.push([record.totals?'组合合计':'可估值小计','',record.asOfDate,label,amounts[key],unit,amounts[key]===null?'无法计算':''])
 for(const line of record.lines){
  const {quote,instrumentId}=line
  for(const [key,label,unit] of amountFields)rows.push(['单股估值',instrumentId,quote.priceDate,label,line[key],unit,line[key]===null?'无法计算':''])
  rows.push(['单股估值',instrumentId,quote.priceDate,'市值占比',line.weightPercent,'%',line.weightPercent===null?'暂无有效占比':''])
  rows.push(...details('持仓及行情依据',instrumentId,quote.priceDate??'',[
   ['当次数量（股）',line.quantity],['当次平均成本（元）',line.averageCost],['持仓记录版本',line.positionRevision],
   ['数据来源',quote.provider],['价格口径',priceBasisLabel(quote.priceBasis)],['收盘价（元）',quote.close],
   ['报价状态',quote.status],['行情版本',quote.versionId],['内容校验',quote.contentHash],['缺价原因',quote.reason],
  ]))
 }
 return rows
}

export function savedResultExport(record:ExportRecord){
 if('predictionId' in record)return {id:record.predictionId,label:'预测',rows:predictionCsv(record)}
 if('comparisonId' in record)return {id:record.comparisonId,label:'对比',rows:comparisonCsv(record)}
 return {id:record.analysisId,label:'持仓估值',rows:portfolioCsv(record)}
}
