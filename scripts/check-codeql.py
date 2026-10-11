#!/usr/bin/env python3
"""Fail the release gate for CodeQL security findings, not just scanner errors."""
import json
from pathlib import Path
import sys

files = list(Path(sys.argv[1]).rglob('*.sarif'))
if not files:
    raise SystemExit('CodeQL produced no SARIF reports.')
findings = []
for file in files:
    for run in json.loads(file.read_text()).get('runs', []):
        rules = {r['id']: r for r in run['tool']['driver'].get('rules', [])}
        for result in run.get('results', []):
            rule = rules.get(result.get('ruleId'), {})
            if 'security-severity' in rule.get('properties', {}):
                findings.append((file.name, result.get('ruleId'), result['message'].get('text')))
for file, rule, message in findings:
    print(f'{file}: {rule}: {message}')
if findings:
    raise SystemExit(f'{len(findings)} CodeQL security findings must be resolved before release.')
print(f'CodeQL security gate passed ({len(files)} reports).')
