"""One SDK session per bounded child process; only marked JSON is consumed."""
import json
import sys
from app.forecast.provider import ForecastSource
result=ForecastSource().collect(json.load(sys.stdin))
print('AISTOCK_FORECAST:'+json.dumps(result,separators=(',',':')))
