#!/usr/bin/env python3
"""Install pinned, checksum-verified scanner binaries outside the source tree."""
import hashlib
import io
from pathlib import Path
import platform
import sys
import tarfile
import urllib.request

ASSETS = {
    ('Linux', 'x86_64'): (
        ('https://github.com/google/osv-scanner/releases/download/v2.6.0/osv-scanner_linux_amd64',
         'ca69b3d3cd08f889a49dc0a383122f71cc528b83803671df5fd874d97485b108'),
        ('https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_linux_x64.tar.gz',
         '551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb')),
    ('Darwin', 'arm64'): (
        ('https://github.com/google/osv-scanner/releases/download/v2.6.0/osv-scanner_darwin_arm64',
         '98c460dcd37de25819babd757d04542045b6243113e209edcd4d89fedb0256b4'),
        ('https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_darwin_arm64.tar.gz',
         'b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5')),
}

def main():
    destination = Path(sys.argv[1]).resolve()
    assets = ASSETS.get((platform.system(), platform.machine()))
    if assets is None:
        raise SystemExit('Unsupported platform; use the Linux x86_64 CI runner.')
    destination.mkdir(parents=True, exist_ok=True)
    for name, (url, digest) in zip(('osv-scanner', 'gitleaks'), assets):
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != digest:
            raise SystemExit(f'Checksum mismatch for {name}; refusing to execute it.')
        if name == 'gitleaks':
            with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as archive:
                data = archive.extractfile('gitleaks').read()
        executable = destination / name
        executable.write_bytes(data)
        executable.chmod(0o755)
        print(f'Installed checksum-verified {name}: {executable}')

if __name__ == '__main__':
    main()
