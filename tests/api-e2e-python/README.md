# API E2E 测试（Python）

基于 pytest + requests + allure 的 API 级回归套件，面向熟悉 Python 的测试同事。

## 目录结构

```
tests/api-e2e-python/
├── pyproject.toml        # 依赖 & pytest 配置
├── conftest.py           # 共享 fixtures（token、client、test-api）
├── clients/              # 精简 HTTP 客户端（封装 auth / base url）
├── cases/
│   ├── admin/            # 三员 API 用例
│   ├── platform/         # 平台 API 用例
│   └── test_api_helpers/ # /test/** 辅助接口自测
├── run.sh                # 统一入口
└── reports/              # allure 原始数据（gitignored）
```

## 快速开始

```bash
# 1. 安装依赖（建议用 venv）
cd tests/api-e2e-python
python3 -m venv .venv && source .venv/bin/activate
pip install -e .

# 2. 配置环境（复制 .env.example 并修改）
cp .env.example .env

# 3. 运行
bash run.sh smoke        # 只跑 test_api_helpers（最快）
bash run.sh admin        # 只跑 admin 用例
bash run.sh platform     # 只跑 platform 用例
bash run.sh all          # 全量

# 4. 报告
allure serve reports/allure-results
```

## 环境变量（`.env`）

| 变量 | 说明 | 示例 |
|---|---|---|
| `ADMIN_BASE_URL` | admin 模块基础 URL | `http://127.0.0.1:18081` |
| `PLATFORM_BASE_URL` | platform 模块基础 URL | `http://127.0.0.1:18082` |
| `OIDC_ISSUER_URI` | Keycloak realm issuer | `https://sso.yuzhicloud.com/realms/S10` |
| `OIDC_CLIENT_ID` | Keycloak client | `dts-system` |
| `OIDC_CLIENT_SECRET` | client secret | `vze3sg...` |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 三员账号 | `sysadmin` / `sa` |
| `PLATFORM_USERNAME` / `PLATFORM_PASSWORD` | 平台账号 | `biuser` / `...` |
| `TEST_API_TOKEN` | `/test/**` 的 `X-Test-Token` | `...` |
| `VERIFY_TLS` | 是否校验 TLS 证书 | `false`（自签） |

## 写新用例

参考 `cases/admin/test_auth_smoke.py`。规则：

1. 优先使用 `admin_client` / `platform_client` fixture（自动带 token）
2. 断言先判 HTTP 状态码再判业务 payload
3. 需要构造数据时走 `test_api_client` fixture 调 `/test/**`
4. 独立、幂等 —— 不依赖其他用例的副作用
