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

## 格尔 PKI 网关错误码

以下整理了当前对接文档提供的错误码、含义以及可能的处理建议，便于排查前后端联调问题。

### 网关接口（0x000000**）

| 预定义值 | 说明 | 可能导致错误的原因及解决办法 |
| --- | --- | --- |
| `0x00000000` | 调用正常 | 正常 |
| `0x00000001` | session不存在 | 未调用login接口或调用了logout后仍调用业务接口，请先调用login接口 |
| `0x00000003` | session已经注册了Notify | 未调用logout接口,同时多次调用getNotify接口，请在完成业务调用后调用logout接口 |
| `0x00000004` | 消息类型错误 | 暂无官方说明 |
| `0x00000005` | 消息JsonBody无效/缺少参数 | 传入接口的参数类型不匹配、缺少、不合法等，请对照接口文档检查 |
| `0x00000006` | app已经登录 | 正常。多次调用login时导致，建议在业务完成时调用logout接口 |
| `0x00000007` | 超时 | 接口调用时长大于超时上限（30s），可能是程序阻塞或者接口调用时存在人机交互的设计 |
| `0x00000008` | 登录参数未授权认证 | 登录接口参数不合法，未使用分配的登录参数 |
| `0x00000009` | 可信驱动已设置 | 暂无官方说明 |
| `0x0000000A` | 可信驱动未设置 | 暂无官方说明 |
| `0x0000000B` | 获取登录临时参数失败 | 暂无官方说明 |
| `0x0000000C` | 调用失败 | 调用失败，请结合接口失败时返回的错误信息定位 |
| `0x0000000D` | 内存不足 | 计算机内存不足 |
| `0x0000000E` | openssl接口调用失败 | 调用失败，请结合接口失败时返回的错误信息定位 |

### 驱动与设备（0x0A******、0x0B******、0x0D******）

