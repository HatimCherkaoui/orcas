# Publishing

The project is structured as a Maven multi-module library. The BOM and feature starters are published with the same version.

## Before a release

Run the complete build on Java 25 and Maven 3.9.6+:

```bash
mvn clean verify
mvn -pl example-app -am verify
cd workflow-orchestrator-dashboard && npm ci && npm run build
```

Check:

- every public API module has no Spring or infrastructure dependency unless it owns that concern;
- all tests pass, including Testcontainers integration tests;
- sources and Javadocs are attached;
- artifacts carry the Apache-2.0 license metadata and `LICENSE` file;
- the BOM resolves the feature artifacts at one version;
- the dashboard build is clean.

## Release

Set the release version in the root project, then run:

```bash
mvn clean deploy -Drelease
```

The release profile attaches sources and Javadocs, signs Maven artifacts and enables the configured Maven Central publishing workflow. Credentials and signing keys must be supplied through the build environment; they are not stored in the repository.

## Selective consumption

Applications should normally import the BOM and only the feature starters they use. The umbrella Spring Boot starter remains available for the standard all-in-one setup.
