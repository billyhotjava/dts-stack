#!/usr/bin/env python3
import json
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


records = [
    {"id": "ord-001", "amount": 100, "updatedAt": "2026-06-12T00:01:00Z"},
    {"id": "ord-002", "amount": 200, "updatedAt": "2026-06-12T00:02:00Z"},
]
next_record = 3


def parse_instant(value):
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.respond({"ok": True})
            return
        if parsed.path == "/__append":
            self.append_record()
            return
        if parsed.path == "/v1/orders":
            self.orders(parse_qs(parsed.query))
            return
        self.respond({"error": "not found", "path": parsed.path}, status=404)

    def orders(self, query):
        updated_after = parse_instant(first(query, "updatedAfter"))
        items = []
        for record in records:
            if updated_after is not None and parse_instant(record["updatedAt"]) <= updated_after:
                continue
            items.append(record)
        self.respond({"data": {"items": items, "total": len(items)}})

    def append_record(self):
        global next_record
        record_id = f"ord-{next_record:03d}"
        if not any(record["id"] == record_id for record in records):
            records.append(
                {
                    "id": record_id,
                    "amount": next_record * 100,
                    "updatedAt": f"2026-06-12T00:{next_record:02d}:00Z",
                }
            )
            next_record += 1
        self.respond({"ok": True, "count": len(records)})

    def respond(self, payload, status=200):
        raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.address_string(), fmt % args), flush=True)


def first(query, key):
    values = query.get(key)
    if not values:
        return None
    return values[0]


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 18080), Handler).serve_forever()
