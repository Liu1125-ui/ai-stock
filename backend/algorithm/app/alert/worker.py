"""One historical-only SDK session per bounded child process."""
import json
import sys
from app.alert.provider import AlertSource
print('AISTOCK_ALERT:'+json.dumps(AlertSource().collect(json.load(sys.stdin)),separators=(',',':')))
