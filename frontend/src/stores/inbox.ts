import {defineStore} from 'pinia'
import {ref,watch} from 'vue'
import {useAuthStore} from './auth'
import {unreadCount} from '../api/messages'
export const useInboxStore=defineStore('inbox',()=>{
 const auth=useAuthStore(),count=ref<number|null>(null)
 let generation=0,sequence=0
 watch(()=>auth.profile?.userId,()=>{generation++;sequence++;count.value=null},{flush:'sync'})
 async function refresh(){const owner=auth.profile?.userId,epoch=generation,turn=++sequence;if(!owner){count.value=null;return}try{const result=await unreadCount();if(epoch===generation&&turn===sequence&&owner===auth.profile?.userId)count.value=result.unreadCount}catch{if(epoch===generation&&turn===sequence)count.value=null}}
 return {count,refresh}
})
