import {it,expect} from 'vitest'
import {defaultWindow,chartCount,validateWindow} from './statisticsPresentation'
it('computes Shanghai dates and preserves unsafe integers for text only',()=>{expect(defaultWindow(new Date('2026-10-21T16:00:00Z'))).toEqual({from:'2026-10-16',to:'2026-10-22'});expect(chartCount('9007199254740992')).toBeNull();expect(chartCount('12')).toBe(12);expect(chartCount('-1')).toBeNull()})
it('rejects invalid or overlong date windows before requesting',()=>{expect(validateWindow({from:'2026-07-24',to:'2026-10-22'},new Date('2026-10-22T04:00:00Z'))).not.toBe('');expect(validateWindow({from:'2026-10-22',to:'2026-10-22'},new Date('2026-10-22T04:00:00Z'))).toBe('')})
