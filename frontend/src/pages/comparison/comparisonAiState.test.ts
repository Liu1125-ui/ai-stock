import {beforeEach,expect,it,vi} from 'vitest'
import {savePending,readPending,clearOwner} from './comparisonAiState'
const id='11111111-1111-4111-8111-111111111111',input={operationId:'22222222-2222-4222-8222-222222222222',comparisonId:id}
beforeEach(()=>sessionStorage.clear())
it('isolates pending operations by owner and comparison',()=>{savePending('one',input);expect(readPending('one',id)).toEqual(input);expect(readPending('two',id)).toBeNull();clearOwner('two');expect(readPending('one',id)).toEqual(input);clearOwner('one');expect(readPending('one',id)).toBeNull()})
it('rejects unavailable or corrupt storage before submitting',()=>{const spy=vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw Error('denied')});expect(()=>savePending('one',input)).toThrow();spy.mockRestore();sessionStorage.setItem('aistock:comparison-ai-pending:one:'+id,'{}');expect(()=>readPending('one',id)).toThrow();expect(readPending('one',id)).toBeNull()})
