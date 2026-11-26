import type {EChartsOption} from 'echarts'
import type {DailyResponse} from '../../api/market'
import {formatDecimal} from './format'
const numeric=(text:string|null)=>text===null?null:Number(text)
const html=(value:string)=>value.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]??c))
export function buildMarketChartOption(response:DailyResponse):EChartsOption{
 const bars=response.bars,dates=bars.map(b=>b.date)
 const labels=['价格与均线','成交量','MACD','RSI 14']
 const grid=[{left:64,right:25,top:58,height:'39%'},{left:64,right:25,top:'51%',height:'11%'},{left:64,right:25,top:'66%',height:'11%'},{left:64,right:25,top:'81%',height:'10%'}]
 const xAxis=labels.map((_,i)=>({type:'category' as const,data:dates,gridIndex:i,boundaryGap:true,axisLabel:{show:i===3}}))
 const yAxis=labels.map((_,i)=>({type:'value' as const,scale:i!==3,gridIndex:i,splitNumber:3,axisLabel:{fontSize:11}}))
 const line=(name:string,field:'ma5'|'ma10'|'ma20'|'dif'|'dea'|'rsi14',pane:number,color:string)=>({name,type:'line' as const,xAxisIndex:pane,yAxisIndex:pane,data:bars.map(b=>numeric(b[field])),showSymbol:false,connectNulls:false,lineStyle:{width:1.5,color},itemStyle:{color}})
 const series:EChartsOption['series']=[
  {name:'日线',type:'candlestick',xAxisIndex:0,yAxisIndex:0,data:bars.map(b=>[Number(b.open),Number(b.close),Number(b.low),Number(b.high)]),itemStyle:{color:'#d94b55',color0:'#22a27a',borderColor:'#d94b55',borderColor0:'#22a27a'}},
  line('MA5','ma5',0,'#315ed0'),line('MA10','ma10',0,'#e29b24'),line('MA20','ma20',0,'#7a68c7'),
  {name:'成交量',type:'bar',xAxisIndex:1,yAxisIndex:1,data:bars.map(b=>numeric(b.volumeShares)),itemStyle:{color:'#7397d4'}},
  {name:'MACD柱',type:'bar',xAxisIndex:2,yAxisIndex:2,data:bars.map(b=>numeric(b.macd)),itemStyle:{color:'#6b91c9'}},
  line('DIF','dif',2,'#315ed0'),line('DEA','dea',2,'#e29b24'),line('RSI14','rsi14',3,'#7a68c7')]
 return {backgroundColor:'#fff',animation:false,legend:{top:6,left:52,data:['日线','MA5','MA10','MA20','成交量','MACD柱','DIF','DEA','RSI14'],textStyle:{color:'#475569'}},
  grid,xAxis,yAxis,series,axisPointer:{link:[{xAxisIndex:'all'}]},
  dataZoom:[{type:'inside',xAxisIndex:[0,1,2,3],start:0,end:100},{type:'slider',xAxisIndex:[0,1,2,3],height:20,bottom:2,start:0,end:100}],
  tooltip:{trigger:'axis',confine:true,axisPointer:{type:'cross'},formatter:(params:unknown)=>{const entry=(Array.isArray(params)?params[0]:params) as {dataIndex?:number}|undefined;const b=entry?.dataIndex===undefined?undefined:bars[entry.dataIndex];if(!b)return '';const display=(v:string|null,places:number)=>v===null?'样本不足':html(formatDecimal(v,places));return `<strong>${html(b.date)}</strong><br/>开 ${display(b.open,2)}　高 ${display(b.high,2)}<br/>低 ${display(b.low,2)}　收 ${display(b.close,2)}<br/>量 ${display(b.volumeShares,0)}<br/>MA5 ${display(b.ma5,4)}　MA10 ${display(b.ma10,4)}　MA20 ${display(b.ma20,4)}<br/>DIF ${display(b.dif,4)}　DEA ${display(b.dea,4)}　MACD ${display(b.macd,4)}　RSI ${display(b.rsi14,2)}`}}
 } as EChartsOption
}
