<script setup lang="ts">
import {onMounted,onUnmounted,ref,watch} from 'vue'
import * as echarts from 'echarts/core'
import {CandlestickChart,BarChart,LineChart} from 'echarts/charts'
import {GridComponent,TooltipComponent,LegendComponent,DataZoomComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import type {ECharts} from 'echarts/core'
import type {DailyResponse} from '../../api/market'
import {buildMarketChartOption} from './chartOptions'
echarts.use([CandlestickChart,BarChart,LineChart,GridComponent,TooltipComponent,LegendComponent,DataZoomComponent,CanvasRenderer])
const props=defineProps<{response:DailyResponse}>(),root=ref<HTMLElement|null>(null)
let chart:ECharts|undefined,observer:ResizeObserver|undefined
onMounted(()=>{if(!root.value)return;chart=echarts.init(root.value);chart.setOption(buildMarketChartOption(props.response));observer=new ResizeObserver(()=>chart?.resize());observer.observe(root.value)})
watch(()=>props.response,response=>chart?.setOption(buildMarketChartOption(response),true))
onUnmounted(()=>{observer?.disconnect();chart?.dispose();chart=undefined})
</script>
<template><div ref="root" class="market-chart" role="img" :aria-label="`${response.instrumentId} 历史K线、成交量、MACD和RSI图；明细可在下方查看`"/></template>
<style scoped>.market-chart{width:100%;height:650px;min-height:500px;overflow:hidden}@media(max-width:700px){.market-chart{height:540px}}</style>
