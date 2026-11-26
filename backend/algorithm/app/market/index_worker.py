"""Run the read-only index collection inside the parent's deadline."""

import json
import sys

from .index_source import IndexSource


def main():
    try:
        command = json.loads(sys.stdin.read(4097))
        result = IndexSource().collect(command)
    except Exception:
        result = IndexSource.error("SOURCE_INVALID")
    print("AISTOCK_INDEX:" + json.dumps(result, separators=(",", ":")))


if __name__ == "__main__":
    main()
