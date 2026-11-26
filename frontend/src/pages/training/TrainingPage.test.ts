
import {flushPromises,mount} from '@vue/test-utils'
import {createPinia} from 'pinia'
import {beforeEach,expect,it,vi} from 'vitest'
import * as api from '../../api/training'
import {useAuthStore} from '../../stores/auth'
import TrainingPage from './TrainingPage.vue'

vi.mock('../../api/training',()=>({
  submitTraining:vi.fn(),trainingHistory:vi.fn(),trainingMetrics:vi.fn(),
  trainingOptions:vi.fn(),trainingStatus:vi.fn(),
}))

const requestId='00000000-0000-0000-0000-000000000001'
const taskId='00000000-0000-0000-0000-000000000002'
const record={requestId,createdAt:'2026-11-11T12:00:00Z'}
const baseTask={taskId,configurationId:'five-day',startedAt:null,finishedAt:null,modelId:null}
const evaluation={mae:0.0249,rmse:0.035,baselineMae:0.0242,direction:0.5,samples:5}

function page(experimentAllowed:boolean){
  const pinia=createPinia()
  useAuthStore(pinia).profile={
    userId:'owner',email:'test@example.com',nickname:'测试',role:'USER',
    status:'ENABLED',emailVerified:true,experimentAllowed,revision:0,
  }
  return mount(TrainingPage,{global:{plugins:[pinia]}})
}

beforeEach(()=>{
  vi.resetAllMocks()
  sessionStorage.clear()
  vi.mocked(api.trainingHistory).mockResolvedValue({items:[record],nextCursor:null})
  vi.mocked(api.trainingOptions).mockResolvedValue(['five-day'])
  vi.mocked(api.trainingStatus).mockResolvedValue([{...baseTask,state:'QUEUED'}])
})

it('keeps history available without a submission grant and displays the saved metrics',async()=>{
  vi.mocked(api.trainingOptions).mockResolvedValue([])
  vi.mocked(api.trainingStatus).mockResolvedValueOnce([{...baseTask,state:'SUCCEEDED',modelId:'candidate-id'}])
  vi.mocked(api.trainingMetrics).mockResolvedValue({
    seed:42,selectedEpoch:1,trainingSamples:15,elapsedSeconds:2.5,
    epochHistory:[{epoch:1,trainingMse:0.003,validation:evaluation}],
    heldout2025:evaluation,heldout2026:evaluation,
  })
  const wrapper=page(false)
  try {
    await flushPromises()
    expect(wrapper.find('form').exists()).toBe(false)
    await wrapper.get('.request-list button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已登记模型版本')
    await wrapper.get('.training-task button').trigger('click')
    await flushPromises()
    expect(api.trainingMetrics).toHaveBeenCalledWith(requestId,taskId)
    expect(wrapper.text()).toContain('2.49%')
    expect(wrapper.text()).toContain('2.42%')
  } finally {wrapper.unmount()}
})

it('reuses the original request number after an interrupted response',async()=>{
  vi.mocked(api.trainingOptions).mockResolvedValue(['five-day','five-day-seed-43'])
  const submit=vi.mocked(api.submitTraining)
  submit.mockRejectedValueOnce(new Error('连接中断')).mockImplementationOnce(async id=>({requestId:id}))
  const wrapper=page(true)
  try {
    await flushPromises()
    await wrapper.get('input[value="five-day-seed-43"]').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('用原编号重试')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(submit).toHaveBeenCalledTimes(2)
    expect(submit.mock.calls[0]?.[1]).toEqual(['five-day','five-day-seed-43'])
    expect(submit.mock.calls[1]).toEqual(submit.mock.calls[0])
    expect(wrapper.text()).toContain('排队中')
  } finally {wrapper.unmount()}
})
