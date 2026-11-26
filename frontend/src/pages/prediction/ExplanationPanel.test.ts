import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,it,expect,vi} from 'vitest'
import {createPinia} from 'pinia'
import {useAuthStore} from '../../stores/auth'
import ExplanationPanel from './ExplanationPanel.vue'
import * as api from '../../api/explanations'
vi.mock('../../api/explanations',()=>({explanation:vi.fn(),generateExplanation:vi.fn()}))
const record={predictionId:'p',state:'SUCCEEDED'} as any
const empty=()=>({configured:true,currentContextHash:'h',stale:false,latest:null,latestAttempt:null})
const saved=()=>({...empty(),latest:{explanationId:'e',contextHash:'h',createdAt:'now',context:{facts:{PRICE:'25.8元'},providerModel:'model',promptVersion:'v1'},paragraphs:[{kind:'overview',text:'参考价格为25.8元。',refs:['PRICE']}]}})
function mounted(){const pinia=createPinia(),auth=useAuthStore(pinia);auth.profile={userId:'u'} as any;return {auth,w:mount(ExplanationPanel,{props:{record},global:{plugins:[pinia]}})}}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(api.explanation).mockResolvedValue(empty());vi.mocked(api.generateExplanation).mockResolvedValue({operationId:'op',state:'FAILED',contextHash:'h',explanationId:null,errorCode:'AI_OUTPUT_INVALID',leaseUntil:'later'})})
it('never starts paid generation on mount, refresh or prediction change; only explicit click generates',async()=>{const {w}=mounted();await flushPromises();expect(api.generateExplanation).not.toHaveBeenCalled();await w.get('[data-refresh]').trigger('click');await flushPromises();expect(api.generateExplanation).not.toHaveBeenCalled();await w.setProps({record:{...record,predictionId:'next'}});await flushPromises();expect(api.generateExplanation).not.toHaveBeenCalled();await w.get('[data-generate]').trigger('click');await flushPromises();expect(api.generateExplanation).toHaveBeenCalledTimes(1);await w.get('[data-refresh]').trigger('click');await flushPromises();expect(api.generateExplanation).toHaveBeenCalledTimes(1);w.unmount()})
it('retains old explanation after refresh failure without calling provider',async()=>{vi.mocked(api.explanation).mockResolvedValueOnce(saved()).mockRejectedValueOnce(new Error('offline'));const {w}=mounted();await flushPromises();await w.get('[data-refresh]').trigger('click');await flushPromises();expect(w.text()).toContain('25.8元');expect(w.text()).toContain('查询失败');expect(api.generateExplanation).not.toHaveBeenCalled();w.unmount()})
it('stale result needs explicit regeneration',async()=>{vi.mocked(api.explanation).mockResolvedValue({...saved(),stale:true,currentContextHash:'next'});const {w}=mounted();await flushPromises();expect(w.text()).toContain('依据已更新');expect(api.generateExplanation).not.toHaveBeenCalled();await w.get('[data-generate]').trigger('click');await flushPromises();expect(api.generateExplanation).toHaveBeenCalledTimes(1);w.unmount()})
it('discards late result when account changes',async()=>{let resolve!:(x:any)=>void;vi.mocked(api.explanation).mockReturnValueOnce(new Promise(r=>resolve=r));const {w,auth}=mounted();auth.profile=null;resolve(saved());await flushPromises();expect(w.text()).not.toContain('25.8元');expect(api.generateExplanation).not.toHaveBeenCalled();w.unmount()})
it('uses plain text for generated content',async()=>{const value=saved();value.latest.paragraphs[0]!.text='<img src=x onerror=alert(1)>';vi.mocked(api.explanation).mockResolvedValue(value);const {w}=mounted();await flushPromises();expect(w.find('img').exists()).toBe(false);expect(w.text()).toContain('<img');w.unmount()})

it('clears an unknown operation after refresh confirms failure',async()=>{vi.mocked(api.generateExplanation).mockRejectedValueOnce(new Error('lost'));const {w}=mounted();await flushPromises();await w.get('[data-generate]').trigger('click');await flushPromises();const op=vi.mocked(api.generateExplanation).mock.calls[0]![1];vi.mocked(api.explanation).mockResolvedValue({...empty(),latestAttempt:{operationId:op,state:'FAILED',contextHash:'h',explanationId:null,errorCode:'AI_UNAVAILABLE',leaseUntil:'later'}});await w.get('[data-refresh]').trigger('click');await flushPromises();await w.get('[data-generate]').trigger('click');await flushPromises();expect(vi.mocked(api.generateExplanation).mock.calls[1]![1]).not.toBe(op);w.unmount()})

it('explains a frozen provider without blaming the API key',async()=>{vi.mocked(api.explanation).mockResolvedValue({...empty(),latestAttempt:{operationId:'op',state:'FAILED',contextHash:'h',explanationId:null,errorCode:'AI_SERVICE_FROZEN',leaseUntil:'later'}});const {w}=mounted();await flushPromises();expect(w.text()).toContain('服务已暂停');expect(w.text()).not.toContain('Key填错');expect(api.generateExplanation).not.toHaveBeenCalled();w.unmount()})

it('retains evidence but does not display an explanation needing review',async()=>{const value={...saved(),latest:{...saved().latest,needsReview:true}};vi.mocked(api.explanation).mockResolvedValue(value);const {w}=mounted();await flushPromises();expect(w.text()).not.toContain('参考价格为25.8元。');expect(w.text()).toContain('旧版解释待重新核验');expect(w.text()).toContain('e');w.unmount()})

it('keeps an uncertain operation across read-only refresh and reuses it only on explicit retry',async()=>{
 vi.mocked(api.generateExplanation).mockRejectedValueOnce(new Error('response lost'))
 const {w}=mounted();await flushPromises();expect(api.generateExplanation).not.toHaveBeenCalled()
 await w.get('[data-generate]').trigger('click');await flushPromises()
 const original=vi.mocked(api.generateExplanation).mock.calls[0]![1]
 await w.get('[data-refresh]').trigger('click');await flushPromises();expect(api.generateExplanation).toHaveBeenCalledTimes(1)
 await w.get('[data-generate]').trigger('click');await flushPromises();expect(api.generateExplanation).toHaveBeenCalledTimes(2)
 expect(vi.mocked(api.generateExplanation).mock.calls[1]![1]).toBe(original);w.unmount()
})
