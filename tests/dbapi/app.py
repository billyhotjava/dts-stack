"""Minimal read-only MySQL data API for integration testing."""
from __future__ import annotations

import hmac
import json
import os
from http import HTTPStatus
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs
from wsgiref.simple_server import make_server


MAX_ROWS = int(os.getenv("DBAPI_MAX_ROWS", "100"))
DEFAULT_PAGE_SIZE = 20
MAX_PAGE_SIZE = max(1, min(MAX_ROWS, int(os.getenv("DBAPI_MAX_PAGE_SIZE", str(MAX_ROWS)))))
PJM_RESOURCES = {
    "project-subject-domain": "ods_project_subject_domain_v2",
    "progress-measure": "ods_progress_measure_v2",
    "quality-issue": "ods_quality_issue_v2",
    "quality-measure": "ods_quality_measure_v2",
    "tech-state": "ods_tech_state_v2",
    "tech-state-measure": "ods_tech_state_measure_v2",
    "risk-info": "ods_risk_info_v2",
    "risk-measure": "ods_risk_measure_v2",
    "material-info": "ods_material_info_v2",
    "budget": "ods_budget_v2",
}


class SecretFileError(RuntimeError):
    """Raised when a configured secret file cannot be read."""


def envelope(data: Any, message: str = "success") -> dict[str, Any]:
    return {"code": 0, "message": message, "data": data}


def error(code: str, message: str) -> dict[str, Any]:
    return {"code": code, "message": message, "data": None}


def json_response(start_response, status: HTTPStatus, body: dict[str, Any]):
    encoded = json.dumps(body, ensure_ascii=False, default=str).encode("utf-8")
    start_response(
        f"{status.value} {status.phrase}",
        [("Content-Type", "application/json; charset=utf-8"), ("Content-Length", str(len(encoded)))],
    )
    return [encoded]


def secret_value(environment_name: str, file_environment_name: str) -> str:
    file_name = os.getenv(file_environment_name, "")
    if not file_name:
        return os.getenv(environment_name, "")
    try:
        return Path(file_name).read_text(encoding="utf-8").rstrip("\r\n")
    except OSError as exc:
        raise SecretFileError(f"{file_environment_name} is unreadable") from exc


def bounded_timeout(environment_name: str, default: int = 10, maximum: int = 30) -> int:
    value = int(os.getenv(environment_name, str(default)))
    if value < 1 or value > maximum:
        raise ValueError(f"{environment_name} must be between 1 and {maximum}")
    return value


def database_config() -> dict[str, Any]:
    return {
        "host": os.getenv("DB_HOST", "127.0.0.1"),
        "port": int(os.getenv("DB_PORT", "3306")),
        "user": os.getenv("DB_USER", ""),
        "password": secret_value("DB_PASSWORD", "DB_PASSWORD_FILE"),
        "database": os.getenv("DB_NAME") or None,
        "connect_timeout": int(os.getenv("DB_CONNECT_TIMEOUT", "5")),
        "read_timeout": bounded_timeout("DB_READ_TIMEOUT"),
        "write_timeout": bounded_timeout("DB_WRITE_TIMEOUT"),
        "charset": "utf8mb4",
        "autocommit": True,
    }


def run_query(sql: str, params: list[Any] | None) -> tuple[list[str], list[list[Any]]]:
    import pymysql

    with pymysql.connect(**database_config()) as connection:
        with connection.cursor() as cursor:
            cursor.execute(sql, params or [])
            columns = [item[0] for item in cursor.description or []]
            rows = [list(row) for row in cursor.fetchmany(MAX_ROWS)]
    return columns, rows


def api_key_error(environ: dict[str, Any]) -> tuple[HTTPStatus, dict[str, Any]] | None:
    expected = secret_value("DBAPI_API_KEY", "DBAPI_API_KEY_FILE")
    if not expected:
        return HTTPStatus.SERVICE_UNAVAILABLE, error("API_KEY_NOT_CONFIGURED", "API Key 未配置")
    provided = environ.get("HTTP_X_API_KEY", "")
    if not isinstance(provided, str) or not hmac.compare_digest(provided, expected):
        return HTTPStatus.UNAUTHORIZED, error("UNAUTHORIZED", "API Key 无效")
    return None


