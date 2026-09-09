# Workflow Orchestrator Dashboard

Minimal React/Vite dashboard for the optional `workflow-orchestrator-service-starter`.

## Run

```bash
npm install
npm run dev
```

Set `VITE_API_URL` if the service API is not at `http://localhost:8080/api/orchestrator`.

The dashboard provides:
- pageable/filterable pipeline list
- pipeline detail view
- GitLab-like clickable step graph
- context and metadata tabs
- execution logs
- Kafka topics and consumer-group view

The backend service starter exposes the API; the frontend remains independent so applications can deploy it separately.
