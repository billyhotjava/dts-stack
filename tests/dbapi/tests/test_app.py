from __future__ import annotations

import importlib.util
import json
import os
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, main
from unittest.mock import call, patch


APP_PATH = Path(__file__).parents[1] / "app.py"


def load_app():
    spec = importlib.util.spec_from_file_location("dbapi_app", APP_PATH)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def request(
    app,
    method: str,
    path: str,
    body: dict | None = None,
    headers: dict[str, str] | None = None,
):
    payload = json.dumps(body).encode() if body is not None else b""
    status = {}
    response_headers = {}
    request_path, _, query_string = path.partition("?")

    def start_response(value, values):
        status["value"] = value
        response_headers.update(dict(values))

    environ = {
        "REQUEST_METHOD": method,
        "PATH_INFO": request_path,
        "QUERY_STRING": query_string,
        "CONTENT_LENGTH": str(len(payload)),
        "CONTENT_TYPE": "application/json",
        "wsgi.input": BytesIO(payload),
    }
    for name, value in (headers or {}).items():
        environ[f"HTTP_{name.upper().replace('-', '_')}"] = value

    response = b"".join(
        app(
            environ,
            start_response,
        )
    )
    return status["value"], response_headers, json.loads(response)


class DbApiContractTest(TestCase):
    API_KEY = "unit-test-api-key"
    API_KEY_HEADER = {"X-API-Key": API_KEY}

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

    def test_generic_query_endpoint_is_not_exposed(self):
        module = load_app()

        with (
            patch.dict(os.environ, {"DBAPI_API_KEY": self.API_KEY, "DBAPI_API_KEY_FILE": ""}, clear=False),
            patch.object(module, "run_query") as run_query,
        ):
            status, _, payload = request(
                module.application,
                "POST",
                "/api/v1/db/query",
                {"sql": "WITH candidate AS (SELECT 1) DELETE FROM audit_log"},
                self.API_KEY_HEADER,
            )

        self.assertEqual(status, "404 Not Found")
        self.assertEqual(payload["code"], "NOT_FOUND")
        run_query.assert_not_called()

    def test_api_endpoints_fail_closed_and_require_the_configured_api_key(self):
        module = load_app()

        with patch.dict(os.environ, {}, clear=True):
            status, _, payload = request(
                module.application,
                "GET",
                "/api/v1/pjm/resources",
                headers=self.API_KEY_HEADER,
            )
        self.assertEqual(status, "503 Service Unavailable")
        self.assertEqual(payload["code"], "API_KEY_NOT_CONFIGURED")

        with (
            patch.dict(os.environ, {"DBAPI_API_KEY": self.API_KEY, "DBAPI_API_KEY_FILE": ""}, clear=False),
            patch.object(module, "run_query") as run_query,
        ):
            status, _, payload = request(module.application, "GET", "/api/v1/pjm/resources")
            self.assertEqual(status, "401 Unauthorized")
            self.assertEqual(payload["code"], "UNAUTHORIZED")

            status, _, payload = request(
                module.application,
                "GET",
                "/api/v1/pjm/resources",
                headers={"X-API-Key": "wrong-key"},
            )
            self.assertEqual(status, "401 Unauthorized")
            self.assertEqual(payload["code"], "UNAUTHORIZED")
            run_query.assert_not_called()

    def test_secret_files_override_plaintext_environment_values(self):
        module = load_app()

        with TemporaryDirectory() as directory:
            password_file = Path(directory) / "db-password"
            api_key_file = Path(directory) / "api-key"
            password_file.write_text("password-from-file\n", encoding="utf-8")
            api_key_file.write_text(f"{self.API_KEY}\n", encoding="utf-8")
            environment = {
                "DB_PASSWORD": "password-from-env",
                "DB_PASSWORD_FILE": str(password_file),
                "DBAPI_API_KEY": "api-key-from-env",
                "DBAPI_API_KEY_FILE": str(api_key_file),
            }

            with (
                patch.dict(os.environ, environment, clear=False),
                patch.object(module, "run_query", return_value=(["value"], [[1]])),
            ):
                self.assertEqual(module.database_config()["password"], "password-from-file")
                status, _, _ = request(
                    module.application,
                    "GET",
                    "/api/v1/db/ping",
                    headers=self.API_KEY_HEADER,
                )
                rejected_status, _, _ = request(
                    module.application,
                    "GET",
                    "/api/v1/db/ping",
                    headers={"X-API-Key": "api-key-from-env"},
                )

        self.assertEqual(status, "200 OK")
        self.assertEqual(rejected_status, "401 Unauthorized")

    def test_database_read_and_write_timeouts_are_bounded(self):
        module = load_app()

        with patch.dict(
            os.environ,
            {
                "DB_PASSWORD_FILE": "",
                "DB_READ_TIMEOUT": "7",
                "DB_WRITE_TIMEOUT": "8",
            },
            clear=False,
        ):
            config = module.database_config()

        self.assertEqual(config["read_timeout"], 7)
        self.assertEqual(config["write_timeout"], 8)

        with patch.dict(
            os.environ,
            {
                "DB_PASSWORD_FILE": "",
                "DB_READ_TIMEOUT": "31",
                "DB_WRITE_TIMEOUT": "0",
            },
            clear=False,
        ):
            with self.assertRaises(ValueError):
                module.database_config()

    def test_resources_returns_the_ten_whitelisted_pjm_resources(self):
        module = load_app()
        expected_resources = {
            "project-subject-domain",
            "progress-measure",
            "quality-issue",
            "quality-measure",
            "tech-state",
            "tech-state-measure",
            "risk-info",
            "risk-measure",
            "material-info",
            "budget",
        }

        with patch.dict(os.environ, {"DBAPI_API_KEY": self.API_KEY, "DBAPI_API_KEY_FILE": ""}, clear=False):
            status, _, payload = request(
                module.application,
                "GET",
                "/api/v1/pjm/resources",
                headers=self.API_KEY_HEADER,
            )

        self.assertEqual(status, "200 OK")
        self.assertEqual(payload["data"]["total"], 10)
        self.assertEqual({item["resource"] for item in payload["data"]["items"]}, expected_resources)
        self.assertTrue(all(isinstance(item, dict) for item in payload["data"]["items"]))

    def test_unknown_resource_and_invalid_pagination_are_rejected_before_database_access(self):
        module = load_app()

        with (
            patch.dict(os.environ, {"DBAPI_API_KEY": self.API_KEY, "DBAPI_API_KEY_FILE": ""}, clear=False),
            patch.object(module, "run_query") as run_query,
        ):
            status, _, payload = request(
                module.application,
                "GET",
                "/api/v1/pjm/not-allowed",
                headers=self.API_KEY_HEADER,
            )
            self.assertEqual(status, "404 Not Found")
            self.assertEqual(payload["code"], "RESOURCE_NOT_FOUND")

            for query in ("page=0", "page=not-a-number", "size=0", "size=100000"):
                status, _, payload = request(
                    module.application,
                    "GET",
                    f"/api/v1/pjm/budget?{query}",
                    headers=self.API_KEY_HEADER,
                )
                self.assertEqual(status, "400 Bad Request")
                self.assertEqual(payload["code"], "INVALID_PAGINATION")
            run_query.assert_not_called()

    def test_resource_page_returns_objects_and_stable_pagination_metadata(self):
        module = load_app()
        database_result = (
            ["id", "classification", "owner_dept", "project_no", "_dts_source_system", "_dts_import_time"],
            [
                [1, "INTERNAL", "DEPT_PJM_A", "P-001", "excel", "2026-07-28"],
                [2, "SECRET", "DEPT_PJM_B", "P-002", "excel", "2026-07-28"],
            ],
        )

        with (
            patch.dict(os.environ, {"DBAPI_API_KEY": self.API_KEY, "DBAPI_API_KEY_FILE": ""}, clear=False),
            patch.object(module, "run_query", side_effect=[(["total"], [[3]]), database_result]) as run_query,
        ):
            status, _, payload = request(
                module.application,
                "GET",
                "/api/v1/pjm/project-subject-domain?page=1&size=2",
                headers=self.API_KEY_HEADER,
            )

        self.assertEqual(status, "200 OK")
        self.assertEqual(
            payload["data"],
            {
                "items": [
                    {
                        "id": 1,
                        "classification": "INTERNAL",
                        "owner_dept": "DEPT_PJM_A",
                        "project_no": "P-001",
                    },
                    {
                        "id": 2,
                        "classification": "SECRET",
                        "owner_dept": "DEPT_PJM_B",
                        "project_no": "P-002",
                    },
                ],
                "total": 3,
                "page": 1,
                "size": 2,
                "hasNext": True,
            },
        )
        run_query.assert_has_calls(
            [
                call("SELECT COUNT(*) AS total FROM `ods_project_subject_domain_v2`", []),
                call(
                    "SELECT * FROM `ods_project_subject_domain_v2` ORDER BY id ASC LIMIT %s OFFSET %s",
                    [2, 0],
                ),
            ]
        )


if __name__ == "__main__":
    main()
