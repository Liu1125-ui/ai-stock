"""Candidate inference only. Business publication belongs to MS-03."""
from decimal import Decimal,ROUND_HALF_UP,InvalidOperation
import numpy as np
import torch
from .artifacts import ArtifactStore,InferenceError
from .contracts import prepare_window,input_digest,number

class InferenceEngine:
    def __init__(self,root):self.store=ArtifactStore(root)
    def validate(self,artifact_id,manifest_hash,window):
        data,model,mean,std=self.store.load(artifact_id,manifest_hash)
        base,origin=prepare_window(window,data['instruments'])
        z=(base-mean)/std
        if not np.isfinite(z).all():raise InferenceError('MODEL_INPUT_INVALID')
        clip=float((np.abs(z)>10).mean());normalized=np.clip(z,-10,10).astype(np.float32)
        torch.set_num_threads(2)
        x=torch.from_numpy(normalized[None]);dec=torch.cat((x[:,-30:,:],torch.zeros(1,5,6)),dim=1)
        with torch.inference_mode():output=model(x,None,dec,None).numpy()
        if output.shape!=(1,5,1) or not np.isfinite(output).all() or not (output>0).all():raise InferenceError('MODEL_OUTPUT_INVALID')
        points=[]
        for step,(target,p) in enumerate(zip(window['targetDates'],output[0,:,0]),1):
            try:
                ratio=Decimal(str(float(p))).quantize(Decimal('.000000000001'),rounding=ROUND_HALF_UP)
                price=(origin*ratio).quantize(Decimal('.000001'),rounding=ROUND_HALF_UP)
                number(format(ratio,'f'),24,12);number(format(price,'f'),20,6)
                points.append({'step':step,'targetDate':target,'ratio':format(ratio,'f'),'referencePrice':format(price,'f'),'displayReferencePrice':format(price.quantize(Decimal('.01'),rounding=ROUND_HALF_UP),'f')})
            except (InvalidOperation,InferenceError):raise InferenceError('MODEL_OUTPUT_INVALID') from None
        return {'validationOnly':True,'artifactId':artifact_id,'manifestHash':manifest_hash,'checkpointHash':data['checkpointHash'],'featureDefinition':data['featureDefinition'],'targetDefinition':data['targetDefinition'],'inputVersionId':window['inputVersionId'],'inputHash':window['inputHash'],'rawVersionId':window['rawVersionId'],'calendarHash':window['calendarHash'],'originDate':window['originDate'],'originRawClose':window['originRawClose'],'freshness':window['freshness'],'referenceBasis':'ORIGIN_SHARE_BASIS','tensorInputHash':input_digest(normalized,window['targetDates']),'clippedFraction':clip,'points':points}
