# Baseline commands

执行时间：2026-07-30（Asia/Shanghai）

## 认证与浏览器

```bash
PLAYWRIGHT_EXECUTABLE_PATH=/usr/bin/google-chrome \
E2E_BASE_URL=https://bi.yuzhicloud.com \
E2E_USERNAME='<ephemeral-user>' \
E2E_PASSWORD='<process-only-random-secret>' \
pnpm exec playwright test e2e/sprint79-workspace-baseline.spec.ts --project=chromium
```

执行目录：`source/dts-platform-webapp`。

测试身份由同一进程创建和清理：

1. Keycloak Realm `S10` 创建 `sprint79-e2e-*` 一次性用户；
2. 仅授予 `ROLE_MODEL_MAINTAINER`；
3. 在 `dts_admin.admin_keycloak_user` 创建同 `username + kc_id` 的启用快照；
4. 测试退出钩子删除快照、Keycloak 用户和 Playwright storage state。

命令、日志和证据不保存真实随机口令或 token。

## 清理断言

```text
Keycloak temp users: 0
Admin snapshot temp rows: 0
Auth storage exists: no
```
