import {beforeEach,expect,it,vi} from 'vitest'
import {request} from './auth'
import {createQuestion,getPortfolioRule,listQuestions} from './portfolioQuestions'
vi.mock('./auth',()=>({request:vi.fn()}))
beforeEach(()=>vi.resetAllMocks())
it('posts only the operation analysis question and disclosure fields',async()=>{const input={operationId:'op',analysisId:'analysis',question:'解释盈亏',disclosureAccepted:true};await createQuestion(input);expect(request).toHaveBeenCalledWith('/portfolio-questions','POST',input)})
it('rules and history use readonly owned-analysis routes and fixed upper pagination',async()=>{await getPortfolioRule('id','PROFIT_FORMULA');expect(request).toHaveBeenLastCalledWith('/portfolio-questions/rules?analysisId=id&topic=PROFIT_FORMULA');await listQuestions('id','10','30');expect(request).toHaveBeenLastCalledWith('/portfolio-questions?analysisId=id&before=10&upper=30')})
