import {it,expect,vi,afterEach} from 'vitest'
import {loadReviews,saveReview} from './marketVersionReview'
const stock='SH600030',version='11111111-1111-1111-1111-111111111111',op='22222222-2222-2222-2222-222222222222'
const page=()=>({instrumentId:stock,versionId:version,revision:'0',current:null,history:[],hasMore:false,queriedAt:'2026-10-28T00:00:00Z'})
const reply=(data:unknown,status=200)=>new Response(JSON.stringify({code:status===200?'OK':'MARKET_ADMIN_UNAVAILABLE',data}),{status})
afterEach(()=>vi.unstubAllGlobals())
it('validates empty state and rejects revision numbers or wrong version',async()=>{for(const data of [{...page(),revision:0},{...page(),versionId:op}]){vi.stubGlobal('fetch',vi.fn().mockResolvedValue(reply(data)));await expect(loadReviews(stock,version)).rejects.toMatchObject({code:'INVALID_RESPONSE'})}vi.stubGlobal('fetch',vi.fn().mockResolvedValue(reply(page())));expect((await loadReviews(stock,version)).revision).toBe('0')})
it('does not retry POST on unavailable result',async()=>{const fetch=vi.fn().mockResolvedValue(reply(null,503));vi.stubGlobal('fetch',fetch);await expect(saveReview(stock,version,{operationId:op,expectedRevision:'0',reviewState:'REVIEWED',note:'已核对'})).rejects.toMatchObject({status:503});expect(fetch).toHaveBeenCalledOnce();expect(fetch.mock.calls[0]![1].headers.get('X-Aistock-Admin-Action')).toBe('1')})
