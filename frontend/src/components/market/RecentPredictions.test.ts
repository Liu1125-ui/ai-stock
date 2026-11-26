import {flushPromises, mount} from '@vue/test-utils'
import {createPinia} from 'pinia'
import {expect, it, vi} from 'vitest'
import {predictionHistory, type PredictionHistory, type PredictionRecord} from '../../api/predictions'
import {useAuthStore} from '../../stores/auth'
import RecentPredictions from './RecentPredictions.vue'

vi.mock('../../api/predictions', () => ({predictionHistory: vi.fn()}))
const history = (instrumentId: string, predictionId: string): PredictionHistory => ({
  items: [{instrumentId,predictionId,horizon:5,createdAt:'2026-11-13T09:00:00Z',state:'RUNNING'} as PredictionRecord],
  page:1,size:20,total:1,
})

it('links to the original prediction and discards a prior account response',async()=>{
  const pinia=createPinia(),auth=useAuthStore(pinia)
  let resolveOld!:(value:PredictionHistory)=>void
  vi.mocked(predictionHistory).mockImplementationOnce(()=>new Promise(resolve=>{resolveOld=resolve}))
    .mockResolvedValueOnce(history('SH600276','current-record'))
  auth.profile={userId:'first',nickname:'First'} as typeof auth.profile
  const wrapper=mount(RecentPredictions,{global:{plugins:[pinia],stubs:{
    RouterLink:{props:['to'],template:'<a :data-record="to.query?.predictionId"><slot/></a>'},
    ElButton:true,
  }}})
  auth.profile={userId:'second',nickname:'Second'} as typeof auth.profile
  await flushPromises()
  expect(wrapper.get('[data-record="current-record"]').text()).toContain('SH600276')
  expect(wrapper.text()).toContain('处理中')
  resolveOld(history('SH600030','old-record'))
  await flushPromises()
  expect(wrapper.find('[data-record="old-record"]').exists()).toBe(false)
  wrapper.unmount()
})
