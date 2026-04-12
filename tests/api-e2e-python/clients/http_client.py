"""Thin requests-based HTTP client with auth and logging."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping
import logging

import requests

LOG = logging.getLogger(__name__)


@dataclass
class ApiClient:
    base_url: str
    token: str | None = None
    extra_headers: Mapping[str, str] = field(default_factory=dict)
    verify_tls: bool = False
    timeout: float = 15.0

    def _headers(self, extra: Mapping[str, str] | None = None) -> dict[str, str]:
        h = {"Accept": "application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        h.update(self.extra_headers or {})
        if extra:
            h.update(extra)
        return h

    def request(
        self,
        method: str,
        path: str,
        *,
        json: Any = None,
        params: Mapping[str, Any] | None = None,
        headers: Mapping[str, str] | None = None,
        expect: int | tuple[int, ...] | None = None,
    ) -> requests.Response:
        url = f"{self.base_url.rstrip('/')}{path}"
        LOG.info("HTTP %s %s", method, url)
        r = requests.request(
            method,
            url,
            json=json,
            params=params,
            headers=self._headers(headers),
            verify=self.verify_tls,
            timeout=self.timeout,
        )
        if expect is not None:
            ok = {expect} if isinstance(expect, int) else set(expect)
            if r.status_code not in ok:
                raise AssertionError(
                    f"{method} {url} expected {expect} got {r.status_code}: {r.text[:500]}"
                )
        return r

    def get(self, path: str, **kw: Any) -> requests.Response:
        return self.request("GET", path, **kw)

    def post(self, path: str, **kw: Any) -> requests.Response:
        return self.request("POST", path, **kw)

    def put(self, path: str, **kw: Any) -> requests.Response:
        return self.request("PUT", path, **kw)

    def delete(self, path: str, **kw: Any) -> requests.Response:
        return self.request("DELETE", path, **kw)
