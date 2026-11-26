import json
import sys
from app.calendar.provider import CalendarSource
print('AISTOCK_CALENDAR:'+json.dumps(CalendarSource().collect(json.load(sys.stdin)),separators=(',',':')))
