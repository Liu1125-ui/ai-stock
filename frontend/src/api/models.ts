import {request} from './auth'
export type ModelAction='publish'|'reject'|'offline'|'recommend'|'rollback'
interface ModelDateRange{from:string;through:string}
interface StockEvaluation extends ModelDateRange{instrumentId:string;windows:number;mae:string;baselineMae:string;directionAccuracy:string}
export interface ModelEvaluation{sourceHash:string;scope:string;seed:number;validationYear?:number;validationMae?:string;validationBaselineMae?:string;expansion?:StockEvaluation;pool?:StockEvaluation[];mae2025?:string;mae2026?:string;direction2026?:string;rolling?:{datasetHash:string;holdoutMae:string;holdoutBaselineMae:string;holdoutDirection:string;holdoutWindows:number}}
export interface ModelVersion{modelId:string;artifactId:string;manifestHash:string;checkpointHash:string;state:'PENDING'|'PUBLISHED'|'REJECTED'|'OFFLINE';revision:number;everPublished:boolean;registrationHash:string;manifest:{trainingCutoff:string;instruments:string[];config:{seq_len:number;enc_in:number;pred_len:number};featureDefinition:string;trainingData?:{train:ModelDateRange;validation:ModelDateRange;holdout:ModelDateRange}};evaluation:ModelEvaluation;createdAt:string}
export interface ModelHead{scope:string;currentModelId:string|null;revision:number}
export interface ModelCatalog{items:ModelVersion[];head:ModelHead}
export interface ModelReview{operationId:string;expectedRevision:number;expectedHeadRevision:number;reason:string;researchLimitationsAcknowledged:boolean}
export interface ModelEvent{eventId:string;operationId:string|null;actorId:string|null;modelId:string;action:string;reason:string;createdAt:string}
export const listModels=()=>request<ModelCatalog>('/admin/models')
export const modelEvents=(id:string)=>request<ModelEvent[]>('/admin/models/'+encodeURIComponent(id)+'/events')
export const modelAction=(id:string,action:ModelAction,body:ModelReview)=>request<{model:ModelVersion;head:ModelHead;eventId:string}>('/admin/models/'+encodeURIComponent(id)+'/'+action,'POST',body)

interface HoldoutScore { mae:number; rmse:number; direction:number }
interface HoldoutModel extends HoldoutScore { artifactId:string; manifestHash:string }
export interface ModelHoldoutComparison {
 candidateId:string
 referenceId:string
 headRevision:number
 metrics:{
  datasetHash:string
  holdout:ModelDateRange
  windows:number
  candidate:HoldoutModel
  reference:HoldoutModel
  baseline:HoldoutScore
 }
}
export const compareModelHoldout=(id:string)=>request<ModelHoldoutComparison>(
 '/admin/models/'+encodeURIComponent(id)+'/holdout-comparison')
