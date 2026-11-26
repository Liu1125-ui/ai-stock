import {ApiError} from '../../../api/auth'
import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi,afterEach} from 'vitest'
import {stock,view,now} from './marketRefreshFixtures'
vi.mock('../../../api/adminMarketRefresh',()=>({getRefreshHistory:vi.fn()}))
import * as api from '../../../api/adminMarketRefresh'
import History from './MarketRefreshHistory.vue'
afterEach(()=>vi.resetAllMocks())
it('loads only on expand and preserves old queried data after failed refresh',async()=>{vi.mocked(api.getRefreshHistory).mockResolvedValue({queriedAt:now,instrumentId:stock,items:[view()],hasMore:true,nextCursor:'next'});const w=mount(History,{props:{instrumentId:stock,refreshEpoch:0}});expect(api.getRefreshHistory).not.toHaveBeenCalled();await w.get('[data-test="open-refresh-history"]').trigger('click');await flushPromises();expect(w.text()).toContain('已排队');vi.mocked(api.getRefreshHistory).mockRejectedValueOnce(new Error('暂不可用'));await w.get('[data-test="refresh-refresh-history"]').trigger('click');await flushPromises();expect(w.text()).toContain('保留原查询');expect(w.text()).toContain(now);await w.get('[data-test="more-refresh-history"]').trigger('click');await flushPromises();expect(api.getRefreshHistory).toHaveBeenLastCalledWith(stock,'next',20);w.unmount()})

it('clears history and notifies parent after authoritative role revocation',async()=>{vi.mocked(api.getRefreshHistory).mockResolvedValue({queriedAt:now,instrumentId:stock,items:[view()],hasMore:false,nextCursor:null});const w=mount(History,{props:{instrumentId:stock,refreshEpoch:0}});await w.get('[data-test="open-refresh-history"]').trigger('click');await flushPromises();vi.mocked(api.getRefreshHistory).mockRejectedValue(new ApiError('ACCESS_DENIED','仅管理员',403));await w.get('[data-test="refresh-refresh-history"]').trigger('click');await flushPromises();expect(w.emitted('accessDenied')).toHaveLength(1);expect(w.text()).not.toContain(now);w.unmount()})
