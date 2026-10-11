#!/usr/bin/env python3
"""Compile external consumers against installed release POMs, not reactor classes."""
import json
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
version = ET.parse(ROOT / 'pom.xml').getroot().findtext('{http://maven.apache.org/POM/4.0.0}version')
GROUP = 'io.github.hatimcherkaoui'
for starter in (False, True):
    artifact = 'workflow-orchestrator-spring-boot-starter' if starter else 'workflow-orchestrator-core'
    bom = f'''<dependencyManagement><dependencies><dependency><groupId>{GROUP}</groupId>
      <artifactId>workflow-orchestrator-bom</artifactId><version>{version}</version>
      <type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>''' if starter else ''
    with tempfile.TemporaryDirectory(prefix='orcas-consumer-') as temp:
        directory = Path(temp)
        (directory / 'pom.xml').write_text(f'''<project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>consumer</artifactId>
          <version>1.0.0</version>{bom}<dependencies><dependency><groupId>{GROUP}</groupId>
          <artifactId>{artifact}</artifactId>{'' if starter else f'<version>{version}</version>'}
          </dependency></dependencies><build><plugins><plugin>
          <groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>
          <version>3.14.1</version><configuration><release>25</release></configuration>
          </plugin></plugins></build></project>''')
        source = directory / 'src/main/java/example/Consumer.java'
        source.parent.mkdir(parents=True)
        source.write_text('''package example;
          import com.github.orcas.orchestrator.core.model.WorkflowContext;
          public class Consumer { public static Class<?> workflowType() { return WorkflowContext.class; } }
          ''')
        subprocess.run(['mvn', '--batch-mode', '--no-transfer-progress', 'package',
                        'org.apache.maven.plugins:maven-dependency-plugin:3.11.0:tree',
                        '-DoutputType=json', '-DoutputFile=target/dependencies.json'], cwd=directory, check=True)
        graph = json.loads((directory / 'target/dependencies.json').read_text())
        def nodes(node):
            yield node
            for child in node.get('children', []):
                yield from nodes(child)
        dependencies = {(n['groupId'], n['artifactId']): n['version'] for n in nodes(graph)}
        if not starter and any(g.startswith(('org.springframework', 'io.opentelemetry', 'org.apache.kafka')) for g, a in dependencies):
            raise SystemExit('Plain core consumer unexpectedly pulls integration dependencies.')
        if starter:
            for coordinate, patched in {('tools.jackson.core', 'jackson-core'): '3.1.7',
                                        ('tools.jackson.core', 'jackson-databind'): '3.1.7',
                                        ('org.apache.tomcat.embed', 'tomcat-embed-core'): '11.0.26',
                                        ('at.yawk.lz4', 'lz4-java'): '1.11.4'}.items():
                if dependencies.get(coordinate) != patched:
                    raise SystemExit(f'Consumer lost security override {coordinate}: {dependencies.get(coordinate)}')
        if any(g == GROUP and v != version for (g, a), v in dependencies.items()):
            raise SystemExit('Consumer resolves inconsistent Orcas versions.')
        print(f'External {artifact} consumer passed ({len(dependencies)-1} dependencies).')
