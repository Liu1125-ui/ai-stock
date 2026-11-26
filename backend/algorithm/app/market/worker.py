"""Short lived Baostock collection worker; parent owns the deadline."""
import json
import sys
from .providers import BaostockProvider
from .collector import run

def main():
    try:
        command=json.loads(sys.stdin.read(4096))
        if command.get('provider')!='baostock':
            raise ValueError('unsupported provider')
        result=run(command.get('operationId'),BaostockProvider(),command.get('instrumentId'),command.get('start'),command.get('end'))
    except Exception:
        result=dict(operationId=None,state='SOURCE_INVALID',candidate=None,errorCode='SOURCE_INVALID',retryAt=None)
    sys.stdout.write('AISTOCK_CANDIDATE:'+json.dumps(result,separators=(',',':'),ensure_ascii=False)+'\n')

if __name__=='__main__': main()
