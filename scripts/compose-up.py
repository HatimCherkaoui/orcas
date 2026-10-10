#!/usr/bin/env python3
"""Build and start Compose with bounded waits and immediate failure propagation."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

root = Path(__file__).resolve().parent.parent
profile = sys.argv[1] if len(sys.argv) > 1 else 'prod'
if profile not in ('dev', 'prod'):
    sys.exit('Usage: python3 scripts/compose-up.py [dev|prod]')
build_timeout = int(os.environ.get('COMPOSE_BUILD_TIMEOUT_SECONDS', '450'))
startup_timeout = int(os.environ.get('COMPOSE_START_TIMEOUT_SECONDS', '600'))
base = ['docker', 'compose', '--profile', profile]
steps = [(['docker', 'buildx', 'version'], 15),
         (base + ['config', '--quiet'], 15),
         (base + ['build'], build_timeout),
         (base + ['up', '-d', '--no-build'], startup_timeout)]
for command, limit in steps:
    started = time.monotonic()
    print('Running:', ' '.join(command), flush=True)
    process = subprocess.Popen(command, cwd=root, start_new_session=True)
    code = 1
    try:
        code = process.wait(timeout=limit)
    except subprocess.TimeoutExpired:
        print(f'Command exceeded {limit}s; cancelling.', file=sys.stderr)
        code = 124
    except KeyboardInterrupt:
        code = 130
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
    print(f'Finished in {time.monotonic()-started:.1f}s, exit {code}', flush=True)
    if code:
        sys.exit(code if code > 0 else 1)

# Compose --wait treats a completed one-shot importer as a failed running service.
# Require successful initialization separately from long-running readiness.
expected = set(subprocess.check_output(base + ['config', '--services'], cwd=root,
                                       text=True, timeout=15).split())
initializers = {'es-setup', 'kibana-init'}
deadline = started + startup_timeout
while True:
    try:
        raw = subprocess.check_output(base + ['ps', '--all', '--format', 'json'],
                                      cwd=root, text=True, timeout=15).strip()
        rows = json.loads(raw) if raw.startswith('[') else [json.loads(line) for line in raw.splitlines()]
    except (subprocess.SubprocessError, json.JSONDecodeError) as error:
        sys.exit(f'Cannot inspect Compose readiness: {error}')
    states = {row['Service']: row for row in rows}
    pending = []
    for service in expected:
        row = states.get(service, {})
        state, health = row.get('State', ''), row.get('Health', '')
        if service in initializers and state == 'exited':
            if row.get('ExitCode', 1) != 0:
                sys.exit(f'Initializer {service} failed: exit {row.get("ExitCode")}')
            continue
        if state in ('exited', 'dead', 'restarting') or health == 'unhealthy':
            sys.exit(f'Service {service} failed: state={state}, health={health}')
        if service in initializers or state != 'running' or health not in ('', 'healthy'):
            pending.append(service)
    if not pending:
        print('All services ready; initialization completed successfully.', flush=True)
        break
    if time.monotonic() >= deadline:
        sys.exit(f'Startup exceeded {startup_timeout}s; waiting for {", ".join(pending)}')
    print('Waiting for: ' + ', '.join(sorted(pending)), flush=True)
    time.sleep(3)
