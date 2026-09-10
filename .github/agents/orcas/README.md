Orcas — Orchestrator Architector agent

Purpose

This agent provides authoritative guidance about the workflow-orchestrator project: architecture, where to find code, common change patterns, and commands to build/run/test. It is intended to be discovered by GitHub Copilot/Agents UI when placed under .github/agents/.

Files included

- agent.yaml — agent manifest used by GitHub Agents
- context.md — embedded context the agent uses to answer questions without re-reading files
- README.md — this file

Usage

- The agent is read-only guidance; for exact code edits request file reads or grant permission to modify specific files.
- To run or test the project follow commands in context.md (Build: mvn -DskipTests package; Run example: mvn -pl example-app spring-boot:run; Dashboard dev: cd workflow-orchestrator-dashboard && npm run dev)

Notes

- This agent manifest is intentionally minimal. Update agent.yaml to add commands, entrypoints, or links to external services if needed.
