# PKI Login (Admin Gateway) — Placeholder

This repo ships a disabled-by-default PKI login entry on the admin service. It adds a future-proof endpoint without affecting current username/password flow.

## Current Behavior
- Endpoint: `POST /api/keycloak/auth/pki-login` (admin)
- Default: returns 404 when feature disabled; returns 501 when enabled but not integrated.
- No changes to existing endpoints:
  - Admin: `/api/keycloak/auth/login|refresh|logout` (JWT)
  - Platform: `/api/keycloak/auth/login|refresh|logout` (opaque portal tokens)

## Enable Flags (admin)
Environment variables (defaults shown in `.env`):
- `DTS_PKI_ENABLED=false`
- `DTS_PKI_MODE=disabled` (planned: `mtls` or `assertion`)
- `DTS_PKI_ACCEPT_FORWARDED_CERT=false`
- `DTS_PKI_CLIENT_CERT_HEADER=X-Forwarded-Tls-Client-Cert`
- `DTS_PKI_ISSUER_CN=`
- `DTS_PKI_API_BASE=`
- `DTS_PKI_API_TOKEN=`
- `DTS_PKI_API_TIMEOUT=3000`
- `DTS_PKI_ALLOW_MOCK=false`

These map to `dts.pki.*` in `source/dts-admin/src/main/resources/config/application.yml`.

## Verify (without integration)
- With defaults (disabled):
  - `curl -k https://admin.${BASE_DOMAIN}/api/keycloak/auth/pki-login` → 404
- Enable the flag:
  - Set `DTS_PKI_ENABLED=true` and restart admin
  - `curl -k -X POST https://admin.${BASE_DOMAIN}/api/keycloak/auth/pki-login -d '{}' -H 'Content-Type: application/json'` → 501

## Next Steps (integration outline)
- `mtls` mode: terminate client TLS at ingress for `/api/keycloak/auth/pki-login`, forward cert via header; admin validates chain/issuer and maps subject to Keycloak user.
- `assertion` mode: accept PKI-signed assertion payload (`assertion`, `sign`, `nonce`), call PKI API for verification, then map to Keycloak user.
- On success, admin returns the same shape as password login: `{ user, accessToken, refreshToken, ... }`.

No other services require changes to adopt PKI later; platform continues calling admin for login.

## Traefik mTLS Route (example)

Goal: enable client certificate authentication only for the admin PKI login endpoint, without touching other routes.

Prerequisites
- Mount CA bundle into Traefik proxy, e.g. `/etc/traefik/pki/ca.crt`.
- Enable file provider for Traefik (existing installations usually do).

1) File provider (tls options + middleware)

Create a dynamic config file like `services/dts-proxy/conf/pki-mtls.yml`:

```yaml
tls:
  options:
    pki-mtls:
      clientAuth:
        clientAuthType: RequireAndVerifyClientCert
        caFiles:
          - /etc/traefik/pki/ca.crt
http:
  middlewares:
    pki-client-cert:
      passTLSClientCert:
        pem: true
```

2) dts-admin service labels (extra router for PKI login)

Add the following labels to the `dts-admin` service (compose snippet):

```yaml
    labels:
      - "traefik.http.routers.dts-admin-pki.rule=Host(`${HOST_ADMIN_UI}`) && Path(`/api/keycloak/auth/pki-login`)"
      - "traefik.http.routers.dts-admin-pki.entrypoints=websecure"
      - "traefik.http.routers.dts-admin-pki.tls=true"
      - "traefik.http.routers.dts-admin-pki.tls.options=pki-mtls@file"
      - "traefik.http.routers.dts-admin-pki.service=dts-admin"
      - "traefik.http.routers.dts-admin-pki.middlewares=pki-client-cert@file,security-headers@file"
```

Notes
- This creates an additional router only for the PKI login path under `admin.${BASE_DOMAIN}`.
- The `passTLSClientCert` middleware injects `X-Forwarded-Tls-Client-Cert` header with PEM content.
- Keep existing routers unchanged; this addition is non-destructive.

3) Admin application flags

Set the following env vars for admin to recognize forwarded client cert:

```bash
DTS_PKI_ENABLED=true
DTS_PKI_MODE=mtls
DTS_PKI_ACCEPT_FORWARDED_CERT=true
# Optional issuer filter
DTS_PKI_ISSUER_CN=""
```

4) Quick check (placeholder behavior)

```bash
# Expect 501 (Not Implemented) when feature is enabled but integration not yet wired
curl -k -X POST https://admin.${BASE_DOMAIN}/api/keycloak/auth/pki-login -H 'Content-Type: application/json' -d '{}'
```

When full PKI integration is developed, the endpoint will return the same payload shape as password login.
