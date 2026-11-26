/** Recover a stale deployment route without retrying login or any business command. */
export function isPageModuleError(error:unknown):boolean {
 const message=error instanceof Error?error.message:''
 return /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Unable to preload CSS for/i.test(message)
}
export function createPageRecovery(storage:Pick<Storage,'getItem'|'setItem'>,replace:(path:string)=>void,now:()=>number=Date.now){
 const key='aistock:page-recovery:v1'
 return (error:unknown,path:string):'reloading'|'manual'|'unrelated'=>{
  if(!isPageModuleError(error))return 'unrelated'
  if(!path.startsWith('/')||path.startsWith('//')||/[\\\r\n]/.test(path))return 'manual'
  try{
   const at=now(),saved=storage.getItem(key)
   if(saved!==null&&(!Number.isFinite(Number(saved))||at-Number(saved)<60000))return 'manual'
   storage.setItem(key,String(at))
   replace(path)
   return 'reloading'
  }catch{return 'manual'}
 }
}
