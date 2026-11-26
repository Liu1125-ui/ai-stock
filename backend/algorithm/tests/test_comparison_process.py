"""Real child-process limits; no SDK/network calls."""
import subprocess
import sys
import pytest
from app.comparison.process import run_bounded_worker

@pytest.mark.parametrize("channel",["stdout","stderr"])
def test_child_output_is_stopped_while_running(channel):
    command=[sys.executable,"-c",f"import sys; s=sys.{channel}.buffer\nwhile True: s.write(b'x'*8192); s.flush()"]
    with pytest.raises(ValueError,match="limit"):
        run_bounded_worker({},command=command,limit=32768,timeout=2)

def test_silent_child_is_reaped_on_deadline():
    with pytest.raises(subprocess.TimeoutExpired):
        run_bounded_worker({},command=[sys.executable,"-c","import time; time.sleep(5)"],timeout=.1)

def test_small_output_returns_exactly_and_nonzero_exit_fails():
    assert run_bounded_worker({},command=[sys.executable,"-c","print('ok')"]).strip()=="ok"
    with pytest.raises(ValueError):
        run_bounded_worker({},command=[sys.executable,"-c","raise SystemExit(2)"])
