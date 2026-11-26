import {request} from './auth'
export interface VerificationPoint {step:number;targetDate:string;state:'WAITING'|'PENDING'|'VERIFIED'|'MISSING'|'SUSPENDED'|'CALENDAR_CHANGED';referencePrice:string;actualPrice?:string|null;signedError?:string|null;absoluteError?:string|null;directionHit?:boolean|null}
export interface VerificationMetrics {priceMae:string|null;priceRmse:string|null;ratioMae:string|null;ratioRmse:string|null;directionAccuracy:string|null;baselineRatioMae:string|null;baselineRatioRmse:string|null;relativeMaeImprovement:string|null}
export interface VerificationEntry {verificationVersionId:string;realizationVersionId:string;ruleVersion:string;createdAt:string;dueThrough:string;evidence:{capturedAt:string;rawVersionId:string};result:{state:string;verifiedCount:number;originRawRevised:boolean;points:VerificationPoint[];metrics:VerificationMetrics}}
export interface VerificationHistory {predictionId:string;page:number;size:number;total:number;cutoffDate:string;expectedPoints:VerificationPoint[];currentVersionId:string|null;current:VerificationEntry|null;items:{verificationVersionId:string;createdAt:string;dueThrough:string;state:string;verifiedCount:number}[];latestAttempt:null|{state:string;errorCode:string|null;expired:boolean}}
const path=(id:string)=>'/predictions/'+encodeURIComponent(id)+'/verifications'
export const verificationHistory=(id:string,page=1)=>request<VerificationHistory>(path(id)+'?page='+page)
export const verificationDetail=(id:string,version:string)=>request<VerificationEntry>(path(id)+'/'+encodeURIComponent(version))
