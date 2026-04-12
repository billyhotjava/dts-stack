"""Sample platform case: the test-api ping is the guaranteed always-green smoke.
Replace with real business endpoints once OpenAPI snapshot is pulled."""
import pytest


@pytest.mark.platform
@pytest.mark.smoke
def test_platform_test_api_reachable(platform_test_api):
    r = platform_test_api.get("/test/ping", expect=200)
    assert r.json()["module"] == "platform"


@pytest.mark.platform
def test_platform_auth_info(platform_client):
    r = platform_client.get("/api/auth-info")
    assert r.status_code in (200, 401, 404)  # probe, not a contract assertion
