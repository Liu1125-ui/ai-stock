import {flushPromises, mount} from '@vue/test-utils'
import {expect, it, vi} from 'vitest'
import MarketOverview from './MarketOverview.vue'
import {marketOverview} from '../../api/market'

vi.mock('../../api/market', () => ({marketOverview: vi.fn()}))

it('shows saved data and the missing trading date when updates are late', async () => {
  vi.mocked(marketOverview).mockResolvedValue({
    freshness: 'STALE', expectedTradeDate: '2026-11-16',
    snapshot: {version: 1, fetchedAt: '2026-11-16T06:59:00Z', provider: 'baostock', indices: [{
      indexId: 'SSE_COMPOSITE', sourceCode: 'sh.000001', date: '2026-11-13',
      previousDate: '2026-11-12', close: '3634.353', previousClose: '3655.8295', changePercent: '-0.5875',
    }]},
  })
  const wrapper = mount(MarketOverview)
  await flushPromises()
  expect(wrapper.text()).toContain('上证指数')
  expect(wrapper.text()).toContain('下跌 0.5875%')
  expect(wrapper.text()).toContain('2026-11-13')
  expect(wrapper.text()).toContain('待更新至 2026-11-16')
  expect(wrapper.text()).toContain('Baostock')
  wrapper.unmount()
})

it('allows a retry after the index request fails and displays an explicit empty state', async () => {
  vi.mocked(marketOverview).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({
    freshness: 'NO_DATA', expectedTradeDate: null, snapshot: null,
  })
  const wrapper = mount(MarketOverview)
  await flushPromises()
  expect(wrapper.text()).toContain('指数行情暂不可用')
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('暂无已发布指数行情')
  expect(wrapper.find('article').exists()).toBe(false)
  wrapper.unmount()
})
