import {afterEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import RegisterPage from './RegisterPage.vue'
import * as api from '../../api/auth'
vi.mock('../../api/auth',()=>({requestChallenge:vi.fn(),challengeStatus:vi.fn(),register:vi.fn()}))
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn()})}))
afterEach(()=>{vi.useRealTimers();vi.clearAllMocks()})
it('continues polling after acceptance and shows a later SMTP failure',async()=>{
 vi.useFakeTimers()
 const value={challengeId:'one',expiresAt:new Date(Date.now()+600000).toISOString(),resendAfter:new Date(Date.now()+60000).toISOString(),state:'PENDING' as const}
 vi.mocked(api.requestChallenge).mockResolvedValue(value)
 vi.mocked(api.challengeStatus).mockResolvedValueOnce({...value,state:'ACCEPTED'}).mockResolvedValueOnce({...value,state:'FAILED'})
 const wrapper=mount(RegisterPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:true}}})
 try{
  await wrapper.get('input[type="email"]').setValue('member@example.com')
  await wrapper.findAll('button').find(b=>b.text().includes('发送验证码'))!.trigger('click');await flushPromises()
  await vi.advanceTimersByTimeAsync(2000);await flushPromises()
  await vi.advanceTimersByTimeAsync(2000);await flushPromises()
  expect(wrapper.text()).toContain('本次验证码发送失败或已过期')
  expect(api.challengeStatus).toHaveBeenCalledTimes(2)
 }finally{wrapper.unmount()}
})
