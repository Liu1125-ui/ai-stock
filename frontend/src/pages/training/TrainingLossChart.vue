<script setup lang="ts">
import {onMounted,onUnmounted,ref,watch} from 'vue'
import {init,use,type ECharts} from 'echarts/core'
import {LineChart} from 'echarts/charts'
import {GridComponent,TooltipComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import type {TrainingMetrics} from '../../api/training'

use([LineChart,GridComponent,TooltipComponent,CanvasRenderer])
const props=defineProps<{history:TrainingMetrics['epochHistory']}>()
const root=ref<HTMLElement|null>(null)
let chart:ECharts|undefined
let observer:ResizeObserver|undefined
function update(){
  chart?.setOption({
    animation:false,
    grid:{left:72,right:18,top:20,bottom:34},
    tooltip:{trigger:'axis'},
    xAxis:{type:'category',name:'轮次',nameLocation:'middle',nameGap:24,data:props.history.map(row=>row.epoch)},
    yAxis:{type:'value',scale:true},
    series:[{name:'训练 MSE',type:'line',data:props.history.map(row=>row.trainingMse),symbolSize:7}],
  })
}
onMounted(()=>{
  if(!root.value)return
  chart=init(root.value)
  update()
  observer=new ResizeObserver(()=>chart?.resize())
  observer.observe(root.value)
})
watch(()=>props.history,update)
onUnmounted(()=>{observer?.disconnect();chart?.dispose()})
</script>

<template><div ref="root" class="loss-chart" role="img" aria-label="按轮次绘制的训练均方误差，下方提供数值明细" /></template>
<style scoped>.loss-chart{height:240px;width:100%}</style>
