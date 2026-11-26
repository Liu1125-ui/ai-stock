import {beforeEach,expect,it,vi} from 'vitest'
import {validateSelection,savePending,readPending,clearPending} from './comparisonState'
const input={requestId:'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',instrumentIds:['SH600030','SH600276'],from:'2026-07-09',to:'2026-10-09',evidenceIds:['bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','cccccccc-cccc-cccc-cccc-cccccccccccc']}
beforeEach(()=>sessionStorage.clear())
it('validates two or three unique stocks',()=>{expect(validateSelection(input.instrumentIds)).toBe(true);expect(validateSelection(['SH600030'])).toBe(false);expect(validateSelection(['SH600030','SH600030'])).toBe(false)})
it('keeps exact owner input across refresh and clears only that owner',()=>{savePending('one',input);expect(readPending('one')).toEqual(input);expect(readPending('two')).toBeNull();clearPending('two');expect(readPending('one')).toEqual(input);clearPending('one');expect(readPending('one')).toBeNull()})
it('fails before send when storage fails or saved data is corrupt',()=>{const spy=vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('denied')});expect(()=>savePending('one',input)).toThrow();spy.mockRestore();sessionStorage.setItem('aistock:comparison-pending:one','{}');expect(()=>readPending('one')).toThrow();expect(readPending('one')).toBeNull()})
