#!/usr/bin/env python3
"""Check effective release metadata and the actual Maven publication artifacts."""
import argparse
import re
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
REQUIRED = ('name', 'description', 'url', 'licenses/license/name',
            'licenses/license/url', 'developers/developer/id',
            'developers/developer/name', 'developers/developer/url',
            'scm/connection', 'scm/developerConnection', 'scm/url')
FORBIDDEN_PARTS = {'.git', '.agents', '.codex', '.idea', 'node_modules', 'target', 'src/test'}

def text(pom, path):
    return pom.findtext('/'.join('m:' + part for part in path.split('/')), namespaces=NS) or ''

def check(condition, message):
    if not condition:
        raise SystemExit(message)

def inspect_jar(path):
    check(path.is_file(), f'Missing publication artifact: {path}')
    with ZipFile(path) as archive:
        check(archive.testzip() is None, f'Corrupt JAR: {path}')
        names = set(archive.namelist())
        for name in names:
            parts = set(Path(name).parts)
            check(not (parts & FORBIDDEN_PARTS) and '/src/test/' not in '/' + name,
                  f'Local/build/test content in {path}: {name}')
            check(Path(name).name != 'AGENTS.md' and not Path(name).name.startswith('.env'),
                  f'Private configuration in {path}: {name}')
        return names

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--effective-pom', type=Path, default=ROOT / 'target/central-effective-pom.xml')
    parser.add_argument('--signed', action='store_true')
    args = parser.parse_args()
    root_pom = ET.parse(ROOT / 'pom.xml').getroot()
    version = text(root_pom, 'version')
    check(version and not version.endswith('-SNAPSHOT'), 'Central release version must not be a snapshot.')
    expected = {text(root_pom, 'artifactId')}
    directories = {text(root_pom, 'artifactId'): ROOT}
    for module in root_pom.findall('m:modules/m:module', NS):
        directory = ROOT / module.text
        pom = ET.parse(directory / 'pom.xml').getroot()
        artifact = text(pom, 'artifactId')
        expected.add(artifact)
        directories[artifact] = directory
        check(text(pom, 'parent/version') == version, f'Inconsistent parent version: {module.text}')
    # Maven Help's aggregate output contains a declaration inside each project.
    effective_xml = re.sub(r'<\?xml[^?]*\?>', '', args.effective_pom.read_text())
    # dependency:properties adds coordinate-named elements containing colons;
    # Maven Help emits them as invalid XML. They are not publication metadata.
    effective_xml = re.sub(r'<properties>.*?</properties>', '', effective_xml, flags=re.S)
    effective = ET.fromstring(effective_xml)
    projects = [p for p in effective if p.tag.endswith('project')] if effective.tag.endswith('projects') else [effective]
    check({text(p, 'artifactId') for p in projects} == expected,
          'Publication reactor must contain exactly the parent, BOM, and libraries; no applications.')
    artifact_count = 0
    for pom in projects:
        artifact = text(pom, 'artifactId')
        directory = directories[artifact]
        check(text(pom, 'groupId') == 'io.github.hatimcherkaoui', f'Wrong namespace: {artifact}')
        check(text(pom, 'version') == version, f'Wrong version: {artifact}')
        for field in REQUIRED:
            value = text(pom, field)
            check(value.strip() and '${' not in value, f'Missing/unresolved {field}: {artifact}')
        for dependency in pom.findall('.//m:dependency', NS):
            check(not text(dependency, 'version').endswith('-SNAPSHOT'),
                  f'Snapshot dependency in {artifact}: {text(dependency, "artifactId")}')
        published = [(directory / 'pom.xml', directory / 'target' / f'{artifact}-{version}.pom.asc')]
        if text(pom, 'packaging') != 'pom':
            base = directory / 'target' / f'{artifact}-{version}'
            binary = Path(str(base) + '.jar')
            source = Path(str(base) + '-sources.jar')
            javadoc = Path(str(base) + '-javadoc.jar')
            binary_names = inspect_jar(binary)
            source_names = inspect_jar(source)
            documentation = inspect_jar(javadoc)
            check({'META-INF/LICENSE', 'META-INF/NOTICE'} <= binary_names,
                  f'Missing legal notices: {binary}')
            java_files = sorted((directory / 'src/main/java').rglob('*.java'))
            with ZipFile(source) as source_archive:
                for java in java_files:
                    entry = java.relative_to(directory / 'src/main/java').as_posix()
                    check(entry in source_names and source_archive.read(entry) == java.read_bytes(),
                          f'Missing or stale source in {source}: {entry}')
            check('index.html' in documentation if java_files else 'README.md' in documentation,
                  f'Missing API/usage documentation: {javadoc}')
            with ZipFile(binary) as archive:
                check(not any(n.startswith('BOOT-INF/') for n in binary_names), f'Executable app in library: {binary}')
                for name in binary_names:
                    if name.endswith('.class'):
                        data = archive.read(name)
                        check(data[:4] == b'\xca\xfe\xba\xbe' and data[4:6] == b'\x00\x00'
                              and int.from_bytes(data[6:8], 'big') == 69,
                              f'Unexpected bytecode version or preview class: {binary}:{name}')
            published += [(p, Path(str(p) + '.asc')) for p in (binary, source, javadoc)]
        if args.signed:
            for content, signature in published:
                check(signature.is_file(), f'Missing signature: {signature}')
                subprocess.run(['gpg', '--batch', '--verify', str(signature), str(content)], check=True)
        artifact_count += len(published)
    print(f'Validated {len(projects)} Maven components and {artifact_count} publication files for {version}.')

if __name__ == '__main__':
    main()
