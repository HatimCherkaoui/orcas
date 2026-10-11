# Publishing to Maven Central

Owner: [Hatim Cherkaoui](https://github.com/HatimCherkaoui)
(`cherkaouihatim@gmail.com`). Repository: [HatimCherkaoui/orcas](https://github.com/HatimCherkaoui/orcas).
Published coordinates: `io.github.hatimcherkaoui:workflow-orchestrator-*`.
The parent, BOM, APIs, adapters and starters share one version. Java packages remain `com.github.orcas`.

## Account setup

Sign into [Central Publisher Portal](https://central.sonatype.com/) with your GitHub account
and verify `io.github.hatimcherkaoui` under Namespaces. The project configuration does not
prove portal ownership. See [Sonatype namespace registration](https://central.sonatype.org/register/namespace/).
Generate a portal user token. For GitHub Actions, configure these repository secrets:

| Secret | Value |
| --- | --- |
| `CENTRAL_TOKEN_USERNAME` | Central Portal token username |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal token password |
| `MAVEN_GPG_KEY` | ASCII-armored exported private signing key |
| `MAVEN_GPG_PASSPHRASE` | Signing key passphrase |

Publish the corresponding public key to a keyserver accepted by Central. Use a
passphrase-protected signing key. Never paste private keys or tokens into issues,
workflow inputs, source files, or chat. For local deployment, configure a `central` server in Maven settings.
Keep tokens and signing keys outside this repository:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <servers>
    <server>
      <id>central</id>
      <username>${env.CENTRAL_TOKEN_USERNAME}</username>
      <password>${env.CENTRAL_TOKEN_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

Create or import your GPG signing key, publish its public key to a keyserver accepted by
Central, and configure `gpg.keyname` and `MAVEN_GPG_PASSPHRASE` in your local environment.
The signing plugin enables its best-practices mode; passphrases must not be committed
or supplied through cleartext POM properties.
See the official [GPG requirements](https://central.sonatype.org/publish/requirements/gpg/).

## Validate without publishing

```bash
./verify.sh
mvn -Drelease -Dgpg.skip=true -DskipTests package
```

The second command attaches source and Javadoc JARs for library modules without signing
or uploading. It reuses the first command's test
result. JARs include `META-INF/LICENSE` and `META-INF/NOTICE`. Starters contain no Java
implementation; their Javadoc-classifier JAR contains the module usage README. The `release-metadata`
profile disables the default `poc` profile, excluding the example and management applications.
Do not explicitly combine `poc` with `release-metadata` for publishing.

Audit the actual publication files and effective metadata:

```bash
mvn -Drelease -Dgpg.skip=true -DskipTests package help:effective-pom -Doutput=target/central-effective-pom.xml
python3 scripts/verify-central-artifacts.py
```

The audit checks all 23 components, non-snapshot versions and dependencies, inherited
Central metadata, matching source content, readable JARs, license/notice files,
Javadoc or starter usage documentation, Java 25 bytecode, and exclusion of runnable
applications and local/test files. `--signed` also verifies each detached signature.
Archive timestamps are fixed by `project.build.outputTimestamp` for reproducible builds.

## CI release gates

`.github/workflows/ci.yml` runs these checks on pushes and pull requests:

- Maven unit and Testcontainers integration tests, including the observability stack.
- Module boundaries, frontend tests/build, publication audit and POC packaging.
- Actual Maven GPG signing with an ephemeral CI key (never used for publication).
- Real Central plugin upload/status verification against a loopback-only synthetic
  Portal fixture (no real registry upload), bundle/signature/checksum validation,
  and external Maven consumer compilation against installed release POMs, including
  security version propagation.
- CodeQL security-extended analysis for Java, JavaScript, Python and GitHub Actions. Security
  findings fail the job; generating/uploading a report alone does not pass the gate.
- CycloneDX inventory of resolved direct/transitive Maven dependencies, including
  test and application dependencies, scanned with OSV together with the npm lockfile.
  Any reported vulnerability or scanner error fails the job. No dependency advisories
  are suppressed.
- Gitleaks over the full Git history. Only exact historical demo-password fingerprints
  and Compose's `ELASTIC_PASSWORD` variable references are exempted; new literal secrets fail.

Actions are pinned to upstream commits; scanner downloads use fixed versions and SHA-256
checksums. Redacted secret reports, dependency reports/SBOM and CodeQL SARIF reports are
retained as CI artifacts. Scans reflect the databases and queries available at run time;
they do not establish that undiscovered vulnerabilities are absent.

## Publish a release

The reactor is prepared as release candidate `0.6.0`. For future releases, update the
version throughout the reactor, inspect changes and update the changelog and reproducible
archive timestamp. Central releases must not end in `-SNAPSHOT`; the release profile
rejects snapshots in the project and resolved dependencies.

```bash
mvn org.codehaus.mojo:versions-maven-plugin:2.19.1:set -DnewVersion=0.6.0 -DgenerateBackupPoms=false
./verify.sh
```

After committing the release to `main`, run **Publish Maven Central release** from GitHub
Actions (or `gh workflow run publish.yml --ref main`). It reruns all CI gates on the same
commit, checks the four secrets, signs and audits the library artifacts, creates a version
tag at that commit, and publishes using `autoPublish=true`, waiting for Central's
`published` confirmation. Only `main` can publish. Ordinary pushes and pull requests do
not publish or receive signing credentials. Central validates the namespace, metadata
and public signatures server-side; it can reject a bundle even after local checks pass.

For a local release, after the same CI checks have passed:

```bash
mvn clean deploy -Drelease -Dcentral.autoPublish=true -Dcentral.waitUntil=published
```

The default `autoPublish=false` supports manual Portal validation when explicitly testing
the upload process. Published Central release coordinates are immutable.
See [Sonatype's Maven publishing guide](https://central.sonatype.org/publish/publish-portal-maven/).

The local POC ZIP is a demonstration bundle; it is separate from Maven Central artifacts.
