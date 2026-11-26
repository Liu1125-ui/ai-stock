<script setup lang="ts">
import {ref,watch} from 'vue'
import {savedResultExport,toCsv,type ExportRecord} from './savedResultExport'

const props=defineProps<{record:ExportRecord;privateRecord?:boolean}>()
const error=ref('')
watch(()=>props.record,()=>{error.value=''})

function download(){
 error.value=''
 try{
  const {id,label,rows}=savedResultExport(props.record)
  if(!rows)return
  const file=new Blob([toCsv(rows)],{type:'text/csv;charset=utf-8'})
  const address=URL.createObjectURL(file)
  const link=document.createElement('a')
  try{
   link.href=address
   link.download=label+'-'+id.replace(/[^a-zA-Z0-9_-]/g,'_')+'.csv'
   document.body.append(link)
   link.click()
  }finally{
   link.remove()
   window.setTimeout(()=>URL.revokeObjectURL(address),1000)
  }
 }catch{
  error.value='文件准备失败，请重试。'
 }
}
</script>

<template>
 <div class="export-actions">
  <button data-test="export-saved-result" type="button" @click="download">下载本份记录（CSV）</button>
  <p v-if="privateRecord">文件含当次持仓和成本，请妥善保存。</p>
  <p v-if="error" role="alert">{{error}}</p>
 </div>
</template>

<style scoped>
.export-actions{margin:12px 0;overflow-wrap:anywhere}
.export-actions p{margin:6px 0 0;color:var(--muted);font-size:14px}
button{min-height:44px;padding:8px 12px;font:inherit;border:1px solid var(--border);border-radius:8px;background:var(--surface);color:var(--accent);cursor:pointer}
button:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
</style>
