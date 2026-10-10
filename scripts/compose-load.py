#!/usr/bin/env python3
"""Load the Compose order/payment workflows and verify their durable business results."""
import argparse, base64, concurrent.futures, datetime, json, math, os, pathlib, subprocess, threading, time, urllib.request, uuid
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--stages', default='50:10,200:25', help='orders:concurrent clients')
p.add_argument('--sku', help='Use existing stock instead of creating an isolated benchmark SKU')
p.add_argument('--timeout', type=int, default=300)
p.add_argument('--url', default='http://localhost:8080')
p.add_argument('--verify-traces', action='store_true', help='Require one native Elasticsearch completion per workflow')
p.add_argument('--elasticsearch-url', default='http://localhost:9200')
p.add_argument('--output', default='/private/tmp/orcas-compose-prod-load.json')
a=p.parse_args()
run='load-'+uuid.uuid4().hex
report={'run':run,'started':datetime.datetime.now(datetime.timezone.utc).isoformat(),'stages':[],'resources':[]}
stop=threading.Event()
def cmd(argv):
 return subprocess.run(argv,check=True,capture_output=True,text=True,timeout=30).stdout
def sql(query):
 return [json.loads(x) for x in cmd(['docker','exec','workflow-postgres','psql','-U','orchestrator','-d','orchestrator','-At','-c',query]).splitlines() if x.strip()]
def monitor():
 while not stop.is_set():
  try: report['resources'].append({'at':time.time(),'containers':[json.loads(x) for x in cmd(['docker','stats','--no-stream','--format','{{json .}}']).splitlines()]})
  except Exception as e: report['resources'].append({'error':str(e)})
  stop.wait(10)
def percentiles(values):
 v=sorted(values)
 return {} if not v else {'mean_s':sum(v)/len(v),**{f'p{q}_s':v[max(0,math.ceil(q*len(v)/100)-1)] for q in (50,95,99)},'max_s':v[-1]}
def post(item):
 path,body,rid=item; start=time.monotonic()
 try:
  req=urllib.request.Request(a.url+path,json.dumps(body).encode(),headers={'Content-Type':'application/json','X-Request-Id':rid,'X-Correlation-Id':run},method='POST')
  with urllib.request.urlopen(req,timeout=65) as r: return {'requestId':rid,'status':r.status,'seconds':time.monotonic()-start}
 except Exception as e: return {'requestId':rid,'error':str(e),'seconds':time.monotonic()-start}
def leg(items,concurrency,prefix,label):
 start=time.monotonic()
 with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool: launches=list(pool.map(post,items))
 launch_seconds=time.monotonic()-start; deadline=time.monotonic()+a.timeout; rows=[]
 while time.monotonic()<deadline:
  rows=sql("select json_build_object('id',w.pipeline_id,'status',w.status,'seconds',extract(epoch from(w.date_updated-w.date_created)),'orderId',m.metadata_json::jsonb->>'orderId') from workflow w join workflow_metadata m using(pipeline_id) where m.metadata_json::jsonb->>'requestId' like '%s%%'"%prefix)
  print(json.dumps({'leg':label,'observed':len(rows),'statuses':{s:sum(r['status']==s for r in rows) for s in sorted({r['status'] for r in rows})},'elapsed_s':round(time.monotonic()-start,2)}),flush=True)
  if len(rows)==len(items) and all(r['status'] in ('SUCCESS','FAILED') for r in rows): break
  time.sleep(3)
 elapsed=time.monotonic()-start
 passed=len(rows)==len(items) and all(r['status']=='SUCCESS' for r in rows) and all(r.get('status')==202 for r in launches)
 result={'label':label,'requests':len(items),'concurrency':concurrency,'passed':passed,'elapsed_s':elapsed,'launch_s':launch_seconds,'workflows_per_second':len(rows)/elapsed,'http_latency':percentiles([r['seconds'] for r in launches]),'workflow_latency':percentiles([float(r['seconds']) for r in rows if r['status']=='SUCCESS']),'launches':launches,'workflows':rows}
 report['stages'].append(result)
 if not passed: raise RuntimeError(label+' did not produce all expected successful workflows')
 return rows
