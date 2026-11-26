import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,it,expect,vi} from 'vitest'
import {createPinia} from 'pinia'
import {useAuthStore} from '../../stores/auth'
import VerificationPanel from './VerificationPanel.vue'
import * as api from '../../api/verifications'
vi.mock('../../api/verifications',()=>({verificationHistory:vi.fn(),verificationDetail:vi.fn()}))
const points=[8,9,12,13,14].map((d,i)=>({step:i+1,targetDate:`2026-10-${String(d).padStart(2,'0')}`,referencePrice:'25.800000',state:'WAITING',actualPrice:null,signedError:null,absoluteError:null}))
const pending=()=>({predictionId:'p',page:1,size:20,total:0,cutoffDate:'2026-10-05',expectedPoints:points,items:[],currentVersionId:null,current:null,latestAttempt:null})
const entry=(id='v')=>({verificationVersionId:id,realizationVersionId:'source-'+id,ruleVersion:'origin-share-close-v1',createdAt:'2026-10-09T13:00:00Z',dueThrough:'2026-10-12',evidence:{capturedAt:'2026-10-09T12:00:00Z',rawVersionId:'raw'},result:{state:'PARTIAL',verifiedCount:2,originRawRevised:false,points:points.map((p,i)=>({...p,state:i===0||i===2?'VERIFIED':i===1?'SUSPENDED':'WAITING',actualPrice:i===0||i===2?'25.600000':null,signedError:i===0||i===2?'0.200000':null,absoluteError:i===0||i===2?'0.200000':null})),metrics:{priceMae:'0.200000000000000000',priceRmse:'0.200000000000000000',ratioMae:'0.007733952049497293',ratioRmse:'0.007733952049497293',directionAccuracy:'0.000000000000000000',baselineRatioMae:'0.010000000000000000',baselineRatioRmse:'0.010000000000000000',relativeMaeImprovement:'0.226604795050270700'} as api.VerificationMetrics}})
const history=()=>({...pending(),cutoffDate:'2026-10-12',total:1,currentVersionId:'v',current:entry(),items:[{...entry(),state:'PARTIAL',verifiedCount:2}]})
const record={predictionId:'p',state:'SUCCEEDED'} as any
function mounted(){const pinia=createPinia();const auth=useAuthStore(pinia);auth.profile={userId:'u',email:'u@example.com',nickname:'u',role:'USER',status:'ENABLED',emailVerified:true,experimentAllowed:false,revision:0};const w=mount(VerificationPanel,{props:{record},global:{plugins:[pinia]}});return {w,auth}}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(api.verificationHistory).mockResolvedValue(pending() as any)})
it('shows five waiting dates without fictional actual or perfect accuracy',async()=>{const {w}=mounted();await flushPromises();expect(w.findAll('tbody tr')).toHaveLength(5);expect(w.text()).toContain('尚未到期');expect(w.text()).toContain('尚无有效样本');expect(w.text()).not.toContain('100%');expect(w.text()).not.toContain('25.600000');w.unmount()})
it('displays exact partial metrics and breaks actual line across missing points',async()=>{vi.mocked(api.verificationHistory).mockResolvedValue(history() as any);const {w}=mounted();await flushPromises();expect(w.text()).toContain('有效样本 2 / 5');expect(w.text()).toContain('0.2000');expect(w.text()).toContain('停牌');expect(w.findAll('[data-actual-point]')).toHaveLength(2);expect(w.findAll('[data-actual-segment]')).toHaveLength(0);w.unmount()})
it('keeps saved result when refresh fails and explains last update failure',async()=>{const h=history();h.latestAttempt={state:'FAILED',errorCode:'SOURCE_BUSY'} as any;vi.mocked(api.verificationHistory).mockResolvedValueOnce(h as any).mockRejectedValueOnce(new Error('offline'));const {w}=mounted();await flushPromises();await w.get('[data-refresh]').trigger('click');await flushPromises();expect(w.text()).toContain('0.2000');expect(w.text()).toContain('上次验证');expect(w.text()).toContain('查询失败');w.unmount()})
it('clears account data synchronously and ignores late response',async()=>{let resolve!:(x:any)=>void;vi.mocked(api.verificationHistory).mockReturnValue(new Promise(r=>resolve=r));const {w,auth}=mounted();auth.profile=null;resolve(history());await flushPromises();expect(w.text()).not.toContain('25.600000');expect(w.text()).not.toContain('source-v');w.unmount()})
it('latest version selection wins against late prior selection',async()=>{const h=history();h.items.push({...entry('older'),state:'PARTIAL',verifiedCount:2});vi.mocked(api.verificationHistory).mockResolvedValue(h as any);let late!:(x:any)=>void;vi.mocked(api.verificationDetail).mockReturnValueOnce(new Promise(r=>late=r));const {w}=mounted();await flushPromises();await w.get('[data-version]').setValue('older');await w.get('[data-version]').setValue('v');late(entry('older'));await flushPromises();expect(w.text()).toContain('source-v');expect(w.text()).not.toContain('source-older');w.unmount()})
it('stored evidence with zero valid samples keeps metrics empty',async()=>{const h=history();h.current.result.verifiedCount=0;h.current.result.state='PENDING';h.items[0]!.verifiedCount=0;h.current.result.points=h.current.result.points.map((p,i)=>({...p,state:i<3?'MISSING':'WAITING',actualPrice:null,signedError:null,absoluteError:null}));h.current.result.metrics=Object.fromEntries(Object.keys(h.current.result.metrics).map(k=>[k,null])) as any;vi.mocked(api.verificationHistory).mockResolvedValue(h as any);const {w}=mounted();await flushPromises();expect(w.text()).toContain('尚无有效样本');expect(w.find('.metrics').exists()).toBe(false);expect(w.findAll('[data-actual-point]')).toHaveLength(0);w.unmount()})
for(const order of ['detail-first','refresh-first'])it(`history selection stays consistent during refresh (${order})`,async()=>{const h=history();h.items.push({...entry('older'),state:'PARTIAL',verifiedCount:2});let refresh!:(x:any)=>void,detail!:(x:any)=>void;vi.mocked(api.verificationHistory).mockResolvedValueOnce(h as any).mockReturnValueOnce(new Promise(r=>refresh=r));vi.mocked(api.verificationDetail).mockReturnValue(new Promise(r=>detail=r));const {w}=mounted();await flushPromises();await w.get('[data-refresh]').trigger('click');await w.get('[data-version]').setValue('older');if(order==='detail-first'){detail(entry('older'));await flushPromises();refresh({...h,items:[h.items[0]]})}else{refresh({...h,items:[h.items[0]]});await flushPromises();detail(entry('older'))}await flushPromises();expect((w.get('[data-version]').element as HTMLSelectElement).value).toBe('older');expect(w.text()).toContain('source-older');expect(w.text()).toContain('历史验证结果');w.unmount()})

