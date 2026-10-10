#!/usr/bin/env python3
"""Package verified public sources and built POC artifacts without local metadata."""
from pathlib import Path
import hashlib
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
pom = ET.parse(ROOT / 'pom.xml').getroot()
version = pom.findtext('m:version', namespaces=NS)
entries = {}


def add(path, destination):
    if not path.is_file():
        raise SystemExit(f'Missing {path.relative_to(ROOT)}; run the documented POC build first.')
    entries[destination] = path.read_bytes()


# Git's inventory includes new public files but respects local exclusions.
inventory = subprocess.check_output(
    ['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=ROOT
).decode().split('\0')
for name in sorted(set(inventory)):
    if not name:
        continue
    path = Path(name)
    if any(part in {'.agents', '.codex', '.git', 'node_modules', 'target', 'dist'} for part in path.parts):
        continue
    if path.name == 'AGENTS.md' or path.parts[:2] == ('.github', 'agents'):
        continue
    if (ROOT / path).is_file():
        add(ROOT / path, 'source/' + name)

add(ROOT / 'pom.xml', f'lib/workflow-orchestrator-{version}.pom')

for module in pom.findall('m:modules/m:module', NS):
    name = module.text
    module_pom = ET.parse(ROOT / name / 'pom.xml').getroot()
    artifact = module_pom.findtext('m:artifactId', namespaces=NS)
    packaging = module_pom.findtext('m:packaging', default='jar', namespaces=NS)
    add(ROOT / name / 'pom.xml', f'lib/{artifact}-{version}.pom')
    if packaging == 'jar':
        for classifier in ('', '-sources', '-javadoc'):
            jar = f'{artifact}-{version}{classifier}.jar'
            add(ROOT / name / 'target' / jar, 'lib/' + jar)

add(ROOT / 'example-app/target/app.jar', 'apps/order-example.jar')
add(ROOT / 'workflow-orchestrator-management-service/target/management-service.jar', 'apps/management-service.jar')
add(ROOT / 'workflow-orchestrator-dashboard/dist/index.html', 'dashboard/index.html')
for path in sorted((ROOT / 'workflow-orchestrator-dashboard/dist').rglob('*')):
    if path.is_file():
        add(path, 'dashboard/' + path.relative_to(ROOT / 'workflow-orchestrator-dashboard/dist').as_posix())

manifest = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name, data in sorted(entries.items()))
entries['manifest.sha256'] = manifest.encode()
archive = ROOT / 'target' / f'orcas-{version}-poc.zip'
archive.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as bundle:
    for name, data in sorted(entries.items()):
        info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        executable = name.endswith(('.sh', '/mvnw'))
        info.external_attr = (0o100755 if executable else 0o100644) << 16
        bundle.writestr(info, data)
checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix('.zip.sha256').write_text(f'{checksum}  {archive.name}\n')
print(f'Packaged {len(entries)} files: {archive}')
