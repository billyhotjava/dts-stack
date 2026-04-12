# OpenAPI 规范导出

本目录存放 `dts-admin` 与 `dts-platform` 的 OpenAPI 3 规范快照，供外部测试同事生成客户端 SDK 或在 Postman/Apifox 中调试。

## 前置条件

服务必须以启用 `api-docs` profile 运行（JHipster 里 `dev` profile 默认已自动带上，`prod` 默认关闭）。如果部署的是生产镜像但又需要导出，请临时加启动参数 `--spring.profiles.active=prod,api-docs`。

验证是否开启：

```bash
curl -sSf https://${HOST_ADMIN_UI}/api/v3/api-docs -o /dev/null && echo OK
```

## 一键导出

**admin 与 platform 是独立登录体系**：

| 模块 | 登录账号 | Keycloak client（默认） | 访问 `/v3/api-docs/**` 需要 |
|---|---|---|---|
| dts-admin | 三员用户（SYS_ADMIN / AUTH_ADMIN / AUDITOR_ADMIN） | `dts-admin` | 任一三员角色 |
| dts-platform | 平台用户 | `dts-platform` | 平台 `ROLE_ADMIN` |

因此需要**两个 token**分别拉取：

```bash
# 1. 分别取两端 token（source 形式会 export ADMIN_TOKEN / PLATFORM_TOKEN）
source tests/openapi/get-token.sh --target admin    <sysadmin_user>     <pwd>
source tests/openapi/get-token.sh --target platform <platform_admin>    <pwd>

# 2. 拉取
bash tests/openapi/fetch.sh --mode direct                 # 本机 dev（18081/18082）
bash tests/openapi/fetch.sh --mode traefik --insecure     # 经 Traefik

# 也可单独拉某一侧（另一侧 token 还没准备好时）
bash tests/openapi/fetch.sh --mode direct --only admin
```

常用环境变量：`OIDC_ISSUER_URI`、`OIDC_CLIENT_ID`、`OIDC_CLIENT_SECRET`、`INSECURE=1`。

所需 Keycloak client 必须开启 "Direct access grants"。如果只允许授权码流，可先从浏览器 DevTools 里复制 `Authorization: Bearer <jwt>`，再用 `fetch.sh --admin-token <JWT>` / `--platform-token <JWT>` 传入。

产物：

- `tests/openapi/snapshots/dts-admin.json`
- `tests/openapi/snapshots/dts-platform.json`

## 在 Apifox / Postman 中使用

直接导入上述 JSON 文件即可。也可用 `openapi-generator-cli` 生成 Python/Java 客户端：

```bash
# Python
openapi-generator-cli generate -i tests/openapi/snapshots/dts-admin.json \
  -g python -o tests/api-e2e-python/clients/admin

# Java
openapi-generator-cli generate -i tests/openapi/snapshots/dts-platform.json \
  -g java -o tests/api-e2e-java/clients/platform
```

## 生产安全

`springdoc.api-docs.enabled: false` 是 `application.yml` 中默认值，只有激活 `api-docs` profile 才会开启。生产环境请勿把 `api-docs` 写进 `spring.profiles.active`。
