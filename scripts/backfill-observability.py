#!/usr/bin/env python3
"""Restore pre-instrumentation completion measurements from durable FINISH audits.

Only correlation identifiers are exported; business inputs and SQL are excluded.
Existing completion telemetry is skipped and audit IDs make repeat runs idempotent.
"""
import argparse
import datetime
import json
import os
import subprocess
import urllib.request
import base64

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--before', required=True, help='Instrumentation rollout timestamp, ISO 8601 UTC')
parser.add_argument('--container', default='workflow-postgres')
parser.add_argument('--database', default='orchestrator')
parser.add_argument('--user', default='orchestrator')
parser.add_argument('--service', default='workflow-example-app')
parser.add_argument('--url', default='http://localhost:9200')
args = parser.parse_args()
cutoff = datetime.datetime.fromisoformat(args.before.replace('Z', '+00:00'))
if cutoff.tzinfo is None:
    parser.error('--before must include a timezone')
cutoff = cutoff.astimezone(datetime.timezone.utc).isoformat()
auth = base64.b64encode(('elastic:' + os.environ.get('ELASTIC_PASSWORD', 'adminpassword')).encode()).decode()

def request(path, body, method='POST', content_type='application/json'):
    raw = body.encode() if isinstance(body, str) else json.dumps(body).encode()
    req = urllib.request.Request(args.url.rstrip('/') + path, raw, method=method,
                                 headers={'Authorization': 'Basic ' + auth, 'Content-Type': content_type})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)

# Validated ISO timestamp and identifiers above are the only interpolated values.
sql = """
select json_build_object('auditId',l.id,'workflowId',w.pipeline_id,'workflow',w.workflow,
 'status',l.snapshot_json::jsonb->>'status','workflowStep',l.snapshot_json::jsonb->>'lastStep',
 '@timestamp',l.date_created,'durationMs',greatest(0,extract(epoch from(l.date_created-w.date_created))*1000),
 'metadata',coalesce(m.metadata_json::jsonb,'{}'::jsonb))::text
from workflow_log l join workflow w on w.pipeline_id=l.pipeline_id
left join workflow_metadata m on m.pipeline_id=w.pipeline_id
where l.action='FINISH' and l.date_created < '%s'::timestamptz
order by l.id
""" % cutoff
process = subprocess.run(['docker', 'exec', args.container, 'psql', '-U', args.user,
                          '-d', args.database, '-At', '-c', sql], capture_output=True, text=True,
                         check=True, timeout=30)
rows = [json.loads(line) for line in process.stdout.splitlines() if line.strip()]
created = skipped = 0
for row in rows:
    existing = request('/traces-orcas-*/_search', {'size': 0, 'query': {'bool': {'filter': [
        {'term': {'operation': 'workflow.completed'}}, {'term': {'workflowId': row['workflowId']}},
        {'bool': {'must_not': [{'term': {'measurementSource': 'jdbc.audit'}}]}}
    ]}}})
    if existing['hits']['total']['value']:
        skipped += 1
        continue
    metadata = row.pop('metadata')
    audit_id = row.pop('auditId')
    row.update({'operation': 'workflow.completed', 'eventType': 'Workflow',
                'serviceName': args.service, 'measurementSource': 'jdbc.audit',
                'outcome': {'SUCCESS': 'success', 'FAILED': 'failure'}.get(row['status'], 'unclassified')})
    for key in ['requestId', 'correlationId', 'transactionId', 'traceId']:
        if metadata.get(key):
            row[key] = metadata[key]
    payload = json.dumps({'create': {'_index': 'traces-orcas-default', '_id': 'orcas-audit-finish-' + row['workflowId'] + '-' + str(audit_id)}}) + '\n'
    payload += json.dumps(row) + '\n'
    result = request('/_bulk?refresh=wait_for', payload, content_type='application/x-ndjson')
    item = result['items'][0]['create']
    if item['status'] == 201:
        created += 1
    elif item['status'] == 409:
        skipped += 1
    else:
        raise RuntimeError(item)
print(f'Imported {created} completion measurements; skipped {skipped} existing measurements.')
