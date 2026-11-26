import {flushPromises, mount} from '@vue/test-utils'
import {expect, it, vi} from 'vitest'
import StockValuationPanel from './StockValuationPanel.vue'
import {stockValuation} from '../../api/market'

vi.mock('../../api/market', () => ({stockValuation: vi.fn()}))
const props = {instrumentId: 'SH600030', versionId: 'verified-version'}

it('shows the matched date, negative PE and unavailable PB', async () => {
  vi.mocked(stockValuation).mockResolvedValue({...props, state: 'AVAILABLE', tradeDate: '2026-11-13',
    peTtm: '-19.25', pbMrq: null, provider: 'baostock'})
  const wrapper = mount(StockValuationPanel, {props})
  await flushPromises()
  expect(wrapper.text()).toContain('2026-11-13')
  expect(wrapper.text()).toContain('-19.25 倍')
  expect(wrapper.text()).toContain('暂无')
  expect(wrapper.text()).toContain('近四个季度盈利为负')
  wrapper.unmount()
})

it('suppresses a result for a different price version', async () => {
  vi.mocked(stockValuation).mockResolvedValue({...props, versionId: 'old', state: 'AVAILABLE',
    tradeDate: '2026-11-13', peTtm: '25.46', pbMrq: '1.32', provider: 'baostock'})
  const wrapper = mount(StockValuationPanel, {props})
  await flushPromises()
  expect(wrapper.text()).toContain('估值暂不可用')
  expect(wrapper.text()).not.toContain('25.46')
  wrapper.unmount()
})
