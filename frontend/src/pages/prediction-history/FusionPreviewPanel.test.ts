import {flushPromises,mount} from '@vue/test-utils'
import {expect,it,vi} from 'vitest'
import {previewPredictionFusion,type PredictionRecord} from '../../api/predictions'
import FusionPreviewPanel from './FusionPreviewPanel.vue'

vi.mock('../../api/predictions',()=>({previewPredictionFusion:vi.fn()}))

function record(predictionId:string,modelId:string,inputVersionId='input'):PredictionRecord {
  return {
    predictionId,modelId,operationId:predictionId,instrumentId:'SH600030',horizon:5,state:'SUCCEEDED',
    errorCode:null,hidden:false,createdAt:'2026-11-11',expiresAt:'2026-11-11',
    modelSnapshot:{artifactId:modelId,manifestHash:'manifest',checkpointHash:'weights',revision:1,manifest:{trainingCutoff:'2023-12-31'}},
    snapshot:{input:{inputVersionId,rawVersionId:'raw',inputHash:'hash',rawContentHash:'content',originDate:'2026-11-10',originRawClose:'25.00'},
      prediction:{points:[],publicationRevision:1,referenceBasis:'origin'}},
  }
}

it('submits a matching pair with exact weights and displays individual, blended and baseline prices',async()=>{
  vi.mocked(previewPredictionFusion).mockResolvedValue({
    instrumentId:'SH600030',originDate:'2026-11-10',inputVersionId:'input',rawVersionId:'raw',baselinePrice:'25.00',
    sources:[
      {predictionId:'first',modelId:'m1',artifactId:'m1',publicationRevision:1,weightBps:5000,referencePrices:['26.00']},
      {predictionId:'second',modelId:'m2',artifactId:'m2',publicationRevision:1,weightBps:5000,referencePrices:['24.00']},
    ],
    points:[{step:1,targetDate:'2026-11-11',ratio:'1.000000000000',referencePrice:'25.000000'}],
    assessment:{state:'PARTIAL',fusion:{verifiedCount:1,evidenceVersionId:'common-realization',originRawRevised:false,
      metrics:{ratioMae:'0.020',ratioRmse:'0.020',directionAccuracy:'1',baselineRatioMae:'0.030'}},members:[]},
  })
  const wrapper=mount(FusionPreviewPanel,{props:{records:[record('first','m1'),record('second','m2'),record('third','m3','different')]}})
  try {
    const checks=wrapper.findAll('input[type=checkbox]')
    expect(checks).toHaveLength(2)
    await checks[0]!.setValue(true)
    await checks[1]!.setValue(true)
    const firstWeight=wrapper.get('input[type=text]')
    await firstWeight.setValue('50.005')
    await wrapper.get('form').trigger('submit')
    expect(previewPredictionFusion).not.toHaveBeenCalled()
    await firstWeight.setValue('50.00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(previewPredictionFusion).toHaveBeenCalledWith([{predictionId:'first',weightBps:5000},{predictionId:'second',weightBps:5000}])
    expect(wrapper.findAll('table')[0]?.findAll('tbody td').map(cell=>cell.text())).toEqual(['26.00','24.00','25.000000','25.00'])
    expect(wrapper.text()).toContain('共同核验 1 / 5')
    expect(wrapper.text()).toContain('2.000%')
    expect(wrapper.text()).toContain('3.000%')
    await wrapper.setProps({records:[record('other','m3')]})
    expect(wrapper.find('table').exists()).toBe(false)
  } finally {wrapper.unmount()}
})
