import {request} from './auth'

const base='/training-submissions'
const id=(value:string)=>encodeURIComponent(value)
export interface TrainingSummary {requestId:string;createdAt:string}
export interface TrainingHistory {items:TrainingSummary[];nextCursor:string|null}
export interface TrainingStatus {
  taskId:string
  configurationId:string
  state:'AWAITING_DISPATCH'|'DISPATCH_CONFLICT'|'QUEUED'|'RUNNING'|'SUCCEEDED'|'FAILED'|'CANCELLED'|'STATUS_UNAVAILABLE'
  startedAt:string|null
  finishedAt:string|null
  modelId:string|null
}
interface Evaluation {mae:number;rmse:number;baselineMae:number;direction:number;samples:number}
export interface TrainingMetrics {
  seed:number
  selectedEpoch:number
  trainingSamples:number
  elapsedSeconds:number
  epochHistory:{epoch:number;trainingMse:number;validation:Evaluation}[]
  heldout2025?:Evaluation
  heldout2026?:Evaluation
  holdout?:Evaluation & {from:string;through:string}
}

export const trainingOptions=()=>request<string[]>(base+'/options')
export const trainingHistory=(before?:string)=>request<TrainingHistory>(base+(before?'?before='+id(before):''))
export const submitTraining=(requestId:string,configurationIds:string[])=>
  request<{requestId:string}>(base,'POST',{requestId,configurationIds})
export const trainingStatus=(requestId:string)=>request<TrainingStatus[]>(base+'/'+id(requestId)+'/status')
export const trainingMetrics=(requestId:string,taskId:string)=>
  request<TrainingMetrics>(base+'/'+id(requestId)+'/tasks/'+id(taskId)+'/metrics')
