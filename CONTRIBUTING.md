# Contributing

Use JDK 25 or 26 and Maven 3.9.6+. The dashboard uses Node.js 20+.
Docker must be running for integration tests; [Colima setup](example-app/TESTCONTAINERS.md)
explains the Testcontainers socket configuration.

```bash
./verify.sh
```

The build checks all Maven modules and the dashboard. Run focused module tests while
editing, then use the complete verification before submitting a pull request.
The [architecture](docs/architecture.md) describes dependency boundaries. Core and public
API modules must remain independent of their infrastructure adapters. Put Java sources,
resources and tests under the standard Maven `src` directories.

Describe the problem, resulting behavior and relevant validation in your pull request.
Report reproducible defects through [GitHub issues](https://github.com/HatimCherkaoui/orcas/issues).
Keep credentials, generated outputs, local editor state and agent instructions out of commits.
