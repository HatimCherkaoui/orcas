# UI Dashboard Developer Rules & Constraints

These rules are enforced unconditionally whenever working on the user interface and dashboard modules of the Workflow Orchestrator project.

## 1. Dynamic Instruction Re-evaluation
- Whenever the user instructs you to "re-evaluate instructions", "check alignment", or "review directives", you MUST pause active generation, compare new user requirements against current rules, summarize the updated priorities, and proceed with confirmed alignment.

## 2. Hard Workspace Boundary
- Strictly modify files in `workflow-orchestrator-dashboard/` only.
- Do NOT touch Java backend files (`workflow-orchestrator-core/`, `workflow-orchestrator-service/`, `workflow-orchestrator-management-service/`), Maven POMs, or Spring Boot classes.

## 3. Autonomous Mocking
- Never block on absent backend services. Use the built-in mock server (`npm run mock` or `npm run dev:mock`) located in `workflow-orchestrator-dashboard/scripts/mock-server.mjs`.

## 4. Visual Preview & Chrome DevTools Verification
- Preview UI work via browser inspection or Chrome DevTools tools before concluding any major UI change.
- Verify responsiveness across Desktop (1440px), Tablet (768px), and Mobile (375px).

## 5. Design System & Aesthetics
- Base Theme: **Dark Blue** (`#07192f`, `#0b172a`), **Ocean Blue** (`#0284c7`, `#0ea5e9`), and **White** (`#ffffff`).
- Semantic State Colors: Emerald Green for SUCCESS, Vibrant Ocean Blue pulse for RUNNING, Crimson Red for FAILED, Warm Amber for SUSPENDED, and Slate for SKIPPED/CANCELLED.
- No cluttered, unnecessary screens. Design the interface as a purpose-built desktop monitoring app with contained viewport heights (`100dvh`), sliding inspector drawers, sortable humanized tables, reactive DAG graphs, and modern informative error pages.
