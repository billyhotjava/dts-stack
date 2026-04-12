"""Smoke tests for /test/** helper endpoints."""
import pytest


@pytest.mark.smoke
def test_admin_ping(admin_test_api):
    r = admin_test_api.get("/test/ping", expect=200)
    body = r.json()
    assert body["status"] == "ok"
    assert body["module"] == "admin"


@pytest.mark.smoke
def test_platform_ping(platform_test_api):
    r = platform_test_api.get("/test/ping", expect=200)
    body = r.json()
    assert body["status"] == "ok"
    assert body["module"] == "platform"


@pytest.mark.smoke
def test_ping_requires_token(admin_client):
    # The admin_client has a Bearer token but NO X-Test-Token — must be rejected
    r = admin_client.get("/test/ping")
    assert r.status_code == 401
