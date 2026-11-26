import {beforeEach,describe,it,expect,vi} from 'vitest'
import {setActivePinia,createPinia} from 'pinia'
import {useWatchlistStore} from './watchlist'
vi.mock('../api/watchlist',()=>({listWatchlist:vi.fn(),addWatchlist:vi.fn(),removeWatchlist:vi.fn()}))
import * as api from '../api/watchlist'
beforeEach(()=>{setActivePinia(createPinia());vi.resetAllMocks()})
describe('watchlist state',()=>{it('keeps loaded refs when removing fails',async()=>{vi.mocked(api.listWatchlist).mockResolvedValue({items:[{instrumentId:'SH600030',createdAt:'2026-09-24T00:00:00Z'}],page:1,size:20,total:1});const s=useWatchlistStore();await s.load(1);vi.mocked(api.removeWatchlist).mockRejectedValue(new Error('down'));await expect(s.remove('SH600030')).rejects.toThrow();expect(s.items).toHaveLength(1)});it('clears on auth expiration',async()=>{vi.mocked(api.listWatchlist).mockResolvedValue({items:[{instrumentId:'SH600030',createdAt:'2026-09-24T00:00:00Z'}],page:1,size:20,total:1});const s=useWatchlistStore();await s.load(1);s.clear();expect(s.items).toEqual([])})})
it('keeps the newest page when an older request finishes later',async()=>{
 let resolveOld!:(page:api.WatchlistPage)=>void
 const old=new Promise<api.WatchlistPage>(resolve=>{resolveOld=resolve})
 vi.mocked(api.listWatchlist).mockReturnValueOnce(old).mockResolvedValueOnce({
  items:[{instrumentId:'SZ000002',createdAt:'2026-09-24T00:00:00Z'}],page:2,size:20,total:21
 })
 const s=useWatchlistStore(),oldCall=s.load(1)
 await s.load(2)
 resolveOld({items:[{instrumentId:'SH600030',createdAt:'2026-09-24T00:00:00Z'}],page:1,size:20,total:21})
 await oldCall
 expect(s.page).toBe(2)
 expect(s.items[0].instrumentId).toBe('SZ000002')
})
