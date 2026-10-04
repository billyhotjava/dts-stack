# DTS API 测试 —— 外部同事入门指南

欢迎。本包包含对 DTS 后端（`dts-admin` + `dts-platform`）做 API 级回归的两套独立骨架，**二选一**：

- **Python**（推荐：门槛低）：`api-e2e-python/` — pytest + requests + allure
- **Java**（熟悉 Java 同事优先）：`api-e2e-java/` — JUnit 5 + RestAssured + allure

两套覆盖范围等价，互相不依赖。目录内各自带 README，下面是**最短上手路径**。

---

## 0. 测试环境信息（已部署）

| 项 | 值 |
|---|---|
| Admin 基础 URL | `https://biadmin.yuzhicloud.com` |
| Platform 基础 URL | `https://bi.yuzhicloud.com` |
| Keycloak Issuer | `https://sso.yuzhicloud.com/realms/S10` |
| OIDC Client ID | `dts-system` |
| 三员账号示例 | `sysadmin` / `authadmin` / `auditadmin` |
| 平台账号 | 向管理员申请 |
| 证书 | 自签，`VERIFY_TLS=false` |

> 需要从管理员拿：`OIDC_CLIENT_SECRET`、平台账号密码、`TEST_API_TOKEN`（用于 `/test/**` 辅助接口，测试环境已启用）。

---

## 1. Python 套件 —— 最快路径

```bash
cd api-e2e-python

# 创建 venv 并安装
python3 -m venv .venv && source .venv/bin/activate
pip install -e .

# 配置环境
cp .env.example .env
# 编辑 .env：填入 OIDC_CLIENT_SECRET、账号密码、TEST_API_TOKEN
#   ADMIN_BASE_URL=https://biadmin.yuzhicloud.com
#   PLATFORM_BASE_URL=https://bi.yuzhicloud.com

# 跑 smoke（/test/ping）
bash run.sh smoke

# 跑全量
bash run.sh all

# 查看 allure 报告
allure serve reports/allure-results
```

## 2. Java 套件 —— 最快路径

```bash
cd api-e2e-java

cp .env.example .env   # 编辑同 Python

bash run.sh smoke      # 用 maven 跑 @Tag("smoke")
bash run.sh all

mvn -q allure:serve
```

## 3. 查看所有 API（Swagger 已生成）

**最简方式：浏览器**

1. 先在浏览器登录对应前端：
   - Admin：`https://biadmin.yuzhicloud.com/`（三员账号）
   - Platform：`https://bi.yuzhicloud.com/`（平台账号）
2. 同一浏览器打开：
   - `https://biadmin.yuzhicloud.com/swagger-ui.html` — admin 所有端点
   - `https://bi.yuzhicloud.com/swagger-ui.html` — platform 所有端点
3. 端点、参数、Schema、请求示例均可直接浏览/试调

**导入 Apifox / Postman**

登录后直接访问 `/v3/api-docs`（同域名），右键 "另存为" 得到 OpenAPI 3 JSON，导入工具即可生成调用集合。

**命令行拉 JSON 快照**（可选）

```bash
cd openapi
INSECURE=1 source get-token.sh --target admin <三员账号> <password>
INSECURE=1 source get-token.sh --target platform <平台账号> <password>
HOST_ADMIN_UI=biadmin.yuzhicloud.com HOST_PLATFORM_UI=bi.yuzhicloud.com \
  bash fetch.sh --mode traefik --insecure
# 产物：openapi/snapshots/dts-admin.json 和 dts-platform.json
```

> 注：该路径目前需要额外排查 audience 配置，推荐优先用浏览器方式。

---

## 4. 写新用例的要点

- **Python**：参考 `api-e2e-python/cases/admin/test_auth_smoke.py` 的写法；用 `admin_client` / `platform_client` fixture，自动带 Bearer token
- **Java**：继承 `BaseAdminTest` / `BasePlatformTest`，用 `admin()` / `platform()` 返回的 `RequestSpecification`
- 用 `@Tag("smoke")` 标记快速冒烟用例，其它用 `admin` / `platform` 标记归属
- 断言顺序：**先状态码，再业务 payload**
- 幂等为先：构造数据请调 `/test/seed/{scenario}` 而不是依赖已有生产数据

## 5. `/test/**` 辅助接口

测试环境专用的数据重置/灌入接口，用 `X-Test-Token` 头鉴权（`TEST_API_TOKEN` 环境变量）。

- `GET  /test/ping` — 联通探活（已实现）
- `POST /test/reset-data` — 重置测试数据（当前 501 stub，待补实现）
- `POST /test/seed/{scenario}` — 灌入场景（当前 501 stub）
- `POST /test/clock/advance` — 时间穿越（当前 501 stub）

具体合同见 `api-helpers/README.md`。如果某用例依赖已填充的某场景数据，请先联系项目方实现对应 scenario。

---

## 6. 反馈渠道

- Bug / 疑问：在 issue tracker 提单
- 安全敏感数据（token / 密码）：走加密通道，勿发明文群聊
