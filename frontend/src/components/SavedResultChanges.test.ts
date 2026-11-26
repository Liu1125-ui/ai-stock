import {mount} from '@vue/test-utils'
import {expect,it} from 'vitest'
import SavedResultChanges from './SavedResultChanges.vue'
import type {ChangeSnapshot} from './savedResultChanges'

const start:ChangeSnapshot={
 id:'old',createdAt:'2026-10-01',metricBasis:'common',fields:[
  {key:'quantity',group:'持仓调整',label:'股数',value:'100'},
  {key:'price',group:'行情依据',label:'收盘价',value:'20'},
  {key:'profit',group:'结果数值',label:'盈亏',value:'200'},
 ],
}

it('groups distinct sources of change and suppresses numeric results for mismatched bases',async()=>{
 const next={...start,id:'new',createdAt:'2026-10-02',metricBasis:'other',fields:[
  {...start.fields[0]!,value:'120'},
  {...start.fields[1]!,value:'25'},
  {...start.fields[2]!,value:'300'},
 ]}
 const wrapper=mount(SavedResultChanges,{props:{owner:'first',snapshot:start}})
 await wrapper.get('[data-pin-result]').trigger('click')
 await wrapper.setProps({snapshot:next})
 expect(wrapper.text()).toContain('持仓调整')
 expect(wrapper.text()).toContain('行情依据')
 expect(wrapper.text()).toContain('计算口径或覆盖范围不同')
 expect(wrapper.text()).not.toContain('300')
 await wrapper.setProps({snapshot:{...next,metricBasis:'common'}})
 expect(wrapper.text()).toContain('盈亏')
 expect(wrapper.text()).toContain('300')
 await wrapper.setProps({owner:'second'})
 expect(wrapper.text()).not.toContain('持仓调整')
 wrapper.unmount()
})
