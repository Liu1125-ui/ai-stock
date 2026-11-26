import {afterEach,beforeEach,expect,it,vi} from 'vitest'
import {invalidateAuthenticatedRequests,ApiError} from './auth'
import {submitTaskAction,getTaskActionReceipt,getTaskActionOptions} from './adminTaskActions'
const taskId='00000000-0000-0000-0000-000000000001'
const op='00000000-0000-0000-0000-000000000002'
const command={operationId:op,expectedRevision:'0',reasonCode:'CANCEL_NOT_NEEDED'}
const receipt={operationId:op,taskId,action:'CANCEL',outcome:'APPLIED',resultCode:'TASK_CANCEL_APPLIED',message:'已取消',beforeState:'QUEUED',afterState:'CANCELLED',beforeRevision:'0',afterRevision:'1',requestedAt:'2026-10-20T12:00:00Z',finishedAt:'2026-10-20T12:00:00Z'}
const response=(code:string,data:unknown,status=200)=>new Response(JSON.stringify({code,message:'说明',data}),{status,headers:{'Content-Type':'application/json'}})
beforeEach(()=>invalidateAuthenticatedRequests());afterEach(()=>vi.unstubAllGlobals())
it('sends one POST on 403 with no csrf acquisition or automatic retry',async()=>{
 const f=vi.fn().mockResolvedValue(response('ACCESS_DENIED',null,403));vi.stubGlobal('fetch',f)
 await expect(submitTaskAction(taskId,'CANCEL',command)).rejects.toMatchObject({status:403})
 expect(f).toHaveBeenCalledTimes(1);expect(f.mock.calls[0]![0]).toBe('/api/v1/admin/tasks/'+taskId+'/cancel')
 expect(new Headers(f.mock.calls[0]![1].headers).get('X-Aistock-Admin-Action')).toBe('1')
})
it('retains a validated 409 receipt without automatically resubmitting',async()=>{
 const data={...receipt,outcome:'REJECTED',resultCode:'TASK_REVISION_CONFLICT',afterState:'QUEUED',afterRevision:'0'}
 const f=vi.fn().mockResolvedValue(response('TASK_REVISION_CONFLICT',data,409));vi.stubGlobal('fetch',f)
 await expect(submitTaskAction(taskId,'CANCEL',command)).rejects.toMatchObject({status:409,data})
 expect(f).toHaveBeenCalledTimes(1)
})
it('rejects numeric, mismatched and malformed receipt fields',async()=>{
 for(const data of [{...receipt,beforeRevision:0},{...receipt,taskId:op},{...receipt,operationId:taskId},{...receipt,action:'RETRY'}]){
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response('OK',data)))
  await expect(submitTaskAction(taskId,'CANCEL',command)).rejects.toBeInstanceOf(ApiError)
 }
})
it('binds delayed responses to the authentication generation',async()=>{
 let resolve!:(value:Response)=>void
 vi.stubGlobal('fetch',vi.fn().mockReturnValue(new Promise<Response>(r=>{resolve=r})))
 const pending=submitTaskAction(taskId,'CANCEL',command);invalidateAuthenticatedRequests();resolve(response('OK',receipt))
 await expect(pending).rejects.toMatchObject({code:'AUTH_CONTEXT_CHANGED'})
})
it('keeps a missing receipt distinguishable and signals expired sessions',async()=>{
 const event=vi.fn();window.addEventListener('aistock:auth-required',event)
 try{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response('OPERATION_NOT_FOUND',null,404)));await expect(getTaskActionReceipt(taskId,op)).rejects.toMatchObject({status:404})
 vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response('AUTH_REQUIRED',null,401)));await expect(getTaskActionOptions(taskId)).rejects.toMatchObject({status:401});expect(event).toHaveBeenCalledTimes(1)}finally{window.removeEventListener('aistock:auth-required',event)}
})
it('does not retry a network timeout',async()=>{
 const f=vi.fn().mockRejectedValue(new TypeError('network'));vi.stubGlobal('fetch',f)
 await expect(submitTaskAction(taskId,'CANCEL',command)).rejects.toMatchObject({code:'NETWORK_UNAVAILABLE'});expect(f).toHaveBeenCalledTimes(1)
})
