# ELT-006：E2E 冒烟与人工异常路径验收

## 目标

把接入中心和开发中心的关键用户路径与异常路径固化成可执行的验收清单。

## E2E 优先覆盖

### 接入中心

- 创建任务
- 执行任务
- 查看状态 / 日志 / 历史

### 开发中心

- compile
- test
- build
- 查看运行记录 / 错误信息

## 人工异常路径

- DAG 未注册
- DAG 已注册但 paused
- Airflow 401/403/500
- 触发成功但状态同步延迟
- 运行失败但页面提示不准确
- rollback / rebuild 的部分成功场景

## 交付标准

- 新增 E2E 冒烟方向明确
- 人工异常路径清单明确
- 结果沉淀到 `req/` 和 sprint 验收材料

## 本轮落地

### 新增自动化冒烟

- `tests/web-e2e/specs/biz/elt-ingestion-center-smoke.spec.ts`
  - 覆盖：任务列表 -> 异步执行 -> 详情页 -> 最新日志
- `tests/web-e2e/specs/biz/elt-development-center-smoke.spec.ts`
  - 覆盖：逻辑建模工作区 -> compile -> test -> build -> 运行记录

### 本轮定位并收口的真实问题

- 平台本地 dev server 使用 `hash` 路由，E2E 不能再假设 `/expert/<path>` 是 browser-history 路径
- `modeling/sql` 依赖菜单树动态解析，shell mock 为空菜单时会导致页面只显示空壳层
- 接入中心列表页的任务名是可点击文本，但不是语义化 `link`
- 执行成功弹窗会挡住列表页后续点击，page object 需要先关闭再进入详情页
- 详情页和接入中心入口都存在多处同文案节点，断言必须收敛到页面根节点或更具体的 DOM

### 已验证命令

```bash
set -a; source .env; set +a
DTS_WEB_E2E_USE_TEST_SERVER=0 \
DTS_PLATFORM_URL=http://127.0.0.1:19349/expert/ \
DTS_BASE_URL=http://127.0.0.1:19349/expert/ \
DTS_EXPERT_URL=http://127.0.0.1:19349/expert/ \
DTS_PLATFORM_AUTH_TOKEN=platform-playwright-token \
DTS_ADMIN_AUTH_TOKEN=admin-playwright-token \
DTS_PLATFORM_REQUIRE_PASSWORD_LOGIN=0 \
DTS_ADMIN_REQUIRE_PASSWORD_LOGIN=0 \
tests/web-e2e/node_modules/.bin/playwright test \
  tests/web-e2e/specs/biz/elt-ingestion-center-smoke.spec.ts \
  tests/web-e2e/specs/biz/elt-development-center-smoke.spec.ts \
  --project=chromium \
  --config=tests/web-e2e/playwright.config.ts
```

结果：

- `2 passed (10.8s)`

### 新增稳定选择器契约

- `source/dts-platform-webapp/src/pages/explore/etl/TransformPage.tsx`
- `source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `tests/web-e2e/specs/contracts/testid-contract.spec.ts`

### 新增人工异常路径手册

- `worklog/v2.2.2/sprint-6/req/elt-manual-failure-runbook.md`
