import type {ModelAction,ModelVersion} from '../../api/models'
export const actionLabels:Record<ModelAction,string>={publish:'发布',reject:'未采用',offline:'下线',recommend:'设为推荐',rollback:'回滚到此版本'}
export function modelActions(role:string,version:ModelVersion,current:string|null):ModelAction[]{
 if(role!=='ADMIN')return []
 if(version.state==='PENDING')return ['publish','reject']
 if(version.state==='OFFLINE')return version.everPublished?['publish','rollback']:['publish']
 if(version.state==='PUBLISHED')return current===version.modelId?['offline']:['recommend','offline',...(version.everPublished?['rollback' as const]:[])]
 return []
}
export const requiresAcknowledgement=(action:ModelAction)=>['publish','recommend','rollback'].includes(action)
export function canSubmitReview(action:ModelAction,reason:string,acknowledged:boolean){return reason.trim().length>=10&&reason.length<=500&&(!requiresAcknowledgement(action)||acknowledged)}
