import {it,expect,vi,afterEach} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
const engine=vi.hoisted(()=>({setOption:vi.fn(),resize:vi.fn(),dispose:vi.fn()}))
const init=vi.hoisted(()=>vi.fn(()=>engine))
vi.mock('echarts/core',()=>({init,use:vi.fn()}))
vi.mock('echarts/charts',()=>({BarChart:{},LineChart:{}}))
vi.mock('echarts/components',()=>({GridComponent:{},TooltipComponent:{},LegendComponent:{},AriaComponent:{}}))
vi.mock('echarts/renderers',()=>({CanvasRenderer:{}}))
import StatisticsChart from './StatisticsChart.vue'
let w:ReturnType<typeof mount>|undefined
const observe=vi.fn(),disconnect=vi.fn()
afterEach(()=>{w?.unmount();vi.clearAllMocks();vi.unstubAllGlobals()})
it('preserves unsafe integer text and can initialize a chart after the value becomes safe',async()=>{vi.stubGlobal('ResizeObserver',class {observe=observe;disconnect=disconnect});w=mount(StatisticsChart,{props:{title:'样本',labels:['一天'],series:[{name:'数量',values:['9007199254740992']}]}});await flushPromises();expect(init).not.toHaveBeenCalled();expect(w.text()).toContain('9007199254740992');await w.setProps({series:[{name:'数量',values:['2']}]});await flushPromises();expect(init).toHaveBeenCalledTimes(1);expect(observe).toHaveBeenCalledTimes(1)})
it('disposes the chart and resize observer on leaving',async()=>{vi.stubGlobal('ResizeObserver',class {observe=observe;disconnect=disconnect});w=mount(StatisticsChart,{props:{title:'样本',labels:['一天'],series:[{name:'数量',values:['2']}]}});await flushPromises();expect(engine.setOption).toHaveBeenCalled();w.unmount();w=undefined;expect(engine.dispose).toHaveBeenCalledTimes(1);expect(disconnect).toHaveBeenCalledTimes(1)})
