import {beforeEach,afterEach,it,expect,vi} from 'vitest'
import {ApiError,invalidateAuthenticatedRequests} from './auth'
import {submitRefresh,getRefreshOptions,getRefreshOperation} from './adminMarketRefresh'
import {stock,command,receipt,options,op,view} from '../pages/admin/data/marketRefreshFixtures'
const reply=(code:string,data:unknown,status=200)=>new Response(JSON.stringify({code,message:'说明',data}),{status})
beforeEach(()=>invalidateAuthenticatedRequests());afterEach(()=>vi.unstubAllGlobals())
it('posts once with action header and retains verified 409 data',async()=>{const saved={...receipt(),outcome:'REJECTED',taskId:null,resultCode:'REFRESH_COOLDOWN'};const fetch=vi.fn().mockResolvedValue(reply('REFRESH_COOLDOWN',saved,409));vi.stubGlobal('fetch',fetch);await expect(submitRefresh(stock,command)).rejects.toMatchObject({status:409,data:saved});expect(fetch).toHaveBeenCalledTimes(1);expect(new Headers(fetch.mock.calls[0]![1].headers).get('X-Aistock-Admin-Action')).toBe('1')})
it('rejects mismatched stock, numeric revision and unsupported source',async()=>{for(const value of [{...options(),instrumentId:'SH600276'},{...options(),headRevision:7},{...options(),provider:'tushare'}]){vi.stubGlobal('fetch',vi.fn().mockResolvedValue(reply('OK',value)));await expect(getRefreshOptions(stock)).rejects.toBeInstanceOf(ApiError)}})
it('never retries a failed write and validates recovered operation',async()=>{const fetch=vi.fn().mockRejectedValue(new TypeError('network'));vi.stubGlobal('fetch',fetch);await expect(submitRefresh(stock,command)).rejects.toMatchObject({code:'NETWORK_UNAVAILABLE'});expect(fetch).toHaveBeenCalledOnce();vi.stubGlobal('fetch',vi.fn().mockResolvedValue(reply('OK',view())));expect((await getRefreshOperation(op)).receipt.operationId).toBe(op)})
