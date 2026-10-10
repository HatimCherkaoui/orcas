# Publishing to Maven Central

Owner: [Hatim Cherkaoui](https://github.com/HatimCherkaoui)
(`cherkaouihatim@gmail.com`). Repository: [HatimCherkaoui/orcas](https://github.com/HatimCherkaoui/orcas).
Published coordinates: `io.github.hatimcherkaoui:workflow-orchestrator-*`.
The parent, BOM, APIs, adapters and starters share one version. Java packages remain `com.github.orcas`.

## Account setup

Sign into [Central Publisher Portal](https://central.sonatype.com/) with your GitHub account
and verify `io.github.hatimcherkaoui` under Namespaces. The project configuration does not
prove portal ownership. See [Sonatype namespace registration](https://central.sonatype.org/register/namespace/).
Generate a portal user token and configure a `central` server in your local Maven settings.
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

The second command attaches source and Javadoc JARs for library modules and verifies
publication packaging without signing or uploading. It reuses the first command's test
result. JARs include `META-INF/LICENSE` and `META-INF/NOTICE`. Starters contain no Java
implementation; their Javadoc-classifier JAR contains the module usage README. The `release-metadata`
profile disables the default `poc` profile, excluding the example and management applications.
Do not explicitly combine `poc` with `release-metadata` for publishing.

## Publish a release

Replace the development snapshot version throughout the reactor with the chosen release
version using the Maven Versions plugin, inspect the version changes, run validation and
commit/tag the release. Central releases must not end in `-SNAPSHOT`.

```bash
mvn org.codehaus.mojo:versions-maven-plugin:2.19.1:set -DnewVersion=0.6.0 -DgenerateBackupPoms=false
./verify.sh
mvn clean deploy -Drelease
```

The release profile attaches sources/Javadocs, signs artifacts and uploads the library
bundle to Central for validation. `autoPublish=false` requires the maintainer to finish
publication in the portal. No automatic publication runs on push or pull requests.
See [Sonatype's Maven publishing guide](https://central.sonatype.org/publish/publish-portal-maven/).

The local POC ZIP is a demonstration bundle; it is separate from Maven Central artifacts.
