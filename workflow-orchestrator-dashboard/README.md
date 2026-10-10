# Dashboard

A separate React application for browsing workflow executions, steps, context, metadata, logs and Kafka activity.

It is intentionally not a Maven artifact. The dashboard talks to the `dashboard-service` and operational service HTTP endpoints supplied by the Java modules.

Build it with:

```bash
npm ci
npm run build
```
