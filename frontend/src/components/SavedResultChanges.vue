<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {ChangeField,ChangeSnapshot} from './savedResultChanges'

const props=defineProps<{owner?:string;snapshot:ChangeSnapshot|null}>()
const pinned=ref<ChangeSnapshot|null>(null)
watch(()=>props.owner,()=>{pinned.value=null},{flush:'sync'})

const pair=computed(()=>{
 const baseline=pinned.value,current=props.snapshot
 if(!baseline||!current||baseline.id===current.id)return null
 return Date.parse(baseline.createdAt)>Date.parse(current.createdAt)?[current,baseline] as const:[baseline,current] as const
})
const comparable=computed(()=>!!pair.value?.[0].metricBasis&&pair.value[0].metricBasis===pair.value[1].metricBasis)
const order:ChangeField['group'][]=['记录与口径','持仓调整','行情依据','结果数值']
const groups=computed(()=>{
 if(!pair.value)return []
 const [older,newer]=pair.value
 const before=new Map(older.fields.map(field=>[field.key,field]))
 const after=new Map(newer.fields.map(field=>[field.key,field]))
 return order.map(name=>{
  const rows=[...new Set([...before.keys(),...after.keys()])].flatMap(key=>{
   const previous=before.get(key),next=after.get(key)
   const field=next??previous!
   if(field.group!==name||previous?.value===next?.value||name==='结果数值'&&(!comparable.value||previous?.value==null||next?.value==null))return []
   return [{key,label:field.label,previous:previous?.value??'暂无',next:next?.value??'暂无'}]
  })
  return {name,rows}
 }).filter(group=>group.rows.length)
})
</script>

<template>
 <section v-if="owner&&(snapshot||pinned)" class="result-changes" aria-label="结果变化摘要">
  <div class="controls">
   <h3>结果变化摘要</h3>
   <button v-if="snapshot&&snapshot.id!==pinned?.id" data-pin-result type="button" @click="pinned=snapshot">设为对照记录</button>
   <button v-if="pinned" type="button" @click="pinned=null">清除对照</button>
  </div>
  <p v-if="!pinned">先设定一份对照记录，再打开另一份已保存结果。</p>
  <p v-else-if="!pair" role="status">已设定对照记录，打开另一份已保存结果即可查看变化。</p>
  <template v-else>
   <dl class="record-times">
    <div v-for="(entry,index) in pair" :key="entry.id">
     <dt>{{index===0?'较早记录':'较晚记录'}}</dt><dd><time :datetime="entry.createdAt">{{entry.createdAt}}</time> · {{entry.id}}</dd>
    </div>
   </dl>
   <p v-if="!comparable" role="status">两份记录的计算口径或覆盖范围不同，仅展示记录与依据变化。</p>
   <p v-if="groups.some(group=>group.name==='持仓调整')&&groups.some(group=>group.name==='行情依据')">这两份估值的持仓和行情依据均有变化，盈亏差异可能同时受到持仓调整和报价影响。</p><p v-if="!groups.length">所列字段没有显示变化。</p>
   <div v-for="group in groups" :key="group.name">
    <h4>{{group.name}}</h4>
    <ul>
     <li v-for="row in group.rows" :key="row.key">
      <strong>{{row.label}}</strong><span>较早 {{row.previous}}</span><span>较晚 {{row.next}}</span>
     </li>
    </ul>
   </div>
  </template>
 </section>
</template>

<style scoped>
.result-changes{margin:20px 0;padding:16px;border:1px solid var(--border);border-radius:10px;overflow-wrap:anywhere}
.controls{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.controls h3{margin:0 auto 0 0;font-size:18px}
button{font:inherit;min-height:44px;padding:6px 12px;border:1px solid var(--border);border-radius:8px;color:var(--accent);background:var(--surface);cursor:pointer}
ul{margin:0;padding:0;list-style:none}
li{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:4px 12px;padding:6px 0;border-bottom:1px solid var(--border)}
.record-times{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px 12px}
dd{margin:0;color:var(--muted)}
@media(max-width:620px){li,.record-times{grid-template-columns:1fr}}
</style>
