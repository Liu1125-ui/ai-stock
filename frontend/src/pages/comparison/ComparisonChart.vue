<script setup lang="ts">
import {onMounted,onUnmounted,ref,watch} from 'vue'
import * as echarts from 'echarts/core'
import {LineChart} from 'echarts/charts'
import {GridComponent,TooltipComponent,LegendComponent,DataZoomComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import type {StockResult} from '../../api/comparisons'
import {chartOptions} from './chartOptions'
echarts.use([LineChart,GridComponent,TooltipComponent,LegendComponent,DataZoomComponent,CanvasRenderer])
const props=defineProps<{stocks:StockResult[]}>(),root=ref<HTMLElement|null>(null)
let chart:echarts.ECharts|undefined,observer:ResizeObserver|undefined
onMounted(()=>{if(!root.value)return;chart=echarts.init(root.value);chart.setOption(chartOptions(props.stocks));if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(()=>chart?.resize());observer.observe(root.value)}})
watch(()=>props.stocks,stocks=>chart?.setOption(chartOptions(stocks),true))
onUnmounted(()=>{observer?.disconnect();chart?.dispose()})
</script>
<template><div ref="root" class="comparison-chart" role="img" aria-label="各股共同起点为100的归一化走势，缺失日期断线，明细见下方"/></template>
<style scoped>.comparison-chart{width:100%;height:360px;min-width:0}@media(max-width:600px){.comparison-chart{height:300px}}</style>
