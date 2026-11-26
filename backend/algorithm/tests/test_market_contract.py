from app.market.collector import run
from app.market.providers import BaostockProvider
from test_market_provider import SDK

def test_collector_includes_operation_and_no_candidate_on_failure():
    result=run('job-1',BaostockProvider(SDK(error='9')),'SH600030','2026-09-01','2026-09-24')
    assert result['operationId']=='job-1'; assert result['candidate'] is None; assert result['errorCode']=='SOURCE_INVALID'