def parse_pagination(query_string: str) -> tuple[int, int]:
    values = parse_qs(query_string, keep_blank_values=True)
    try:
        page = int(values.get("page", ["1"])[0])
        size = int(values.get("size", [str(DEFAULT_PAGE_SIZE)])[0])
    except (TypeError, ValueError) as exc:
        raise ValueError("page 和 size 必须是整数") from exc
    if page < 1 or size < 1 or size > MAX_PAGE_SIZE:
        raise ValueError(f"page 必须大于 0，size 必须在 1 到 {MAX_PAGE_SIZE} 之间")
    return page, size


def resource_page(resource: str, page: int, size: int) -> dict[str, Any]:
    table = PJM_RESOURCES[resource]
    _, total_rows = run_query(f"SELECT COUNT(*) AS total FROM `{table}`", [])
    total = int(total_rows[0][0]) if total_rows else 0
    columns, rows = run_query(
        f"SELECT * FROM `{table}` ORDER BY id ASC LIMIT %s OFFSET %s",
        [size, (page - 1) * size],
    )
    visible_columns = [
        (index, column) for index, column in enumerate(columns) if not column.lower().startswith("_dts_")
    ]
    items = [{column: row[index] for index, column in visible_columns} for row in rows]
    return {
        "items": items,
        "total": total,
        "page": page,
        "size": size,
        "hasNext": page * size < total,
    }


def application(environ: dict[str, Any], start_response):
    method = environ.get("REQUEST_METHOD", "GET").upper()
    path = environ.get("PATH_INFO", "/")
    try:
        if method == "GET" and path == "/":
            return json_response(start_response, HTTPStatus.OK, envelope({"service": "dbapi", "status": "UP"}))
        if method == "GET" and path == "/health":
            return json_response(start_response, HTTPStatus.OK, envelope({"status": "UP"}))
        if path.startswith("/api/v1/"):
            authentication_error = api_key_error(environ)
            if authentication_error:
                status, body = authentication_error
                return json_response(start_response, status, body)
        if method == "GET" and path == "/api/v1/db/ping":
            run_query("SELECT 1", [])
            return json_response(start_response, HTTPStatus.OK, envelope({"database": "UP"}))
        if method == "GET" and path == "/api/v1/db/tables":
            schema = parse_qs(environ.get("QUERY_STRING", "")).get("schema", [os.getenv("DB_NAME", "")])[0]
            if not schema:
                return json_response(start_response, HTTPStatus.BAD_REQUEST, error("INVALID_SCHEMA", "schema 不能为空"))
            columns, rows = run_query(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = %s ORDER BY table_name",
                [schema],
            )
            return json_response(start_response, HTTPStatus.OK, envelope({"columns": columns, "rows": rows, "rowCount": len(rows)}))
        if method == "GET" and path == "/api/v1/pjm/resources":
            items = [
                {"resource": resource, "path": f"/api/v1/pjm/{resource}"}
                for resource in PJM_RESOURCES
            ]
            return json_response(
                start_response,
                HTTPStatus.OK,
                envelope({"items": items, "total": len(items)}),
            )
        if method == "GET" and path.startswith("/api/v1/pjm/"):
            resource = path.removeprefix("/api/v1/pjm/")
            if resource not in PJM_RESOURCES:
                return json_response(
                    start_response,
                    HTTPStatus.NOT_FOUND,
                    error("RESOURCE_NOT_FOUND", "资源不存在"),
                )
            try:
                page, size = parse_pagination(environ.get("QUERY_STRING", ""))
            except ValueError as exc:
                return json_response(
                    start_response,
                    HTTPStatus.BAD_REQUEST,
                    error("INVALID_PAGINATION", str(exc)),
                )
            return json_response(
                start_response,
                HTTPStatus.OK,
                envelope(resource_page(resource, page, size)),
            )
        return json_response(start_response, HTTPStatus.NOT_FOUND, error("NOT_FOUND", "接口不存在"))
    except SecretFileError:
        return json_response(
            start_response,
            HTTPStatus.SERVICE_UNAVAILABLE,
            error("SECRET_UNAVAILABLE", "密钥文件不可用"),
        )
    except Exception:
        return json_response(start_response, HTTPStatus.SERVICE_UNAVAILABLE, error("DATABASE_UNAVAILABLE", "数据库暂不可用"))


if __name__ == "__main__":
    host = os.getenv("DBAPI_HOST", "0.0.0.0")
    port = int(os.getenv("DBAPI_PORT", "8000"))
    print(f"dbapi listening on {host}:{port}")
    make_server(host, port, application).serve_forever()
