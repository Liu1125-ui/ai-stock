import {beforeEach,it,expect,vi} from 'vitest'
import {normalizeQuestion,savePending,readPending,removePending,clearQuestionRecovery} from './questionRecovery'
const input={operationId:'11111111-1111-4111-8111-111111111111',analysisId:'22222222-2222-4222-8222-222222222222',question:'风险？'}
beforeEach(()=>{sessionStorage.clear();vi.restoreAllMocks()})
it('normalizes Unicode and rejects unsafe boundaries',()=>{expect(normalizeQuestion('\u00a0e\u0301\u3000')).toBe('é');expect(normalizeQuestion('😀'.repeat(300))).toHaveLength(600);for(const s of ['😀'.repeat(301),'\n问题','\ud800',' '])expect(()=>normalizeQuestion(s)).toThrow()})
it('restores only fixed own pending and clears by identity',()=>{savePending('one',input);expect(readPending('one',input.analysisId)).toEqual(input);expect(readPending('other',input.analysisId)).toBeNull();clearQuestionRecovery('other');expect(readPending('one',input.analysisId)).toEqual(input);removePending('one',input.analysisId);expect(readPending('one',input.analysisId)).toBeNull()})
it('storage failure never silently proceeds',()=>{vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('quota')});expect(()=>savePending('one',input)).toThrow()})
