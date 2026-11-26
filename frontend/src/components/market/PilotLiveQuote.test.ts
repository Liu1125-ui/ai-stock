// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import PilotLiveQuote from './PilotLiveQuote.vue'
import { pilotLiveQuote } from '../../api/market'

vi.mock('../../api/market', () => ({ pilotLiveQuote: vi.fn() }))

afterEach(() => {
  vi.useRealTimers()
  vi.resetAllMocks()
})

it('refreshes automatically, preserves the last price on failure and stops when unmounted', async () => {
  vi.useFakeTimers()
  vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
  const request = vi.mocked(pilotLiveQuote)
  request.mockResolvedValueOnce({
    state: 'LIVE', price: '343.80', currency: 'HKD',
    quoteTime: '2026-11-23T07:36:22Z', receivedAt: '2026-11-23T07:36:25Z'
  }).mockRejectedValueOnce(new Error('offline'))
  const view = mount(PilotLiveQuote)
  try {
    await flushPromises()
    expect(view.text()).toContain('343.80 港元')
    expect(view.text()).toContain('盘中更新')
    await vi.advanceTimersByTimeAsync(5000)
    expect(request).toHaveBeenCalledTimes(2)
    expect(view.text()).toContain('暂时无法读取报价')
    expect(view.text()).toContain('上次收到的报价')
    expect(view.text()).toContain('343.80 港元')
  } finally {
    view.unmount()
    vi.restoreAllMocks()
  }
  await vi.advanceTimersByTimeAsync(5000)
  expect(request).toHaveBeenCalledTimes(2)
})
