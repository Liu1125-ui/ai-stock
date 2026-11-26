import type {Router} from 'vue-router'
import {useAuthStore} from '../stores/auth'
export function installAccountGuards(router:Router){router.beforeEach(async to=>{
 if(!to.meta.requiresAuth)return true
 const auth=useAuthStore()
 try{await auth.refresh()}catch{return {path:'/login',query:{unavailable:'1',returnTo:to.fullPath}}}
 if(!auth.profile)return {path:'/login',query:{returnTo:to.fullPath}}
 if(to.meta.admin&&!auth.isAdmin)return {path:'/profile'}
 return true
})}
