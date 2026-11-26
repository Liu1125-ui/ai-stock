"""Bounded pipe reads for the Linux algorithm container; no full-output buffering."""
import json
import os
import selectors
import subprocess
import sys
import time

def run_bounded_worker(body, *, command=None, limit=2097152, timeout=25):
    if limit<1 or timeout<=0:
        raise ValueError("Invalid worker limits")
    payload=json.dumps(body).encode("utf-8")
    if len(payload)>4096:
        raise ValueError("Worker input limit")
    command=command or [sys.executable,"-m","app.comparison.worker"]
    process=subprocess.Popen(command,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    output=bytearray()
    total=0
    deadline=time.monotonic()+timeout
    try:
        process.stdin.write(payload)
        process.stdin.close()
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout,selectors.EVENT_READ,"stdout")
            selector.register(process.stderr,selectors.EVENT_READ,"stderr")
            while selector.get_map():
                remaining=deadline-time.monotonic()
                if remaining<=0:
                    raise subprocess.TimeoutExpired(command,timeout)
                for key,_ in selector.select(min(remaining,.1)):
                    chunk=os.read(key.fd,8192)
                    if not chunk:
                        selector.unregister(key.fileobj)
                        continue
                    total+=len(chunk)
                    if total>limit:
                        raise ValueError("Worker output limit exceeded")
                    if key.data=="stdout":
                        output.extend(chunk)
        remaining=deadline-time.monotonic()
        if remaining<=0:
            raise subprocess.TimeoutExpired(command,timeout)
        if process.wait(timeout=remaining)!=0:
            raise ValueError("Worker failed")
        return output.decode("utf-8")
    finally:
        if process.poll() is None:
            process.kill()
        process.wait()
        for stream in (process.stdin,process.stdout,process.stderr):
            if stream and not stream.closed:
                stream.close()