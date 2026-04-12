"""Shared fixtures for API E2E tests."""
from __future__ import annotations

import logging
import os
from pathlib import Path

import pytest
import urllib3
from dotenv import load_dotenv

from clients import ApiClient, fetch_token

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

ROOT = Path(__file__).parent
load_dotenv(ROOT / ".env")
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


def _env(name: str, required: bool = False, default: str = "") -> str:
    v = os.environ.get(name, default)
    if required and not v:
        pytest.skip(f"env {name} not set — skipping this case")
    return v


def _verify_tls() -> bool:
    return os.environ.get("VERIFY_TLS", "false").lower() in {"1", "true", "yes"}


# ---------- session-scoped tokens ----------

@pytest.fixture(scope="session")
def admin_token() -> str:
    return fetch_token(
        issuer=_env("OIDC_ISSUER_URI", required=True),
        client_id=_env("OIDC_CLIENT_ID", required=True),
        client_secret=_env("OIDC_CLIENT_SECRET") or None,
        username=_env("ADMIN_USERNAME", required=True),
        password=_env("ADMIN_PASSWORD", required=True),
        verify_tls=_verify_tls(),
    )


@pytest.fixture(scope="session")
def platform_token() -> str:
    return fetch_token(
        issuer=_env("OIDC_ISSUER_URI", required=True),
        client_id=_env("OIDC_CLIENT_ID", required=True),
        client_secret=_env("OIDC_CLIENT_SECRET") or None,
        username=_env("PLATFORM_USERNAME", required=True),
        password=_env("PLATFORM_PASSWORD", required=True),
        verify_tls=_verify_tls(),
    )


# ---------- clients ----------

@pytest.fixture(scope="session")
def admin_client(admin_token) -> ApiClient:
    return ApiClient(
        base_url=_env("ADMIN_BASE_URL", required=True),
        token=admin_token,
        verify_tls=_verify_tls(),
    )


@pytest.fixture(scope="session")
def platform_client(platform_token) -> ApiClient:
    return ApiClient(
        base_url=_env("PLATFORM_BASE_URL", required=True),
        token=platform_token,
        verify_tls=_verify_tls(),
    )


@pytest.fixture(scope="session")
def admin_test_api() -> ApiClient:
    """Client for /test/** on admin, authenticated via X-Test-Token only."""
    return ApiClient(
        base_url=_env("ADMIN_BASE_URL", required=True),
        extra_headers={"X-Test-Token": _env("TEST_API_TOKEN", required=True)},
        verify_tls=_verify_tls(),
    )


@pytest.fixture(scope="session")
def platform_test_api() -> ApiClient:
    return ApiClient(
        base_url=_env("PLATFORM_BASE_URL", required=True),
        extra_headers={"X-Test-Token": _env("TEST_API_TOKEN", required=True)},
        verify_tls=_verify_tls(),
    )