it('formats metrics and shows direction hits relative to forecast origin',async()=>{
 const h=history();h.current.result.metrics.priceRmse='0.295006807356203223';h.current.result.metrics.directionAccuracy='0.500000000000000000';h.current.result.metrics.relativeMaeImprovement='-0.012519607843137200';
 h.current.result.points=h.current.result.points.map((p,i)=>({...p,directionHit:p.state==='VERIFIED'?i===0:null}));
 vi.mocked(api.verificationHistory).mockResolvedValue(h as any);const {w}=mounted();await flushPromises();
 const metrics=w.get('.metrics').text();expect(metrics).toContain('0.2000');expect(metrics).toContain('0.2950');expect(metrics).toContain('0.007734');expect(metrics).toContain('50.00%（命中1/2个已核验点）');expect(metrics).toContain('-1.25%');expect(metrics).not.toContain('000000000000');expect(w.text()).toContain('方向相对预测原点收盘价判断');
 expect(h.current.result.metrics.priceRmse).toBe('0.295006807356203223');w.unmount()
})
it('does not invent zero percent or hits for unavailable metrics',async()=>{
 const h=history();h.current.result.metrics.directionAccuracy=null;h.current.result.metrics.relativeMaeImprovement=null;
 vi.mocked(api.verificationHistory).mockResolvedValue(h as any);const {w}=mounted();await flushPromises();expect(w.get('.metrics').text()).toContain('暂无有效值');expect(w.get('.metrics').text()).not.toContain('命中');expect(w.get('.metrics').text()).not.toContain('0.00%');w.unmount()
})

it('keeps a selected historical version on refresh and uses its own cutoff',async()=>{
 const h=history()
 h.cutoffDate='2026-10-15'
 const old=entry('older')
 old.dueThrough='2026-10-09'
 old.result.points=old.result.points.map((point,i)=>i>=2?{...point,state:'WAITING',actualPrice:null,signedError:null,absoluteError:null}:point)
 old.result.verifiedCount=1
 h.items.push({...old,state:'PARTIAL',verifiedCount:1})
 vi.mocked(api.verificationHistory).mockResolvedValue(h as any)
 vi.mocked(api.verificationDetail).mockResolvedValue(old as any)
 const {w}=mounted()
 try{
  await flushPromises()
  await w.get('[data-version]').setValue('older')
  await flushPromises()
  expect(w.findAll('tbody tr')[2]?.text()).toContain('当时尚未到期')
  await w.get('[data-refresh]').trigger('click')
  await flushPromises()
  expect(w.text()).toContain('历史验证结果')
  expect(w.text()).toContain('source-older')
  await w.setProps({record:{...record}})
  await flushPromises()
  expect(w.text()).toContain('source-older')
  await w.get('[data-current]').trigger('click')
  await flushPromises()
  expect(w.text()).toContain('当前验证结果')
  expect(w.text()).toContain('source-v')
 }finally{w.unmount()}
})
