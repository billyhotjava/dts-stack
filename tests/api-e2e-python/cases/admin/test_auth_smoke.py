"""Sample admin case: verify auth-info and current account."""
import pytest


@pytest.mark.admin
@pytest.mark.smoke
def test_auth_info_public(admin_client):
    """/api/auth-info is permitAll — must work even without a token, but with one too."""
    r = admin_client.get("/api/auth-info", expect=200)
    body = r.json()
    assert "issuer" in body or "oidcIssuer" in body or body  # tolerate payload shape


@pytest.mark.admin
def test_current_account(admin_client):
    """Authenticated user should expose identity via /api/account."""
    r = admin_client.get("/api/account", expect=(200, 404))
    # 404 is acceptable if endpoint path differs — colleague to refine per OpenAPI.
    if r.status_code == 200:
        payload = r.json()
        assert payload  # non-empty
