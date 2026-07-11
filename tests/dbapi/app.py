"""Minimal read-only MySQL data API for integration testing."""
from __future__ import annotations

import json
import os
import re
from http import HTTPStatus
from typing import Any
from urllib.parse import parse_qs
from wsgiref.simple_server import make_server


MAX_ROWS = int(os.getenv("DBAPI_MAX_ROWS", "100"))
READ_ONLY_SQL = re.compile(r"^\s*(SELECT|WITH)\b", re.IGNORECASE)


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


def read_json(environ: dict[str, Any]) -> dict[str, Any]:
    size = int(environ.get("CONTENT_LENGTH") or 0)
    if size <= 0:
        return {}
    value = json.loads(environ["wsgi.input"].read(size).decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError("请求体必须是 JSON 对象")
    return value


def validate_read_only_sql(sql: Any) -> str:
    if not isinstance(sql, str) or not sql.strip():
        raise ValueError("sql 不能为空")
    normalized = sql.strip()
    without_terminal_semicolon = normalized.rstrip(";").rstrip()
    if ";" in without_terminal_semicolon or not READ_ONLY_SQL.match(without_terminal_semicolon):
        raise ValueError("只允许单条 SELECT 或 WITH 查询")
    return without_terminal_semicolon


def database_config() -> dict[str, Any]:
    return {
        "host": os.getenv("DB_HOST", "127.0.0.1"),
        "port": int(os.getenv("DB_PORT", "3306")),
        "user": os.getenv("DB_USER", "root"),
        "password": os.getenv("DB_PASSWORD", ""),
        "database": os.getenv("DB_NAME") or None,
        "connect_timeout": int(os.getenv("DB_CONNECT_TIMEOUT", "5")),
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


def application(environ: dict[str, Any], start_response):
    method = environ.get("REQUEST_METHOD", "GET").upper()
    path = environ.get("PATH_INFO", "/")
    try:
        if method == "GET" and path == "/":
            return json_response(start_response, HTTPStatus.OK, envelope({"service": "dbapi", "status": "UP"}))
        if method == "GET" and path == "/health":
            return json_response(start_response, HTTPStatus.OK, envelope({"status": "UP"}))
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
        if method == "POST" and path == "/api/v1/db/query":
            body = read_json(environ)
            try:
                sql = validate_read_only_sql(body.get("sql"))
            except ValueError as exc:
                return json_response(start_response, HTTPStatus.BAD_REQUEST, error("INVALID_QUERY", str(exc)))
            params = body.get("params", [])
            if not isinstance(params, list):
                return json_response(start_response, HTTPStatus.BAD_REQUEST, error("INVALID_PARAMS", "params 必须是数组"))
            columns, rows = run_query(sql, params)
            return json_response(start_response, HTTPStatus.OK, envelope({"columns": columns, "rows": rows, "rowCount": len(rows)}))
        return json_response(start_response, HTTPStatus.NOT_FOUND, error("NOT_FOUND", "接口不存在"))
    except json.JSONDecodeError:
        return json_response(start_response, HTTPStatus.BAD_REQUEST, error("INVALID_JSON", "请求体不是合法 JSON"))
    except Exception:
        return json_response(start_response, HTTPStatus.SERVICE_UNAVAILABLE, error("DATABASE_UNAVAILABLE", "数据库暂不可用"))


if __name__ == "__main__":
    host = os.getenv("DBAPI_HOST", "0.0.0.0")
    port = int(os.getenv("DBAPI_PORT", "8000"))
    print(f"dbapi listening on {host}:{port}")
    make_server(host, port, application).serve_forever()
