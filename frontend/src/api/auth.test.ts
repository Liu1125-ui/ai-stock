import {beforeEach,afterEach,expect,it,vi} from 'vitest'
import {login,logout,resetCsrf,requestChallenge} from './auth'
const response=(data:unknown,status=200)=>new Response(JSON.stringify({code:'OK',data}),{status,headers:{'Content-Type':'application/json'}})
beforeEach(()=>resetCsrf());afterEach(()=>vi.unstubAllGlobals())
it('fetches csrf before writing and refreshes it after authentication changes',async()=>{
  const fetcher=vi.fn().mockResolvedValueOnce(response({token:'anonymous'})).mockResolvedValueOnce(response({userId:'id'}))
    .mockResolvedValueOnce(response({token:'authenticated'})).mockResolvedValueOnce(response(null)).mockResolvedValueOnce(response({token:'after-logout'}))
  vi.stubGlobal('fetch',fetcher)
  await login('member@example.com','Long-test-password-1');await logout()
  expect(fetcher.mock.calls.map(c=>c[0])).toEqual(['/api/v1/auth/csrf','/api/v1/auth/login','/api/v1/auth/csrf','/api/v1/auth/logout','/api/v1/auth/csrf'])
  expect(new Headers(fetcher.mock.calls[1]![1].headers).get('X-XSRF-TOKEN')).toBe('anonymous')
  expect(new Headers(fetcher.mock.calls[3]![1].headers).get('X-XSRF-TOKEN')).toBe('authenticated')
  expect(fetcher.mock.calls.every(c=>c[1].credentials==='include')).toBe(true)
  expect(localStorage.length).toBe(0)
})
it('never submits a write when csrf acquisition fails',async()=>{
  const fetcher=vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'USER_SERVICE_UNAVAILABLE',message:'暂不可用'}),{status:503}))
  vi.stubGlobal('fetch',fetcher)
  await expect(requestChallenge('member@example.com')).rejects.toMatchObject({code:'USER_SERVICE_UNAVAILABLE'})
  expect(fetcher).toHaveBeenCalledTimes(1)
})
it('refreshes a stale csrf token and retries the same registration request once',async()=>{
 const fetcher=vi.fn().mockResolvedValueOnce(response({token:'old'}))
  .mockResolvedValueOnce(new Response(JSON.stringify({code:'ACCESS_DENIED',message:'拒绝'}),{status:403}))
  .mockResolvedValueOnce(response({token:'new'})).mockResolvedValueOnce(response({challengeId:'one'}))
 vi.stubGlobal('fetch',fetcher)
 await requestChallenge('member@example.com')
 expect(fetcher.mock.calls.map(c=>c[0])).toEqual(['/api/v1/auth/csrf','/api/v1/auth/registration-challenges','/api/v1/auth/csrf','/api/v1/auth/registration-challenges'])
 expect(fetcher.mock.calls[1]![1].body).toBe(fetcher.mock.calls[3]![1].body)
 expect(new Headers(fetcher.mock.calls[3]![1].headers).get('X-XSRF-TOKEN')).toBe('new')
})
it('signals expired protected identity but does not redirect for login failure',async()=>{
 const onExpired=vi.fn();window.addEventListener('aistock:auth-required',onExpired)
 try{
  const fetcher=vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'AUTH_REQUIRED',message:'请登录'}),{status:401}))
  vi.stubGlobal('fetch',fetcher)
  const {me}=await import('./auth');await expect(me()).rejects.toMatchObject({code:'AUTH_REQUIRED'})
  expect(onExpired).toHaveBeenCalledTimes(1)
 }finally{window.removeEventListener('aistock:auth-required',onExpired)}
})
it('does not replay a delayed denial under a newly logged in account',async()=>{
 const {request,refreshCsrf}=await import('./auth');let release!:(r:Response)=>void;let attempts=0
 const fetcher=vi.fn().mockImplementation((url:string)=>{if(url==='/api/v1/auth/csrf')return Promise.resolve(response({token:'fixture-csrf'}));if(url==='/api/v1/predictions'){attempts++;return attempts===1?new Promise<Response>(resolve=>{release=resolve}):Promise.resolve(response({predictionId:'wrong-account'}))}return Promise.resolve(response(null))})
 vi.stubGlobal('fetch',fetcher);await refreshCsrf();const old=request('/predictions','POST',{operationId:'original-request'}).then(()=>({code:'unexpected-success'}),error=>error)
 await logout();await login('second@example.com','fixture-password');release(new Response(JSON.stringify({code:'ACCESS_DENIED',message:'拒绝',data:null}),{status:403}));expect((await old).code).toBe('AUTH_CONTEXT_CHANGED');expect(attempts).toBe(1)
})
it('does not submit an old write after account changes while waiting for csrf',async()=>{
 const {createPinia,setActivePinia}=await import('pinia');const {useAuthStore}=await import('../stores/auth');const {request}=await import('./auth');setActivePinia(createPinia());const store=useAuthStore();store.profile={userId:'first',role:'USER'} as never;let release!:(r:Response)=>void;let writes=0
 vi.stubGlobal('fetch',vi.fn().mockImplementation((url:string)=>url==='/api/v1/auth/csrf'?new Promise<Response>(resolve=>{release=resolve}):(writes++,Promise.resolve(response(null)))))
 const old=request('/predictions','POST',{operationId:'original-request'}).then(()=>({code:'unexpected-success'}),error=>error);store.profile={userId:'second',role:'USER'} as never;release(response({token:'late-csrf'}));expect((await old).code).toBe('AUTH_CONTEXT_CHANGED');expect(writes).toBe(0)
})

