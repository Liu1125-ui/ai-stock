import {inject,onBeforeUnmount,watch,type Ref} from 'vue'
import {matchedRouteKey,onBeforeRouteLeave} from 'vue-router'

export function useUnsavedDraft(dirty:Readonly<Ref<boolean>>,label:string){
 function confirmDiscard(){
  return !dirty.value||window.confirm(label+'尚未保存。要放弃这些修改吗？')
 }
 function beforeUnload(event:BeforeUnloadEvent){
  event.preventDefault()
  event.returnValue=''
 }
 if(inject(matchedRouteKey,null))onBeforeRouteLeave(confirmDiscard)
 watch(dirty,active=>{
  if(active)window.addEventListener('beforeunload',beforeUnload)
  else window.removeEventListener('beforeunload',beforeUnload)
 },{immediate:true,flush:'sync'})
 onBeforeUnmount(()=>window.removeEventListener('beforeunload',beforeUnload))
 return confirmDiscard
}
