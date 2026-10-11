# Security

Report suspected vulnerabilities privately to the maintainer at
`cherkaouihatim@gmail.com`, with affected versions, reproduction steps and impact.
Do not include live credentials or personal workflow data in public issues.

## Release checks

CI scans Java, JavaScript and GitHub Actions source with CodeQL, resolved Maven and
npm dependencies with OSV, and full Git history with Gitleaks. The release workflow
requires every test, artifact audit and security gate to pass before signing and
publishing. See [Publishing](PUBLISHING.md) for the exact coverage and prerequisites.

Dependency vulnerability findings are not suppressed. The secret scan exempts only
reviewed historical Compose demo-password fingerprints and shell variable references.
Those demo credentials were never suitable for production and must not be reused.

## Deployment boundaries

The orchestrator runs within the hosting application's security boundary. Protect
management/dashboard APIs with the application's authentication and authorization;
replay and Kafka inspection endpoints grant administrative capabilities. Restrict
network access to Kafka, databases and observability backends, and provide deployment
credentials through external secret configuration. The example Compose stack is a
local demonstration, not a hardened internet-facing deployment.

Workflow payloads and observability identifiers can contain business data. The hosting
application owns access control, redaction, retention and allowed outbound REST targets.
