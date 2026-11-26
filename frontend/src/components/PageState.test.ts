import {mount} from '@vue/test-utils'
import {expect,it} from 'vitest'
import PageState from './PageState.vue'

it('uses a non-urgent announcement and emits only the explicit recovery action',async()=>{
 const w=mount(PageState,{props:{kind:'pending',message:'原请求状态待确认',actionLabel:'查询原请求'}})
 expect(w.get('[role="status"]').text()).toContain('原请求状态待确认')
 expect(w.emitted('action')).toBeUndefined()
 await w.get('button').trigger('click')
 expect(w.emitted('action')).toHaveLength(1)
 w.unmount()
})
it('announces a read failure and disables its action while busy',()=>{
 const w=mount(PageState,{props:{kind:'error',message:'读取失败',actionLabel:'重试',busy:true}})
 expect(w.get('[role="alert"]').text()).toContain('读取失败')
 expect(w.get('button').attributes('disabled')).toBeDefined()
 w.unmount()
})