| 预定义值 | 说明 | 可能导致错误的原因及解决办法 |
| --- | --- | --- |
| `0x0A000001` | 失败 | 驱动接口调用失败。可能是参数异常 |
| `0x0A000002` | 异常错误 | 驱动接口调用失败。可能是参数异常 |
| `0x0A000003` | 不支持的服务 | 驱动不支持。需要检查调用的接口是否正确 |
| `0x0A000004` | 文件操作错误 | 暂无官方说明 |
| `0x0A000005` | 无效的句柄 | 暂无官方说明 |
| `0x0A000006` | 无效的参数 | 暂无官方说明 |
| `0x0A000007` | 读文件错误 | 暂无官方说明 |
| `0x0A000008` | 写文件错误 | 暂无官方说明 |
| `0x0A000009` | 名称长度错误 | 暂无官方说明 |
| `0x0A00000A` | 密钥用途错误 | 可能是传入的参数不正确，请对照接口文档检查 |
| `0x0A00000B` | 模的长度错误 | 暂无官方说明 |
| `0x0A00000C` | 未初始化 | 暂无官方说明 |
| `0x0A00000D` | 对象错误 | 暂无官方说明 |
| `0x0A00000E` | 内存错误 | 暂无官方说明 |
| `0x0A00000F` | 超时 | 可能是驱动阻塞超时，请执行插拔key重试 |
| `0x0A000010` | 输入数据长度错误 | 暂无官方说明 |
| `0x0A000011` | 输入数据错误 | 暂无官方说明 |
| `0x0A000012` | 生成随机数错误 | 暂无官方说明 |
| `0x0A000013` | HASH对象错误 | 暂无官方说明 |
| `0x0A000014` | HASH运算错误 | 暂无官方说明 |
| `0x0A000015` | 产生RSA密钥错误 | 暂无官方说明 |
| `0x0A000016` | RSA密钥模长错误 | 暂无官方说明 |
| `0x0A000017` | CSP服务导入公钥错误 | 暂无官方说明 |
| `0x0A000018` | RSA加密错误 | 暂无官方说明 |
| `0x0A000019` | RSA解密错误 | 暂无官方说明 |
| `0x0A00001A` | HASH值不相等 | 数据错误，请检查传入的数据 |
| `0x0A00001B` | 未发现密钥 | 容器中不存在相应秘钥，请更换介质或签发新证书重试 |
| `0x0A00001C` | 未发现证书 | 容器不存在证书，请在驱动工具中检查待导出证书是否存在 |
| `0x0A00001D` | 对象未导出 | 暂无官方说明 |
| `0x0A00001E` | 解密时做补丁错误 | 暂无官方说明 |
| `0x0A00001F` | MAC长度错误 | 暂无官方说明 |
| `0x0A000020` | 缓冲区不足 | 暂无官方说明 |
| `0x0A000021` | 密钥类型错误 | 暂无官方说明 |
| `0x0A000022` | 无事件错误 | 暂无官方说明 |
| `0x0A000023` | 设备已移除 | 设备拔除、设备松动导致接触不良可重新插拔 |
| `0x0A000024` | PIN错误 | 输入PIN码与预置值不匹配 |
| `0x0A000025` | PIN锁死 | PIN码错误次数太多，请使用驱动工具或调用unlockPIN解锁 |
| `0x0A000026` | PIN无效 | 请检查确认正确PIN重试 |
| `0x0A000027` | PIN长度错误 | 请设置安全级别较高（复杂度高、长度大于6位）的PIN码 |
| `0x0A000028` | 用户已经登录 | 已经验过PIN |
| `0x0A000029` | 没有初始化用户口令 | 暂无官方说明 |
| `0x0A00002A` | PIN类型错误 | 请检查接口文档后重试 |
| `0x0A00002B` | 应用名称无效 | 请检查输入的应用名 |
| `0x0A00002C` | 应用已经存在 | 请更换或删除已存在的应用 |
| `0x0A00002D` | 用户没有登录 | 未验证PIN，请调用verifyPin接口校验pin |
| `0x0A00002E` | 应用不存在 | 请新建或更换已存在的应用名 |
| `0x0A00002F` | 文件已经存在 | 请更换或删除已存在的文件 |
| `0x0A000030` | 存储空间不足 | 介质空间不足，请清理删除废弃数据后重试 |
| `0x0A000031` | 文件不存在 | 请新建或更换已存在的文件名 |
| `0x0A000032` | 已达到最大可管理容器数 | 容器数量达到上限，请清理删除废弃数据后重试 |
| `0x0B000035` | 容器不存在 | 请新建或更换已存在的容器名 |
| `0x0B000036` | 容器已存在 | 请更换或删除已存在的文件名 |
| `0x0D000000` | 源数据过长 | 数据长度过大，请对照接口文档检查 |
| `0x0D000001` | 设备不存在 | 请确认驱动是否安装或重新插拔key测试 |
| `0x0D000002` | 应用打开失败 | 可能是程序异常，请联系研发反馈日志 |
| `0x0D000003` | 容器打开失败 | 可能是程序异常，请联系研发反馈日志 |
| `0x0D000004` | 容器中无密钥对 | 请检查介质中秘钥对是否正常或重新发证测试 |
| `0x0D000005` | 加密密钥对结构转换失败 | 可能是数据错误，请检查核对加密密钥对数据 |
| `0x0D000006` | 字段加密失败 | 暂无官方说明 |
| `0x0D000007` | 字段解密失败 | 暂无官方说明 |
| `0x0D000008` | 写缓存失败 | 暂无官方说明 |
| `0x0D000009` | 读缓存失败 | 暂无官方说明 |
| `0x0D00000A` | 应用名编码格式不为utf8编码 | 中文应用名请使用utf-8编码 |
| `0x0D00000B` | 容器名编码格式不为utf8编码 | 中文容器名请使用utf-8编码 |
| `0x0D00000C` | 秘钥为空 | 暂无官方说明 |
