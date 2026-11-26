"""Formal inference protocol tests with synthetic weights and mock business HTTP, not accuracy evidence."""
import copy,json,threading
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from urllib.parse import urlparse,parse_qs
import pytest
from fastapi.testclient import TestClient
from app.main import app
from test_model_artifacts import bundle,window,ARTIFACT,manifest_hash

MODEL='33333333-3333-3333-3333-333333333333'
HEADERS={'X-Service-Token':'private-test'}

@contextmanager
def authority(monkeypatch,bundle,window,mode='published'):
    root,folder,data=bundle
    packet={'modelId':MODEL,'artifactId':ARTIFACT,'manifestHash':manifest_hash(folder),'instrumentId':window['instrumentId'],'inputVersionId':window['inputVersionId']}
    published={'modelId':MODEL,'artifactId':ARTIFACT,'manifestHash':packet['manifestHash'],'checkpointHash':data['checkpointHash'],'publicationRevision':1}
    snapshot=copy.deepcopy(window);snapshot['freshness']='FRESH'
    hits=[]
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def do_GET(self):
            hits.append(self.path)
            assert self.headers.get('X-Service-Token')=='private-test'
            if self.path.endswith('/publication'):
                if mode=='pending':self.reply(409,{'code':'MODEL_NOT_PUBLISHED','data':None});return
                if mode=='redirect':self.send_response(302);self.send_header('Location','http://127.0.0.1:1/private');self.end_headers();return
                if mode=='oversized':self.reply(200,b'x'*65537);return
                if mode=='wrong_model':published['modelId']='44444444-4444-4444-4444-444444444444'
                if mode=='wrong_hash':published['manifestHash']='0'*64
                self.reply(200,{'code':'OK','data':published})
            elif self.path.startswith('/internal/v1/market/forecast-raw-window?'):
                query=parse_qs(urlparse(self.path).query)
                assert query['instrumentId']==[packet['instrumentId']]
                head={'instrumentId':packet['instrumentId'],'state':'AVAILABLE','provider':'baostock','priceBasis':'RAW','versionId':window['rawVersionId'],'rawContentHash':window['rawContentHash']}
                if mode=='new_raw':head['versionId']='55555555-5555-5555-5555-555555555555'
                if mode=='new_hash':head['rawContentHash']='d'*64
                self.reply(200,{'code':'OK','data':head})
            else:
                query=parse_qs(urlparse(self.path).query)
                assert query['instrumentId']==[packet['instrumentId']] and query['inputVersionId']==[packet['inputVersionId']]
                if mode=='stale':snapshot['freshness']='STALE'
                if mode=='wrong_input':snapshot['inputVersionId']='44444444-4444-4444-4444-444444444444'
                self.reply(200,{'code':'OK','data':snapshot})
        def reply(self,status,value):
            raw=value if isinstance(value,bytes) else json.dumps(value).encode()
            self.send_response(status);self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
    server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    monkeypatch.setenv('SERVICE_TOKEN','private-test');monkeypatch.setenv('MODEL_ARTIFACT_ROOT',str(root))
    base=f'http://127.0.0.1:{server.server_port}'
    monkeypatch.setenv('PREDICTION_MODEL_AUTHORITY_URL',base);monkeypatch.setenv('MARKET_INPUT_AUTHORITY_URL',base)
    try:yield packet,hits
    finally:server.shutdown();server.server_close();thread.join(timeout=2)

def test_formal_contract_authenticates_and_rejects_forged_window(monkeypatch,bundle,window):
    with authority(monkeypatch,bundle,window) as (packet,hits):
        client=TestClient(app)
        assert client.post('/internal/v1/infer',content='{').status_code==401
        assert client.post('/internal/v1/infer',json={**packet,'window':window},headers=HEADERS).status_code==422
        assert client.post('/internal/v1/infer',json={**packet,'modelId':'../bad'},headers=HEADERS).status_code==422
        assert client.post('/internal/v1/infer',content=b'x'*1048577,headers=HEADERS).status_code==413
        assert hits==[]

def test_formal_uses_authoritative_fixed_window_and_returns_five_points(monkeypatch,bundle,window):
    with authority(monkeypatch,bundle,window) as (packet,hits):
        response=TestClient(app).post('/internal/v1/infer',json=packet,headers=HEADERS)
        assert response.status_code==200 and response.json()['state']=='AVAILABLE'
        result=response.json()['result']
        assert result['formalInference'] is True and result['validationOnly'] is False
        assert result['modelId']==MODEL and result['publicationRevision']==1 and result['inputVersionId']==window['inputVersionId']
        assert len(result['points'])==5 and result['points'][0]['referencePrice']=='10.200000'
        assert [p['targetDate'] for p in result['points']]==window['targetDates']
        assert len(hits)==3

@pytest.mark.parametrize('mode,expected',[('pending','MODEL_NOT_PUBLISHED'),('wrong_model','MODEL_PUBLICATION_MISMATCH'),('wrong_hash','MODEL_PUBLICATION_MISMATCH'),('stale','MODEL_INPUT_NOT_CURRENT'),('wrong_input','MODEL_INPUT_INCOMPATIBLE'),('redirect','MODEL_PUBLICATION_UNAVAILABLE'),('oversized','MODEL_PUBLICATION_UNAVAILABLE')])
def test_formal_business_validation_failures_never_have_points(monkeypatch,bundle,window,mode,expected):
    with authority(monkeypatch,bundle,window,mode) as (packet,hits):
        result=TestClient(app).post('/internal/v1/infer',json=packet,headers=HEADERS).json()
        assert result['state']==expected and result['result'] is None
        if mode=='redirect':assert len(hits)==1

def test_formal_authority_failure_is_controlled(monkeypatch,bundle,window):
    with authority(monkeypatch,bundle,window) as (packet,hits):
        monkeypatch.setenv('PREDICTION_MODEL_AUTHORITY_URL','http://127.0.0.1:1')
        result=TestClient(app).post('/internal/v1/infer',json=packet,headers=HEADERS).json()
        assert result['state']=='MODEL_PUBLICATION_UNAVAILABLE' and result['result'] is None and hits==[]


@pytest.mark.parametrize('mode',['new_raw','new_hash'])
def test_formal_rejects_same_day_input_after_raw_head_changes(monkeypatch,bundle,window,mode):
    with authority(monkeypatch,bundle,window,mode) as (packet,hits):
        result=TestClient(app).post('/internal/v1/infer',json=packet,headers=HEADERS).json()
        assert result['state']=='MODEL_INPUT_NOT_CURRENT' and result['result'] is None
