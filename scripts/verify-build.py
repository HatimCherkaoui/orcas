#!/usr/bin/env python3
"""Run independent verification tasks concurrently with bounded time and fail fast."""
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

root = Path(__file__).resolve().parent.parent
threads = os.environ.get('MAVEN_THREADS', '2')
timeout = int(os.environ.get('VERIFY_TIMEOUT_SECONDS', '900'))
commands = [
    ('Maven', ['mvn', '--batch-mode', '--no-transfer-progress', '--fail-fast',
               '-T', threads, 'verify'], root),
    ('Dashboard', ['bash', '-euc',
                  'npm ci --no-audit --no-fund --prefer-offline --fetch-retries=1 '
                  '--fetch-timeout=30000; npm test; npm run build'],
     root / 'workflow-orchestrator-dashboard'),
]
processes = []
started = time.monotonic()
try:
    for name, command, cwd in commands:
        print(f'Starting {name}: {" ".join(command)}', flush=True)
        processes.append((name, subprocess.Popen(command, cwd=cwd, start_new_session=True)))
    pending = list(processes)
    while pending:
        for name, process in pending[:]:
            code = process.poll()
            if code is not None:
                pending.remove((name, process))
                if code:
                    print(f'{name} failed with exit {code}; stopping other tasks.', file=sys.stderr)
                    sys.exit(code if code > 0 else 1)
                print(f'{name} passed.', flush=True)
        if time.monotonic() - started > timeout:
            print(f'Verification exceeded {timeout}s; stopping tasks.', file=sys.stderr)
            sys.exit(124)
        time.sleep(0.2)
except KeyboardInterrupt:
    sys.exit(130)
finally:
    for _, process in processes:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
    for _, process in processes:
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
print(f'Verification passed in {time.monotonic() - started:.1f}s.')