it('question endpoint 401 signals identity expiration without replay',async()=>{const onExpired=vi.fn();window.addEventListener('aistock:auth-required',onExpired);try{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'AUTH_REQUIRED',message:'请登录'}),{status:401})));const {request}=await import('./auth');await expect(request('/stock-questions?analysisId=test')).rejects.toMatchObject({code:'AUTH_REQUIRED'});expect(onExpired).toHaveBeenCalledTimes(1);expect(fetch).toHaveBeenCalledTimes(1)}finally{window.removeEventListener('aistock:auth-required',onExpired)}})

it('alert and preference endpoints signal expired identity',async()=>{const listener=vi.fn();window.addEventListener('aistock:auth-required',listener);try{vi.stubGlobal('fetch',vi.fn().mockImplementation(()=>Promise.resolve(new Response(JSON.stringify({code:'AUTH_REQUIRED'}),{status:401}))));const {request}=await import('./auth');for(const path of ['/alerts','/notification-preferences','/messages'])await expect(request(path)).rejects.toMatchObject({code:'AUTH_REQUIRED'});expect(listener).toHaveBeenCalledTimes(3)}finally{window.removeEventListener('aistock:auth-required',listener)}})

it('comparison endpoints signal expired identity',async()=>{const listener=vi.fn();window.addEventListener('aistock:auth-required',listener);try{vi.stubGlobal('fetch',vi.fn().mockImplementation(()=>Promise.resolve(new Response(JSON.stringify({code:'AUTH_REQUIRED'}),{status:401}))));const {request}=await import('./auth');await expect(request('/comparisons')).rejects.toMatchObject({code:'AUTH_REQUIRED'});expect(listener).toHaveBeenCalledTimes(1)}finally{window.removeEventListener('aistock:auth-required',listener)}})

it('comparison AI endpoint signals expired identity',async()=>{const listener=vi.fn();window.addEventListener('aistock:auth-required',listener);try{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'AUTH_REQUIRED'}),{status:401})));const {request}=await import('./auth');await expect(request('/comparison-explanations?comparisonId=x')).rejects.toMatchObject({code:'AUTH_REQUIRED'});expect(listener).toHaveBeenCalledTimes(1)}finally{window.removeEventListener('aistock:auth-required',listener)}})

it('portfolio analysis expiration signals login without retrying the request',async()=>{const listener=vi.fn();window.addEventListener('aistock:auth-required',listener);try{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'AUTH_REQUIRED',message:'请登录',data:null}),{status:401})));const {request}=await import('./auth');await expect(request('/portfolio-analyses')).rejects.toMatchObject({code:'AUTH_REQUIRED'});expect(listener).toHaveBeenCalledTimes(1);expect(fetch).toHaveBeenCalledTimes(1)}finally{window.removeEventListener('aistock:auth-required',listener)}})

it('portfolio question expiration signals login without repeating the request',async()=>{const listener=vi.fn();window.addEventListener('aistock:auth-required',listener);try{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'AUTH_REQUIRED',message:'请登录',data:null}),{status:401})));const {request}=await import('./auth');await expect(request('/portfolio-questions?analysisId=x')).rejects.toMatchObject({code:'AUTH_REQUIRED'});expect(listener).toHaveBeenCalledTimes(1);expect(fetch).toHaveBeenCalledTimes(1)}finally{window.removeEventListener('aistock:auth-required',listener)}})
