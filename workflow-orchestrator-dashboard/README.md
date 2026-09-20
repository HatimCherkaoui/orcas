# Orcas Workflow Orchestrator Dashboard

Compact React/Vite operations dashboard for the `workflow-orchestrator-service-starter`.

## API contract

The dashboard uses the service starter routes directly under `/api/orchestrator`:

- `GET /workflows`
- `GET /workflows/{workflowId}`
- `GET /workflows/definitions/{workflow}`
- `GET /workflows/{workflowId}/steps`
- `GET /workflows/{workflowId}/steps/{stepName}`
- `GET /workflows/{workflowId}/steps/{stepName}/context`
- `GET /workflows/{workflowId}/context`
- `GET /workflows/{workflowId}/metadata`
- `GET /workflows/{workflowId}/logs`
- `GET /workflows/{workflowId}/steps/{stepName}/logs`
- `POST /workflows/{workflowId}/steps/{stepName}/replay`
- `POST /workflows/replays/suspended`
- `GET /kafka/topics`
- `GET /kafka/topics/{name}`
- `GET /kafka/consumer-groups`
- `GET /kafka/consumer-groups/{groupId}`

The client defaults to the same-origin `/api/orchestrator`, which works with the Vite development proxy and the provided nginx reverse proxy. Set `VITE_API_URL` only when the service is hosted elsewhere.

API failures are surfaced with the HTTP status and exact endpoint instead of being swallowed by the UI.

## Run

```bash
npm ci
npm run dev
```

Build:

```bash
npm run build
```

## Dashboard UX

- GitHub/GitLab-inspired compact navigation with Orcas SVG branding.
- Roboto typography and black/white/ocean-blue visual system.
- Workflow list with floating-label filters, live refresh, adaptive page size, sortable columns and compact pagination.
- Workflow rows open the execution graph directly.
- React Flow graph with status-aware nodes, automatic layout, zoom/pan and minimap.
- Context, metadata, audit logs and step details open as closable side panels so the graph remains the primary surface.
- Failed/suspended steps can be replayed without leaving the graph.
- Kafka topics and consumer groups use the same compact dashboard language, with detail panels instead of page expansion.
- Refreshing resources keeps the previous data visible while the next request is running, avoiding dashboard flicker.
