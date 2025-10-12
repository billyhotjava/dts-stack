# PKI Login (Admin Gateway) — Platform Only

This repo ships a disabled-by-default PKI login entry on the admin service. It adds a future-proof endpoint without affecting current username/password flow.

## Design Decision
- Admin端仅内置三用户可登录：`sysadmin`、`authadmin`、`auditadmin`（不对接 PKI）。
- 普通用户登录业务平台（platform）通过 PKI；后台在验证签名后，为该用户向 Keycloak 发起 Token Exchange（impersonation），返回与口令登录一致的数据结构。

## Current Behavior
- Endpoints (admin):
  - `GET /api/keycloak/auth/pki-challenge` — issue a short‑lived challenge (nonce)
  - `POST /api/keycloak/auth/pki-login` — verify PKCS#7 signature (agent/gateway)
- Default: returns 404 when feature disabled; returns 501/501-like when verification/mapping not configured.
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

## Token Issuance (finalized)
- 策略：Keycloak Token Exchange（优先）
- 前置条件（Keycloak 管理端）：
  - 在 Realm 级别启用 Token Exchange。
  - 为前端使用的 OIDC Client（本项目复用 `OAUTH2_PLATFORM_CLIENT_ID`）开启 Service Account，并在 `realm-management` 客户端分配必要角色（允许 token-exchange 与 impersonation）。
  - 平台用户已在 Keycloak 中存在；或通过管理员流程在本系统“审批启用”。
- 流程：
  1) Admin 服务校验 `challenge + PKCS#7` 签名（可选：验证签发 CA/Issuer）。
  2) 从证书 `Subject DN` 解析出用户名（优先 `UID`，其次 `CN`），并校验是否已在本系统审批启用（内置四个保护账号允许跳过审批：`sysadmin/authadmin/auditadmin/opadmin`）。
  3) 使用 OIDC Client 的 Service Account 获取 `subject_token`，调用 Keycloak token 端点执行 `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`，`requested_subject=<username>`，获得平台用的 `access_token/refresh_token`。
  4) 返回 `{ user, accessToken, refreshToken, ... }`，与口令登录一致。

注意：Admin（三员）不会走 PKI；平台端会明确拒绝三员通过 PKI 登录。

## Next Steps (integration outline)
- `agent` mode: Browser talks to local PKI agent (Thrift on 127.0.0.1), performs `verifyPIN` and `signMessage` (PKCS#7) on the server‑issued `plain` string. FE posts `{challengeId, plain, p7Signature, certB64?}` to admin for verification.
- `gateway` mode: FE posts `{challengeId, plain, p7Signature, certB64?}` and admin verifies via vendor middleware (Koal SVS) using a vendor JAR loaded at runtime.
- `mtls` mode: terminate client TLS at ingress for `/api/keycloak/auth/pki-login`, forward cert via header; admin validates chain/issuer and maps subject to Keycloak user.
- On success, admin returns the same shape as password login: `{ user, accessToken, refreshToken, ... }`。

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

4) Quick check (enabled behavior)

```bash
# Expect 401/400 when params missing; 200 with tokens when集成完成
curl -k -X POST https://admin.${BASE_DOMAIN}/api/keycloak/auth/pki-login -H 'Content-Type: application/json' -d '{"challengeId":"...","plain":"...nonce...","p7Signature":"...","certB64":"...","mode":"agent"}'
```

When full PKI integration is developed, the endpoint will return the same payload shape as password login.

## Agent + Gateway usage (current)

1) Enable feature flags

Add to `.env` (examples):

```
DTS_PKI_ENABLED=true
DTS_PKI_MODE=gateway   # or agent / mtls / disabled
DTS_PKI_GATEWAY_HOST=127.0.0.1
DTS_PKI_GATEWAY_PORT=8008
DTS_PKI_DIGEST=SHA1
# Optional: path to vendor jar (mounted into admin container)
DTS_PKI_VENDOR_JAR=/opt/dts/vendor/svs-uk_custom.jar
```

2) Mount vendor JAR (gateway)

- Place `svs-uk_custom.jar` under a host path and mount into admin service as `/opt/dts/vendor/svs-uk_custom.jar`, or set another path via `DTS_PKI_VENDOR_JAR`.

3) Flow (agent)

- FE GET `.../api/keycloak/auth/pki-challenge` → `{challengeId, nonce, ts, exp}`
- Build canonical `plain` (include `nonce`), then request local agent to produce `p7Signature` (PKCS#7) and export `certB64`.
- FE POST `.../api/keycloak/auth/pki-login` with `{challengeId, plain, p7Signature, certB64, mode:"agent"}`.
- Admin verifies signature via vendor JAR (if configured). Mapping to tokens will be wired next; current builds return 501 with identity details on success.

4) Flow (gateway)

- Same as agent except FE skips the local agent step; signature comes from a central page/widget or an existing gateway SDK.
- Admin verifies via vendor JAR and records signer identity.

Notes
- 当未设置 `DTS_PKI_VENDOR_JAR` 时，服务端无法完成 PKCS#7 验签，将返回明确提示。
- `DTS_PKI_ALLOW_MOCK=true` 仅用于联调：可在请求中指定 `username`，绕过证书映射；正式环境请关闭。
