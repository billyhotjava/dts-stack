"""Fetch OIDC access_token via password grant."""
from __future__ import annotations

import requests


def fetch_token(
    issuer: str,
    client_id: str,
    client_secret: str | None,
    username: str,
    password: str,
    verify_tls: bool = False,
    timeout: float = 15.0,
) -> str:
    url = f"{issuer.rstrip('/')}/protocol/openid-connect/token"
    data = {
        "grant_type": "password",
        "client_id": client_id,
        "username": username,
        "password": password,
        "scope": "openid",
    }
    if client_secret:
        data["client_secret"] = client_secret
    r = requests.post(
        url,
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        verify=verify_tls,
        timeout=timeout,
    )
    if r.status_code != 200:
        raise RuntimeError(
            f"Keycloak password grant failed [{r.status_code}]: {r.text[:500]}"
        )
    return r.json()["access_token"]
