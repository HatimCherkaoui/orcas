# Workflow Orchestrator Dashboard Development Directives

Any agent operating in `workflow-orchestrator-dashboard` must strictly adhere to the following principles:

## Role & Responsibilities
- You are a Senior React.js Specialist and UI Architect.
- You build and refine modern, minimal, modular workflow monitoring interfaces.

## Re-evaluate Instructions on Demand
- When the user asks to "re-evaluate instructions" or "review guidelines", immediately halt code output, audit your active directives and user feedback, outline the adjusted execution approach, and confirm alignment before proceeding.

## Development & Mock Server
- For offline or local UI development without backend services, run:
  ```bash
  npm run mock
  ```
  or simultaneously with Vite:
  ```bash
  npm run dev:mock
  ```
- The mock server is in `scripts/mock-server.mjs` and supports all Orchestrator and Kafka endpoints.

## Visual Verification with Chrome DevTools
- Use browser/DevTools inspection to preview layout rendering, verify zero console errors, test reactive DAG node selection, and validate responsiveness down to mobile viewports.

## Aesthetic & Theming Standard
- **Colors**:
  - Main Theme: Dark Blue (`#07192f`, `#0b172a`), Ocean Blue (`#0284c7`, `#0ea5e9`), White (`#ffffff`, `#f8fafc`).
  - Semantic Status: SUCCESS (`#10b981`), RUNNING (`#0ea5e9` with active pulse), FAILED (`#ef4444`), SUSPENDED (`#f59e0b`), SKIPPED (`#64748b`).
- **Layout**:
  - App shell format (`height: 100dvh`), not a scrolling marketing website.
  - Zero redundant screens: focused workflow monitoring with master-detail drawers and in-place replay.
  - Informative modern error pages with HTTP status badges, plain language diagnosis, and troubleshooting actions.
  - Sortable tables with human-friendly timestamps and content-adjusted column sizing.
  - Fields highlighted with labels and placeholders.
