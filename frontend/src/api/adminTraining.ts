import {requestWithoutRetry} from './auth'

export interface TrainingCancellation {
  operationId:string
  actorUserId:string
  reasonCode:string
  cancelledAt:string
}
export interface AdminTrainingTask {
  taskId:string
  configurationId:string
  state:'QUEUED'|'RUNNING'|'SUCCEEDED'|'FAILED'|'CANCELLED'
  receivedAt:string
  startedAt:string|null
  finishedAt:string|null
  modelId:string|null
  cancellation:TrainingCancellation|null
}
export interface AdminTrainingPage {
  items:AdminTrainingTask[]
  hasMore:boolean
  actionsEnabled:boolean
}
export interface TrainingCancelCommand {
  operationId:string
  expectedRevision:'0'
  reasonCode:'CANCEL_NOT_NEEDED'|'CANCEL_DUPLICATE_REQUEST'
}

const base='/admin/tasks/training'
export const listAdminTraining=(page:number)=>requestWithoutRetry<AdminTrainingPage>(base+'?page='+page,'GET')
export const cancelAdminTraining=(id:string,command:TrainingCancelCommand)=>
  requestWithoutRetry<{taskId:string;operationId:string;afterState:string}>(
    base+'/'+encodeURIComponent(id)+'/cancel','POST',command,{'X-Aistock-Admin-Action':'1'})
