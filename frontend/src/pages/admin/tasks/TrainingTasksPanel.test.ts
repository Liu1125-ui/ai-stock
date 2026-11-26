import {flushPromises,mount} from '@vue/test-utils'
import {createPinia} from 'pinia'
import {expect,it,vi} from 'vitest'
import TrainingTasksPanel from './TrainingTasksPanel.vue'
import {cancelAdminTraining,listAdminTraining,type AdminTrainingTask} from '../../../api/adminTraining'

vi.mock('../../../api/adminTraining',()=>({cancelAdminTraining:vi.fn(),listAdminTraining:vi.fn()}))

it('reuses the original operation after an ambiguous reply and renders the audit',async()=>{
  const task:AdminTrainingTask={
    taskId:'00000000-0000-0000-0000-000000000001',configurationId:'five-day',state:'QUEUED',
    receivedAt:'2026-11-12T00:00:00Z',startedAt:null,finishedAt:null,modelId:null,cancellation:null,
  }
  vi.mocked(listAdminTraining).mockResolvedValueOnce({items:[task],hasMore:false,actionsEnabled:true})
  const cancel=vi.mocked(cancelAdminTraining)
  cancel.mockRejectedValueOnce(new Error('结果暂无法确认')).mockImplementationOnce(async(taskId,command)=>{
    vi.mocked(listAdminTraining).mockResolvedValueOnce({
      items:[{...task,state:'CANCELLED',cancellation:{operationId:command.operationId,actorUserId:'admin-one',
        reasonCode:command.reasonCode,cancelledAt:'2026-11-12T00:01:00Z'}}],
      hasMore:false,actionsEnabled:true,
    })
    return {taskId,operationId:command.operationId,afterState:'CANCELLED'}
  })
  const panel=mount(TrainingTasksPanel,{global:{plugins:[createPinia()]}})
  try {
    await flushPromises()
    await panel.findAll('button').find(button=>button.text()==='取消排队')!.trigger('click')
    await panel.get('form').trigger('submit')
    await flushPromises()
    expect(panel.text()).toContain('用原编号重试')
    await panel.get('form').trigger('submit')
    await flushPromises()
    expect(cancel).toHaveBeenCalledTimes(2)
    expect(cancel.mock.calls[1]).toEqual(cancel.mock.calls[0])
    expect(panel.get('details').text()).toContain('admin-one')
  } finally {panel.unmount()}
})
