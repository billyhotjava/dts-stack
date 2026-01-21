from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import re

SECRETS = {}


def secret_key(namespace, name):
    return f"{namespace}/{name}"


def normalize_secret(namespace, payload):
    meta = payload.get("metadata") or {}
    name = meta.get("name") or ""
    meta["name"] = name
    meta["namespace"] = namespace
    payload["apiVersion"] = payload.get("apiVersion", "v1")
    payload["kind"] = payload.get("kind", "Secret")
    payload["metadata"] = meta
    payload["data"] = payload.get("data") or {}
    return payload, name


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self):
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode("utf-8"))
        except Exception:
            return {}

    def log_message(self, fmt, *args):
        return

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path in ("/api", "/api/"):
            self._send(200, {"versions": ["v1"]})
            return
        if path in ("/api/v1", "/api/v1/"):
            self._send(200, {"kind": "APIVersions", "versions": ["v1"]})
            return
        match = re.match(r"^/api/v1/namespaces/([^/]+)/secrets/([^/]+)$", path)
        if match:
            namespace, name = match.group(1), match.group(2)
            secret = SECRETS.get(secret_key(namespace, name))
            if secret is None:
                self._send(404, {"kind": "Status", "code": 404, "message": "Not Found"})
            else:
                self._send(200, secret)
            return
        self._send(200, {"status": "ok"})

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        match = re.match(r"^/api/v1/namespaces/([^/]+)/secrets$", path)
        if match:
            namespace = match.group(1)
            payload = self._read_json()
            payload, name = normalize_secret(namespace, payload)
            if not name:
                self._send(400, {"kind": "Status", "code": 400, "message": "Missing secret name"})
                return
            SECRETS[secret_key(namespace, name)] = payload
            self._send(201, payload)
            return
        self._send(404, {"kind": "Status", "code": 404, "message": "Not Found"})

    def do_PUT(self):
        path = self.path.split("?", 1)[0]
        match = re.match(r"^/api/v1/namespaces/([^/]+)/secrets/([^/]+)$", path)
        if match:
            namespace, name = match.group(1), match.group(2)
            payload = self._read_json()
            payload, payload_name = normalize_secret(namespace, payload)
            if not payload_name:
                payload["metadata"]["name"] = name
                payload_name = name
            SECRETS[secret_key(namespace, payload_name)] = payload
            self._send(200, payload)
            return
        self._send(404, {"kind": "Status", "code": 404, "message": "Not Found"})


def main():
    server = HTTPServer(("0.0.0.0", 8000), Handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
