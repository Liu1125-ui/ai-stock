"""Read business-owned publication and frozen input; never trusts caller-supplied prices or status."""
import json,os,re
from urllib.error import HTTPError
from urllib.request import Request,build_opener,HTTPRedirectHandler

class PublicationFailure(Exception):
    def __init__(self,code):self.code=code;super().__init__(code)

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):return None


def read_business(base,path,unavailable,not_found=None,market_use=None):
    token=os.environ.get('SERVICE_TOKEN','')
    if not token or not base:raise PublicationFailure(unavailable)
    try:
        request=Request(base.rstrip('/')+path,headers={'X-Service-Token':token,'Accept':'application/json',**(market_use or {})})
        with build_opener(NoRedirect()).open(request,timeout=3) as response:
            raw=response.read(65537)
            if len(raw)>65536:raise PublicationFailure(unavailable)
            payload=json.loads(raw)
            if not isinstance(payload,dict) or payload.get('code')!='OK' or not isinstance(payload.get('data'),dict):raise PublicationFailure(unavailable)
            return payload['data']
    except HTTPError as error:
        code=not_found if not_found and error.code in (404,409) else unavailable
        raise PublicationFailure(code) from None
    except PublicationFailure:raise
    except (OSError,ValueError,TypeError,RecursionError):raise PublicationFailure(unavailable) from None


def fetch_publication(body):
    data=read_business(os.environ.get('PREDICTION_MODEL_AUTHORITY_URL',''),'/internal/v1/models/'+body['modelId']+'/publication','MODEL_PUBLICATION_UNAVAILABLE','MODEL_NOT_PUBLISHED')
    if (set(data)!={'modelId','artifactId','manifestHash','checkpointHash','publicationRevision'}
            or any(data.get(key)!=body[key] for key in ('modelId','artifactId','manifestHash'))
            or not isinstance(data.get('checkpointHash'),str) or not re.fullmatch('[a-f0-9]{64}',data['checkpointHash'])
            or type(data.get('publicationRevision')) is not int or not 1<=data['publicationRevision']<=9223372036854775807):
        raise PublicationFailure('MODEL_PUBLICATION_MISMATCH')
    return data


def fetch_window(body):
    from .market_use import validate_usage
    try:usage=validate_usage(body.get('_marketUse'))
    except (ValueError,TypeError,AttributeError):raise PublicationFailure('MODEL_MARKET_USE_INVALID') from None
    # Both values are canonical, validated IDs; the host/path never comes from the request.
    path='/internal/v1/market/forecast-window?instrumentId='+body['instrumentId']+'&inputVersionId='+body['inputVersionId']
    data=read_business(os.environ.get('MARKET_INPUT_AUTHORITY_URL',''),path,'MODEL_INPUT_UNAVAILABLE',market_use=usage)
    if data.get('instrumentId')!=body['instrumentId'] or data.get('inputVersionId')!=body['inputVersionId']:
        raise PublicationFailure('MODEL_INPUT_INCOMPATIBLE')
    if data.get('freshness')!='FRESH':raise PublicationFailure('MODEL_INPUT_NOT_CURRENT')
    # Both reads forward the same usage: the market pins its bound RAW, not a new live head.
    head=read_business(os.environ.get('MARKET_INPUT_AUTHORITY_URL',''),'/internal/v1/market/forecast-raw-window?instrumentId='+body['instrumentId'],'MODEL_INPUT_UNAVAILABLE',market_use=usage)
    if (head.get('instrumentId')!=body['instrumentId'] or head.get('state')!='AVAILABLE'
            or head.get('provider')!='baostock' or head.get('priceBasis')!='RAW'):
        raise PublicationFailure('MODEL_INPUT_INCOMPATIBLE')
    if (not isinstance(data.get('rawVersionId'),str) or not isinstance(data.get('rawContentHash'),str)
            or head.get('versionId')!=data['rawVersionId'] or head.get('rawContentHash')!=data['rawContentHash']):
        raise PublicationFailure('MODEL_INPUT_NOT_CURRENT')
    return data
