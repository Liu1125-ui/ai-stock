import {beforeEach,expect,it,vi} from 'vitest'
import {request} from './auth'
import {createPortfolioAnalysis,portfolioAnalysis,portfolioAnalysisOperation,listPortfolioAnalyses} from './portfolioAnalyses'
vi.mock('./auth',()=>({request:vi.fn()}))
beforeEach(()=>vi.resetAllMocks())
it('posts only a request ID, never user positions or prices',async()=>{await createPortfolioAnalysis('request-id');expect(request).toHaveBeenCalledWith('/portfolio-analyses','POST',{requestId:'request-id'})})
it('reads the original operation and saved result without creation',async()=>{await portfolioAnalysisOperation('id');expect(request).toHaveBeenCalledWith('/portfolio-analyses/operations/id');await portfolioAnalysis('saved');expect(request).toHaveBeenLastCalledWith('/portfolio-analyses/saved')})
it('uses fixed upper pagination with encoded query arguments',async()=>{await listPortfolioAnalyses('20','40');expect(request).toHaveBeenCalledWith('/portfolio-analyses?before=20&upper=40')})
