import {ref,type InjectionKey,type Ref} from 'vue'

export function taskIdentity(profile:{userId:string;role:string}|null|undefined){
  return profile?profile.userId+':'+profile.role:''
}
export interface AdminTaskAccess {
  denied:Ref<boolean>
  reset:()=>void
  reject:(error:unknown,requestIdentity:string)=>boolean
}
export const taskAccessKey:InjectionKey<AdminTaskAccess>=Symbol('admin-task-access')

/** One permission boundary for list, detail and attempts in a mounted workspace. */
export function createAdminTaskAccess(identity:()=>string,refresh:()=>unknown):AdminTaskAccess {
  const denied=ref(false)
  return {
    denied,
    reset(){denied.value=false},
    reject(error,requestIdentity){
      if((error as {status?:number}|null)?.status!==403||requestIdentity!==identity())return false
      if(!denied.value){
        // Synchronous watchers clear every view and advance each request epoch first.
        denied.value=true
        // Failure to refresh permissions must not unlock the workspace again.
        void Promise.resolve().then(refresh).catch(()=>{})
      }
      return true
    },
  }
}
