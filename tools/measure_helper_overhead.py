#!/usr/bin/env python3
"""Measure a local helper without SSH credentials, installation, or configuration changes."""
import argparse
import json
import os
from pathlib import Path
import resource
import subprocess
import sys
import time

parser=argparse.ArgumentParser()
parser.add_argument('--samples',type=int,default=3)
args=parser.parse_args()
if not 1<=args.samples<=100: parser.error('--samples must be 1..100')
helper=Path(__file__).resolve().parents[1]/'app/src/main/assets/sentinel-monitor.py'
results=[]
for command in ('sentinel-status-v1','sentinel-processes-v2 all cpu user','sentinel-health-v1','sentinel-jobs-v1'):
    for _ in range(args.samples):
        before=resource.getrusage(resource.RUSAGE_CHILDREN)
        start=time.monotonic()
        completed=subprocess.run([sys.executable,'-I',str(helper)],env=dict(os.environ,SSH_ORIGINAL_COMMAND=command),capture_output=True,timeout=15)
        after=resource.getrusage(resource.RUSAGE_CHILDREN)
        results.append({'request':command,'elapsedSeconds':round(time.monotonic()-start,4),
                        'cpuSeconds':round(after.ru_utime+after.ru_stime-before.ru_utime-before.ru_stime,4),
                        'responseBytes':len(completed.stdout),'exitCode':completed.returncode})
print(json.dumps({'samples':results,'peakChildRssKiB':resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss,
                  'note':'Local helper only. Excludes SSH, phone, network and representative-device verification.'},indent=2))
