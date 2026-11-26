"""Prepare controlled historical market candidates without modifying the source database."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'backend'/'algorithm'))
from app.market.prepare import main
if __name__=='__main__': main()
