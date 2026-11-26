"""One bounded SDK session; only marked JSON is consumed."""
import json
import sys
from app.comparison.provider import ComparisonSource

result = ComparisonSource().collect(json.load(sys.stdin))
print("AISTOCK_COMPARISON:" + json.dumps(result, separators=(",", ":")))