thread=threading.Thread(target=monitor,daemon=True); thread.start()
try:
 report['environment']=json.loads(cmd(['docker','info','--format','{"cpus":{{.NCPU}},"memory_bytes":{{.MemTotal}},"architecture":"{{.Architecture}}"}']))
 customer=sql('select row_to_json(c) from customers c order by id limit 1')[0]['id']
 stages=[tuple(map(int,stage.split(':'))) for stage in a.stages.split(',')]
 if any(min(n,c)<1 for n,c in stages): raise ValueError('Stages must be positive')
 required=sum(n for n,c in stages)
 sku=a.sku or 'PERF-'+uuid.uuid4().hex
 if a.sku:
  if not all(ch.isalnum() or ch in '-_' for ch in sku): raise ValueError('SKU must contain letters, digits, hyphens or underscores')
  stock=sql("select json_build_object('quantity',quantity) from inventory where sku='%s'"%sku)
  if not stock or stock[0]['quantity']<required: raise RuntimeError('Not enough inventory for configured stages')
 else:
  sql("with seeded as (insert into inventory(product_name,quantity,sku) values ('Orcas load fixture',%d,'%s') returning *) select json_build_object('sku',sku) from seeded"%(required,sku))
 report['fixture']={'sku':sku,'customer_id':customer,'initial_quantity':required}
 for index,stage in enumerate(a.stages.split(',')):
  n,c=map(int,stage.split(':'))
  if min(n,c)<1: raise ValueError('Stages must be positive')
  prefix=f'{run}-{index}-order-'
  rows=leg([('/orders',{'customerId':customer,'items':[{'sku':sku,'quantity':1,'unitPrice':12345.01}]},prefix+str(i)) for i in range(n)],c,prefix,f'{n} orders')
  ids=[int(r['orderId']) for r in rows]
  if len(set(ids))!=n: raise RuntimeError('Missing or duplicate order IDs')
  in_ids=','.join(map(str,ids))
  orders=sql("select json_build_object('id',o.id,'paymentId',p.provider_payment_id) from orders o join payments p on p.order_id=o.id where o.id in ("+in_ids+")")
  if len(orders)!=n: raise RuntimeError('Successful workflows missing order/payment records')
  prefix=f'{run}-{index}-callback-'
  leg([('/payments/callback/success',{'orderId':o['id'],'paymentId':o['paymentId']},prefix+str(i)) for i,o in enumerate(orders)],c,prefix,f'{n} payment callbacks')
  business=sql("select json_build_object('orders',count(*),'confirmed_orders',count(*) filter(where o.status='CONFIRMED'),'confirmed_payments',count(*) filter(where p.status='CONFIRMED')) from orders o join payments p on p.order_id=o.id where o.id in ("+in_ids+")")[0]
  report['stages'][-1]['business_verification']=business
  if business['confirmed_orders']!=n or business['confirmed_payments']!=n: raise RuntimeError('Business verification failed')
 if a.verify_traces:
  expected={w['id'] for stage in report['stages'] for w in stage['workflows']}
  query={'size':len(expected)+1,'_source':['workflowId','durationMs','outcome'],'query':{'bool':{'filter':[{'term':{'correlationId':run}},{'term':{'operation':'workflow.completed'}}]}}}
  auth=base64.b64encode(('elastic:'+os.environ.get('ELASTIC_PASSWORD','adminpassword')).encode()).decode()
  deadline=time.monotonic()+30; spans=[]
  while time.monotonic()<deadline:
   req=urllib.request.Request(a.elasticsearch_url.rstrip('/')+'/traces-orcas-*/_search',json.dumps(query).encode(),headers={'Content-Type':'application/json','Authorization':'Basic '+auth})
   with urllib.request.urlopen(req,timeout=30) as response: result=json.load(response)
   spans=[hit['_source'] for hit in result['hits']['hits']]
   if {span['workflowId'] for span in spans}==expected: break
   time.sleep(2)
  report['completion_traces']={'expected':len(expected),'documents':result['hits']['total']['value'],'unique_workflows':len({span['workflowId'] for span in spans})}
  if len(spans)!=len(expected) or {span['workflowId'] for span in spans}!=expected or any(span.get('outcome')!='success' for span in spans): raise RuntimeError('Missing, duplicate or unsuccessful Elasticsearch completion traces')
  for stage in report['stages']:
   ids={w['id'] for w in stage['workflows']}
   stage['completion_trace_latency']=percentiles([float(span['durationMs'])/1000 for span in spans if span['workflowId'] in ids])
 report['passed']=True
except Exception as e: report['passed']=False; report['error']=str(e)
finally:
 stop.set(); thread.join(timeout=35)
 report['finished']=datetime.datetime.now(datetime.timezone.utc).isoformat()
 pathlib.Path(a.output).write_text(json.dumps(report,indent=2))
 print(json.dumps({'passed':report['passed'],'report':a.output,'error':report.get('error')}),flush=True)
if not report['passed']: raise SystemExit(1)
