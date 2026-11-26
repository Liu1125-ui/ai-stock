"""Protocol-only fixtures: no trained model, cloud call, or live stock data."""
import json,threading
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.inference import http,publication

OP='11111111-1111-4111-8111-111111111111'
BUNDLE='22222222-2222-4222-8222-222222222222'
HEADERS={'X-Service-Token':'private-test','X-Market-Use-Bundle':BUNDLE,'X-Market-Use-Owner':'prediction','X-Market-Use-Purpose':'FORECAST','X-Market-Use-Operation':OP,'X-Market-Use-Generation':'1'}
BODY={'modelId':OP,'artifactId':'transformer-s3-2023-seed42','manifestHash':'a'*64,'instrumentId':'SH600030','inputVersionId':BUNDLE}

def setup_env(monkeypatch):
    monkeypatch.setenv('SERVICE_TOKEN','private-test');monkeypatch.setenv('PREDICTION_MODEL_AUTHORITY_URL','http://127.0.0.1:1');monkeypatch.setenv('MARKET_INPUT_AUTHORITY_URL','http://127.0.0.1:1');monkeypatch.setenv('MARKET_VERSION_USE_REQUIRED','true')

def test_required_usage_is_checked_before_worker_and_authentication_precedes_it(monkeypatch):
    setup_env(monkeypatch);calls=[];monkeypatch.setattr(http,'run_worker',lambda body:calls.append(body) or {'state':'fixture','result':None})
    c=TestClient(app)
    assert c.post('/internal/v1/infer',json=BODY).status_code==401
    assert c.post('/internal/v1/infer',json=BODY,headers={'X-Service-Token':'private-test'}).status_code==422
    assert not calls

@pytest.mark.parametrize('key,value',[('X-Market-Use-Purpose','STOCK_ANALYSIS'),('X-Market-Use-Generation','0'),('X-Market-Use-Bundle','bad'),('X-Market-Use-Owner','notification')])
def test_wrong_usage_never_starts_worker(monkeypatch,key,value):
    setup_env(monkeypatch);calls=[];monkeypatch.setattr(http,'run_worker',lambda body:calls.append(body) or {})
    assert TestClient(app).post('/internal/v1/infer',json=BODY,headers={**HEADERS,key:value}).status_code==422
    assert not calls

def test_canonical_usage_is_passed_to_internal_worker_and_duplicate_headers_rejected(monkeypatch):
    setup_env(monkeypatch);seen=[];monkeypatch.setattr(http,'run_worker',lambda body:seen.append(body) or {'state':'fixture','result':None})
    c=TestClient(app);assert c.post('/internal/v1/infer',json=BODY,headers=HEADERS).status_code==200
    assert seen[0]['_marketUse']=={k:v for k,v in HEADERS.items() if k!='X-Service-Token'}
    duplicate=list(HEADERS.items())+[('X-Market-Use-Generation','2')]
    assert c.post('/internal/v1/infer',json=BODY,headers=duplicate).status_code==422
    assert len(seen)==1

def test_offline_inspection_does_not_require_or_accept_injected_business_context(monkeypatch):
    setup_env(monkeypatch);calls=[];monkeypatch.setattr(http,'run_worker',lambda body:calls.append(body) or {'state':'fixture','result':None})
    body={'artifactId':BODY['artifactId'],'manifestHash':BODY['manifestHash']};c=TestClient(app)
    assert c.post('/internal/v1/model-artifacts/inspect',json=body,headers={'X-Service-Token':'private-test'}).status_code==200
    assert '_marketUse' not in calls[0]
    assert c.post('/internal/v1/infer',json={**BODY,'_marketUse':{}},headers=HEADERS).status_code==422

def test_only_market_reads_receive_credentials_and_both_reads_use_same_identity(monkeypatch):
    setup_env(monkeypatch);hits=[]
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*a):pass
        def do_GET(self):
            hits.append((self.path,self.headers.get('X-Market-Use-Bundle')))
            if self.path.endswith('/publication'):data={'modelId':OP,'artifactId':BODY['artifactId'],'manifestHash':'a'*64,'checkpointHash':'b'*64,'publicationRevision':1}
            elif 'forecast-raw-window' in self.path:data={'instrumentId':'SH600030','state':'AVAILABLE','provider':'baostock','priceBasis':'RAW','versionId':OP,'rawContentHash':'c'*64}
            else:data={'instrumentId':'SH600030','inputVersionId':BUNDLE,'freshness':'FRESH','rawVersionId':OP,'rawContentHash':'c'*64}
            raw=json.dumps({'code':'OK','data':data}).encode();self.send_response(200);self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
    server=ThreadingHTTPServer(('127.0.0.1',0),Handler);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    monkeypatch.setenv('PREDICTION_MODEL_AUTHORITY_URL',f'http://127.0.0.1:{server.server_port}');monkeypatch.setenv('MARKET_INPUT_AUTHORITY_URL',f'http://127.0.0.1:{server.server_port}')
    packet={**BODY,'_marketUse':{k:v for k,v in HEADERS.items() if k!='X-Service-Token'}}
    try:
        publication.fetch_publication(packet);publication.fetch_window(packet)
        assert [value for _,value in hits]==[None,BUNDLE,BUNDLE]
    finally:server.shutdown();server.server_close();thread.join(timeout=2)
