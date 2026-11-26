import {expect,it} from 'vitest'
import {mount} from '@vue/test-utils'
import ComparisonResult from './ComparisonResult.vue'
import {result} from './fixtures'
it('shows fixed ranges formula and unavailable volume without inventing zero',()=>{const wrapper=mount(ComparisonResult,{props:{result},global:{stubs:{ComparisonChart:true}}});expect(wrapper.text()).toContain('共同有效区间截至 2026-10-09');expect(wrapper.text()).toContain('后复权');expect(wrapper.text()).toContain('252');expect(wrapper.text()).toContain('成交量缺失');expect(wrapper.text()).toContain('2026-07-10');expect(wrapper.text()).toContain('历史对比');wrapper.unmount()})

it('shows missing volume separately from a suspended price on exact dates',()=>{const data={...result,stocks:result.stocks.map(s=>({...s,dataIssues:[{date:'2026-10-07',field:'volumeShares',state:'MISSING'},{date:'2026-10-08',field:'backwardClose',state:'SUSPENDED'}]}))};const w=mount(ComparisonResult,{props:{result:data},global:{stubs:{ComparisonChart:true}}});expect(w.text()).toContain('2026-10-07 · 成交量：数据缺失');expect(w.text()).toContain('2026-10-08 · 价格：停牌');w.unmount()})

it('labels the requested endpoint separately when comparable data is missing',()=>{
 const wrapper=mount(ComparisonResult,{props:{result:{...result,actualRange:null}},global:{stubs:{ComparisonChart:true}}})
 expect(wrapper.text()).toContain('请求区间截至 '+result.requestedRange.to)
 expect(wrapper.text()).not.toContain('共同有效区间截至')
 expect(wrapper.text()).toContain('不足两个共同有效端点')
 wrapper.unmount()
})
