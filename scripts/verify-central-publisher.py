#!/usr/bin/env python3
"""Exercise the real publisher against a loopback-only, synthetic Portal fixture."""
import argparse
from email import policy
from email.parser import BytesParser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
from threading import Thread
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / 'target/central-publishing/central-bundle.zip'
requests = []

class PortalFixture(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # Never log authorization headers or multipart payloads.

    def do_POST(self):
        path = urlparse(self.path).path
        length = int(self.headers.get('Content-Length', '0'))
        if not 0 <= length <= 64 * 1024 * 1024:
            self.send_error(413)
            return
        payload = self.rfile.read(length)
        if path == '/api/v1/publisher/upload':
            message = BytesParser(policy=policy.default).parsebytes(
                b'Content-Type: ' + self.headers['Content-Type'].encode() + b'\r\n\r\n' + payload)
            files = [part.get_payload(decode=True) for part in message.walk() if part.get_filename()]
            if len(files) != 1 or not BUNDLE.is_file() or files[0] != BUNDLE.read_bytes():
                self.send_error(400, 'Uploaded payload does not match the generated bundle')
                return
            body = b'00000000-0000-4000-8000-000000000001'
            content_type = 'text/plain'
        elif path == '/api/v1/publisher/status':
            body = json.dumps({'deploymentId': '00000000-0000-4000-8000-000000000001',
                               'deploymentName': 'offline-verification', 'deploymentState': 'VALIDATED',
                               'purls': [], 'errors': {}, 'warnings': []}).encode()
            content_type = 'application/json'
        else:
            self.send_error(404)
            return
        requests.append(path)
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--unsigned', action='store_true',
                        help='Skip GPG signing for local upload/status verification only.')
    args = parser.parse_args()
    requests.clear()
    with ThreadingHTTPServer(('127.0.0.1', 0), PortalFixture) as server:
        worker = Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            command = ['mvn', '--batch-mode', '--no-transfer-progress', '--settings',
                       str(ROOT / 'scripts/central-offline-settings.xml'), '-Drelease', '-DskipTests',
                       '-Dcentral.autoPublish=false', '-Dcentral.waitUntil=validated',
                       '-Dcentral.skipPublishing=false',
                       f'-Dcentral.baseUrl=http://127.0.0.1:{server.server_port}', 'deploy']
            if args.unsigned:
                command.insert(-1, '-Dgpg.skip=true')
            subprocess.run(command, cwd=ROOT, check=True, timeout=600)
        finally:
            server.shutdown()
            worker.join()
    if requests != ['/api/v1/publisher/upload', '/api/v1/publisher/status']:
        raise SystemExit(f'Unexpected publisher requests: {requests}')
    print('Real Central plugin upload/status contract verified against loopback fixture; no registry upload.')

if __name__ == '__main__':
    main()
