from __future__ import annotations

import importlib.util
import json
from io import BytesIO
from pathlib import Path
from unittest import TestCase, main
from unittest.mock import patch


APP_PATH = Path(__file__).parents[1] / "app.py"


def load_app():
    spec = importlib.util.spec_from_file_location("dbapi_app", APP_PATH)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def request(app, method: str, path: str, body: dict | None = None):
    payload = json.dumps(body).encode() if body is not None else b""
    status = {}
    headers = {}

    def start_response(value, response_headers):
        status["value"] = value
        headers.update(dict(response_headers))

    response = b"".join(
        app(
            {
                "REQUEST_METHOD": method,
                "PATH_INFO": path,
                "CONTENT_LENGTH": str(len(payload)),
                "CONTENT_TYPE": "application/json",
                "wsgi.input": BytesIO(payload),
            },
            start_response,
        )
    )
    return status["value"], headers, json.loads(response)


class DbApiContractTest(TestCase):
    def test_root_probe_is_a_success_envelope_for_base_url_connection_tests(self):
        module = load_app()

        status, _, payload = request(module.application, "GET", "/")

        self.assertEqual(status, "200 OK")
        self.assertEqual(payload, {"code": 0, "message": "success", "data": {"service": "dbapi", "status": "UP"}})

    def test_health_response_is_a_success_envelope_without_connection_secret(self):
        module = load_app()

        status, _, payload = request(module.application, "GET", "/health")

        self.assertEqual(status, "200 OK")
        self.assertEqual(payload, {"code": 0, "message": "success", "data": {"status": "UP"}})
        self.assertNotIn("password", json.dumps(payload).lower())

    def test_query_rejects_non_select_and_multiple_statements_before_database_access(self):
        module = load_app()

        for sql in ("DELETE FROM audit_log", "SELECT 1; SELECT 2"):
            status, _, payload = request(module.application, "POST", "/api/v1/db/query", {"sql": sql})
            self.assertEqual(status, "400 Bad Request")
            self.assertEqual(payload["code"], "INVALID_QUERY")

    def test_query_returns_a_standard_envelope_for_a_read_only_statement(self):
        module = load_app()

        with patch.object(module, "run_query", return_value=(["value"], [[1]])):
            status, headers, payload = request(module.application, "POST", "/api/v1/db/query", {"sql": "SELECT 1"})

        self.assertEqual(status, "200 OK")
        self.assertEqual(headers["Content-Type"], "application/json; charset=utf-8")
        self.assertEqual(
            payload,
            {
                "code": 0,
                "message": "success",
                "data": {"columns": ["value"], "rows": [[1]], "rowCount": 1},
            },
        )


if __name__ == "__main__":
    main()
