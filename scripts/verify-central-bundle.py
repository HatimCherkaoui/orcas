#!/usr/bin/env python3
"""Verify the exact offline Central bundle, including signatures and checksums."""
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
pom = ET.parse(ROOT / 'pom.xml').getroot()
version = pom.findtext('m:version', namespaces=NS)
group = pom.findtext('m:groupId', namespaces=NS).replace('.', '/')
expected = set()
for directory in [ROOT, *(ROOT / e.text for e in pom.findall('m:modules/m:module', NS))]:
    module = ET.parse(directory / 'pom.xml').getroot()
    artifact = module.findtext('m:artifactId', namespaces=NS)
    prefix = f'{group}/{artifact}/{version}/{artifact}-{version}'
    expected.add(prefix + '.pom')
    if module.findtext('m:packaging', default='jar', namespaces=NS) != 'pom':
        expected.update(prefix + suffix for suffix in ('.jar', '-sources.jar', '-javadoc.jar'))

bundle = ROOT / 'target/central-publishing/central-bundle.zip'
with ZipFile(bundle) as archive:
    names = {n for n in archive.namelist() if not n.endswith('/')}
    expected_entries = set()
    for name in sorted(expected):
        for content_name in (name, name + '.asc'):
            expected_entries.add(content_name)
            if content_name not in names:
                raise SystemExit(f'Missing bundle file: {content_name}')
            content = archive.read(content_name)
            if not content:
                raise SystemExit(f'Empty bundle file: {content_name}')
            # Central's plugin generates checksums for primary files; signatures
            # may also carry checksums, which are checked whenever present.
            for algorithm in ('md5', 'sha1', 'sha256', 'sha512'):
                checksum_name = content_name + '.' + algorithm
                if content_name == name or checksum_name in names:
                    expected_entries.add(checksum_name)
                    if checksum_name not in names:
                        raise SystemExit(f'Missing checksum: {checksum_name}')
                    actual = archive.read(checksum_name).decode().strip().split()[0]
                    if actual != hashlib.new(algorithm, content).hexdigest():
                        raise SystemExit(f'Invalid checksum: {checksum_name}')
    if names != expected_entries:
        raise SystemExit(f'Unexpected bundle files: {sorted(names - expected_entries)}')
print(f'Validated Central bundle: {len(expected)} publication files with signatures and checksums.')
