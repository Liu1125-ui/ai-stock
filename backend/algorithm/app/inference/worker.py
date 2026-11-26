"""One bounded model inspection/inference process; no publication authority."""
import json,os,sys,time

def engine_for(root):
    from .engine import InferenceEngine
    from .pool_artifacts import PoolArtifactStore
    engine=InferenceEngine(root)
    engine.store=PoolArtifactStore(root)
    return engine


def main():
    start=time.monotonic()
    try:
        from .pool_artifacts import PoolArtifactStore
        body=json.loads(sys.stdin.read(1048577))
        root=os.environ['MODEL_ARTIFACT_ROOT']
        if 'modelId' in body:
            from .publication import fetch_publication,fetch_window,PublicationFailure
            publication=fetch_publication(body);window=fetch_window(body)
            result=engine_for(root).validate(body['artifactId'],body['manifestHash'],window)
            if result['checkpointHash']!=publication['checkpointHash']:raise PublicationFailure('MODEL_PUBLICATION_MISMATCH')
            result.update(formalInference=True,validationOnly=False,modelId=body['modelId'],publicationRevision=publication['publicationRevision'],instrumentId=body['instrumentId'])
        elif 'window' in body:
            result=engine_for(root).validate(body['artifactId'],body['manifestHash'],body['window'])
        else:
            data,_,_,_=PoolArtifactStore(root).load(body['artifactId'],body['manifestHash'])
            result={'inspectionOnly':True,'artifactId':body['artifactId'],'manifestHash':body['manifestHash'],'checkpointHash':data['checkpointHash'],'manifest':data}
        output={'state':'AVAILABLE','result':result,'elapsedMillis':round((time.monotonic()-start)*1000)}
    except Exception as error:
        output={'state':getattr(error,'code','MODEL_VALIDATION_FAILED'),'result':None}
    print('AISTOCK_MODEL:'+json.dumps(output,separators=(',',':'),allow_nan=False))
if __name__=='__main__':main()
