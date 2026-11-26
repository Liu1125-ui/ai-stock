<script setup lang="ts">
import {ref,computed,onMounted,onUnmounted,watch} from 'vue'
import {init,use,type EChartsType} from 'echarts/core'
import {BarChart,LineChart} from 'echarts/charts'
import {GridComponent,TooltipComponent,LegendComponent,AriaComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import {chartCount} from './statisticsPresentation'
use([BarChart,LineChart,GridComponent,TooltipComponent,LegendComponent,AriaComponent,CanvasRenderer])
const props=defineProps<{title:string;labels:string[];series:{name:string;values:string[]}[];type?:'line'|'bar'}>()
const host=ref<HTMLElement|null>(null),failed=ref(false)
const chartSafe=computed(()=>props.labels.length>0&&props.series.length>0&&props.series.every(s=>s.values.length===props.labels.length&&s.values.every(v=>chartCount(v)!==null)))
let chart:EChartsType|undefined,observer:ResizeObserver|undefined,observedHost:HTMLElement|undefined
function resize(){chart?.resize()}
function observeHost(){if(observer&&host.value&&observedHost!==host.value){if(observedHost)observer.disconnect();observer.observe(host.value);observedHost=host.value}}
function render(){if(!host.value||!chartSafe.value){chart?.dispose();chart=undefined;if(observedHost){observer?.disconnect();observedHost=undefined}return}try{failed.value=false;chart??=init(host.value);chart.setOption({aria:{enabled:true,description:props.title},animation:false,color:['#315ed0','#24756c','#8b5a20','#7756a2'],tooltip:{trigger:'axis',renderMode:'richText'},legend:{top:0,type:'scroll'},grid:{left:44,right:18,top:44,bottom:66},xAxis:{type:'category',data:props.labels,axisLabel:{interval:props.labels.length>12?'auto':0,formatter:(v:string)=>v.length>12?v.slice(0,11)+'…':v}},yAxis:{type:'value',minInterval:1},series:props.series.map(s=>({name:s.name,type:props.type??'line',data:s.values.map(v=>chartCount(v)),symbol:'circle',symbolSize:6,lineStyle:{width:2},barMaxWidth:28}))},true);observeHost()}catch{chart?.dispose();chart=undefined;failed.value=true}}
watch(()=>[props.labels,props.series,props.type],render,{deep:true,flush:'post'})
onMounted(()=>{render();if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(resize);observeHost()}else window.addEventListener('resize',resize)})
onUnmounted(()=>{observer?.disconnect();window.removeEventListener('resize',resize);chart?.dispose()})
</script>
<template><figure class="stats-figure"><figcaption>{{title}}</figcaption><div v-if="chartSafe" ref="host" class="stats-chart" role="img" :aria-label="title+'，精确数据见下方文字表格'"/><p v-if="!chartSafe||failed" class="small">暂无可绘制图形，或数值超出图表安全范围，请查看精确数据。</p><details :open="!chartSafe||failed"><summary>查看图表数据</summary><div class="stats-scroll" tabindex="0" role="region" :aria-label="title+'数据，可横向滚动'"><table><thead><tr><th scope="col">日期 / 项目</th><th v-for="s in series" :key="s.name" scope="col">{{s.name}}</th></tr></thead><tbody><tr v-for="(label,i) in labels" :key="label"><th scope="row">{{label}}</th><td v-for="s in series" :key="s.name">{{s.values[i]??'暂无样本'}}</td></tr></tbody></table></div></details></figure></template>
