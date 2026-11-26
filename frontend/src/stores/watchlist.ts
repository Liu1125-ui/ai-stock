import {defineStore} from 'pinia'
import {ref} from 'vue'
import * as api from '../api/watchlist'
export const useWatchlistStore=defineStore('watchlist',()=>{
 const items=ref<api.WatchlistItem[]>([]),total=ref(0),page=ref(1),loading=ref(false),error=ref('')
 let generation=0
 async function load(nextPage=1){
  const turn=++generation
  loading.value=true;error.value=''
  try{
   const result=await api.listWatchlist(nextPage)
   if(turn!==generation)return
   items.value=result.items;total.value=result.total;page.value=result.page
  }catch(e){
   if(turn!==generation)return
   error.value=(e as Error).message;throw e
  }finally{if(turn===generation)loading.value=false}
 }
 async function add(id:string){
  const created=await api.addWatchlist(id)
  generation++;loading.value=false
  if(!items.value.some(x=>x.instrumentId===id)){items.value=[created,...items.value];total.value++}
  return created
 }
 async function remove(id:string){
  await api.removeWatchlist(id)
  generation++;loading.value=false
  const before=items.value.length
  items.value=items.value.filter(x=>x.instrumentId!==id)
  if(items.value.length!==before)total.value=Math.max(0,total.value-1)
 }
 function clear(){generation++;items.value=[];total.value=0;page.value=1;loading.value=false;error.value=''}
 return {items,total,page,loading,error,load,add,remove,clear}
})
