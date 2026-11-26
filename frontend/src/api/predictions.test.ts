import {describe,it,expect} from 'vitest'
import {predictionFailure} from './predictions'
describe('prediction failure recovery guidance',()=>{
 it.each(['FORECAST_INPUT_STALE','FORECAST_INPUT_FRESHNESS_UNKNOWN','FORECAST_WINDOW_INCOMPLETE','SOURCE_TIMEOUT','SOURCE_UNAVAILABLE','SOURCE_INVALID','MARKET_VERSION_UNAVAILABLE'])('explains %s without presenting a result',code=>{
  const text=predictionFailure(code)
  expect(text).not.toBe('预测未完成，原行情和历史结果仍可查看')
  expect(text).not.toContain(code)
 })
 it('keeps unknown provider messages private',()=>expect(predictionFailure('secret-upstream-error')).not.toContain('secret'))
})
