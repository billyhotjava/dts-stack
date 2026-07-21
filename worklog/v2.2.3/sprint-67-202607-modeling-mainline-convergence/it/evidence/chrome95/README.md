# Sprint-67 Chrome 95 浏览器证据

验证时间：2026-07-19 17:06 +08:00
浏览器：`Chromium 95.0.4638.0`
视口：1366x768 和 390x844
覆盖结论：8 个场景均已得到 GREEN 证据。完整运行中 7 个不受最终修复影响的场景通过；修复折叠表单完整取值后，仅复验受影响场景并 1/1 通过，未重复运行无关 7 个场景。

## 命令

```bash
cd source/dts-platform-webapp
pnpm preview --host 127.0.0.1 --port 4173

CHROME95_EXECUTABLE_PATH=/tmp/dts-chrome95-ttQJ8k/chrome-linux/chrome \
  E2E_BASE_URL=http://127.0.0.1:4173 \
  pnpm exec playwright test --config=playwright.sprint67.chrome95.config.ts
```

## 证据图

- `desktop-business-first-form.png`：业务目标起点的条件表单。
- `desktop-business-first.png`：服务端 nextAction 进入业务范围 Tab。
- `desktop-asset-first-form.png`：现有数据起点与初始来源。
- `desktop-asset-first.png`：服务端 nextAction 进入来源盘点 Tab。
- `desktop-recovery.png`：exact plan 在失败重试、返回和刷新后仍保持。
- `narrow-recovery.png`：390x844 窄屏，无 document/body 水平溢出。
- `model-detail-standards.png`：服务端模型上下文下的“模型设计 / 字段设计 / 字段标准”三 Tab，以及只读字段标准版本证据。
- `model-detail-standards-narrow.png`：模型详情在 390x844 下保持可用且无 document/body 水平溢出。
- `dimension-catalog-entry-desktop.png`：规划页按“维度目录 → 模型中心”展示入口，点击后精确透传 planId 并进入 canonical 维度目录。
- `dimension-catalog-entry-narrow.png`：维度目录在 390x844 下无 document/body 水平溢出。
- `dimension-draft-create-narrow.png`：390x844 下登记维度，抽屉进入动画稳定后宽度不超过视口，定义、粒度与维度键可直接保存。

## 边界

该套件在当前 production bundle 和真实 Chrome 95 中运行，但 WarehousePlan/ModelSpec API 由 Playwright 精确拦截。它证明 UI、路由、错误恢复、响应式与浏览器兼容性，不声称 live backend/live auth/权限 E2E。

故意注入的失败只有：

- `GET /api/modeling/warehouse-plans` -> 503，随后列表独立重试成功；
- `GET /api/modeling/warehouse-plans/plan-exact/baseline` -> 503，随后证据独立重试成功。
- `GET /api/modeling/model-specs` -> 503，随后维度目录重试成功且失败态不显示伪空表。

除上述预期 503 外，断言为 0 pageerror、0 传输失败、0 非预期 HTTP >=400 与 0 非预期 console.error。

## F3 真实联动补证

验证时间：2026-07-19 18:08 +08:00

浏览器：`Chromium 95.0.4638.0`

环境：`https://bi.yuzhicloud.com`，未拦截 API

```bash
E2E_BASE_URL=https://bi.yuzhicloud.com \
E2E_F3_PLAN_ID=<isolated-fixture-plan> \
E2E_F3_DOMAIN_ID=<confirmed-domain> \
E2E_F3_DIMENSION_CODE=<isolated-dimension-code> \
PLAYWRIGHT_EXECUTABLE_PATH=/tmp/dts-chromium95-M97EPK/chrome-linux/chrome \
pnpm exec playwright test e2e/sprint67-f3-real.spec.ts --project=chromium --reporter=list
```

结果：1/1 PASS（6.2s）。覆盖 `opadmin` 登录会话、计划 owner 权限、真实 POST、强 ETag CAS PUT、PostgreSQL revision、服务端 gate r1→r2、1440px/390px；零非预期 HTTP >=400、零 pageerror、零 requestfailed。

- `f3-real-dimension-gates-chromium95.png`：真实服务端三阶段门禁桌面证据。
- `f3-real-dimension-gates-chromium95-narrow.png`：390x844 无横向溢出证据。

数据库核对：canonical DIMENSION 主记录 revision=2，dimensionProfile 同时写入主表与 r1/r2 snapshot；当前 checksum 只与 r2 一致。隔离计划、绑定、模型和两个 revision 已按精确 UUID 清理，不保留客户数据。

## F2 真实联动补证

验证时间：2026-07-19 18:42 +08:00

浏览器：`Chromium 95.0.4638.0`

环境：`https://bi.yuzhicloud.com`，未拦截 API

```bash
E2E_BASE_URL=https://bi.yuzhicloud.com \
E2E_F2_PLAN_ID=<isolated-fixture-plan> \
E2E_F2_DOMAIN_ID=<active-public-domain> \
E2E_F2_DOMAIN_NAME=项目管理域 \
PLAYWRIGHT_EXECUTABLE_PATH=/tmp/dts-chromium95-M97EPK/chrome-linux/chrome \
pnpm exec playwright test e2e/sprint67-f2-real.spec.ts --project=chromium --reporter=list
```

结果：1/1 PASS（8.5s）。覆盖 `opadmin` 登录会话、计划 owner 权限、真实 category/policy PUT、强 ETag 旧版本 409、PostgreSQL 两个 edit-unit version、服务端九阶段投影、唯一 blocker/nextAction、1440px/390px；零非预期 HTTP >=400、零 pageerror、零 requestfailed。

- `f2-real-category-policy-chromium95.png`：真实分类与分层基线桌面证据。
- `f2-real-category-policy-chromium95-narrow.png`：390x844 无横向溢出证据。

数据库核对：分类 `CONFIRMED`；分层、命名、历史和时区正文均真实持久化；`business_scope_version=2`、`policy_version=2`。隔离计划及全部子记录已按精确 UUID 清理，计划/domain/policy 残留计数均为 0。

## F4 真实菜单与旧深链补证

验证时间：2026-07-19 22:26 +08:00

浏览器：`Chromium 95.0.4638.0`

环境：`https://bi.yuzhicloud.com`，真实 `opadmin` 登录、真实菜单/ModelSpec API，无 API 拦截。

```bash
E2E_BASE_URL=https://bi.yuzhicloud.com \
PLAYWRIGHT_EXECUTABLE_PATH=/tmp/dts-chromium95-M97EPK/chrome-linux/chrome \
pnpm exec playwright test e2e/sprint67-f4-menu-convergence.spec.ts --project=chromium --reporter=list
```

结果：2 个逻辑场景均 PASS。首次菜单场景暴露的是测试 locator 假设错误；旧深链场景 1/1 已通过。修正 locator 后仅定点复验菜单场景 1/1，通过且未重复运行已通过的旧深链场景。

- `f4-live-modeling-menu-chromium95.png`：真实角色菜单不暴露退役入口；`opadmin` 当前只看到获授权的工作台，canonical 模型中心深链可访问。完整菜单矩阵由真实数据库迁移结果与角色绑定 diff 证明，不把更高权限菜单伪装成当前角色可见。
- `f4-legacy-recovery-chromium95-narrow.png`：旧参数归一为 `planId/modelSpecId`、站外 `returnTo` 被丢弃、无映射对象进入 `NEEDS_CLASSIFICATION`；390px 无横向溢出。

运行状态：`dts-admin` healthy，Liquibase `20260719-01-sprint67-modeling-menu-convergence` 已执行；11 个 menu ID、22 条 visibility 绑定和 2 个角色均保留。

## F3 复核后的 production bundle 回归

验证时间：2026-07-20 01:12 +08:00

浏览器：`Chromium 95.0.4638.0`

结果：受影响场景 2/2 PASS。字段标准编辑弹窗可打开；SUMMARY dependency graph 的 CURRENT/STALE/UNKNOWN 在桌面与 390x844 下可见，且无横向溢出。

- `model-detail-standards.png`、`model-detail-standards-narrow.png`：字段版本展示与配置入口。
- `model-detail-dependencies.png`、`model-detail-dependencies-narrow.png`：上游当前、漂移、不可用及受限摘要。

边界：本节在本次 production bundle 上运行，但 API 使用精确拦截，只证明 UI、Chrome95 和响应式兼容。当前运行容器尚未发布本次新增后端接口；真实 auth/API/PostgreSQL 复验必须在 F6 部署后执行。

## F5 真实旧写冻结与 canonical 指标工作台补证

验证时间：2026-07-20 00:00 +08:00

浏览器：`Chromium 95.0.4638.0`

环境：`https://bi.yuzhicloud.com`，真实用户名密码登录、真实平台 API 和 PostgreSQL，无 API mock。

- 旧 `POST /api/semantic/business-objects` 返回 HTTP 410、`BUSINESS_OBJECT_RETIRED`，并携带 `Deprecation`、`Sunset`、successor `Link`。
- `#/modeling/metric-workbench` 只消费 canonical ModelSpec/metric projection；1366px 与 390px 均无水平溢出，console error 为 0。
- `f5-canonical-metric-workbench-chromium95.png`：桌面 canonical 模型锚点与指标工作区。
- `f5-canonical-metric-workbench-chromium95-narrow.png`：390x844 堆叠布局与无横向溢出证据。

本次验收调用被审计为 2 条，故退出门禁明确返回 `NO-DROP`；该调用数是物理退役阻塞证据，不被伪报为零消费者。

## F6-T01 标准与指标专业交接回归

验证时间：2026-07-20 01:55 +08:00

浏览器：`Chromium 95.0.4638.0`

结果：三个新增场景均 PASS；为避免重复执行，先分别定点验证单位 owner 与模型→指标，再在数据元版本契约完成后定点验证字段三类标准绑定。

- `f6-measurement-unit-owner-chromium95.png`、`f6-measurement-unit-owner-chromium95-narrow.png`：单位版本、引用影响、漂移修复、安全返回链和 390px。
- `f6-model-metric-handoff-chromium95.png`：已发布 canonical 模型的度量字段创建原子指标草稿，并以精确指标版本 CAS 回绑。
- `model-detail-standards.png`、`model-detail-standards-narrow.png`：同一模型字段保存数据元 v3、公共码表 v4、度量单位 v3；PUT 载荷保持三个稳定 ID/版本。

边界：以上 F6-T01 浏览器场景使用精确 API mock，证明 production bundle、交互、路由、响应式与 Chrome95 兼容；新增后端、权限和数据库的真实联动必须在 F6-T03 部署后复验。

## F6-T02 构建、发布、运行与失败修复回归

验证时间：2026-07-20 02:55 +08:00

浏览器：`Chromium 95.0.4638.0`

环境：本次 production bundle preview，精确 lifecycle API mock。

```bash
CHROME95_EXECUTABLE_PATH=/tmp/dts-chrome95-ttQJ8k/chrome-linux/chrome \
E2E_BASE_URL=http://127.0.0.1:4173 \
pnpm exec playwright test e2e/sprint67-f6-lifecycle.spec.ts \
  --config=playwright.sprint67.chrome95.config.ts
```

结果：1/1 PASS（6.3s）。覆盖模型详情进入 SQL/dbt 时保留精确 `modelSpecId/revision=3/DBT_MANAGED`，以及失败 `DBT_RUN` 返回同一模型修复；1366x768 与 390x844 均通过，pageerror、console error、requestfailed、HTTP >=400 均为 0。

- `f6-model-implementation-context-chromium95.png`：桌面实现上下文证据。
- `f6-model-repair-chromium95-narrow.png`：390x844 失败运行回链证据。

本轮真实发现并修复 HashRouter production 查询参数读取缺陷，重新构建后定点复验通过。边界：浏览器 API 为精确 mock；真实 Spring/PostgreSQL 事实由 F6-T02 Testcontainers IT 证明。部署环境、真实认证和外部 dbt/Airflow/Catalog E2E 保留给 F6-T03。

## F6-T03 第一批真实 A/B/C 旅程

验证时间：2026-07-20 04:47 +08:00

浏览器：`Chromium 95.0.4638.0`

环境：`https://bi.yuzhicloud.com`，真实 `opadmin` 门户会话、部署 Spring API、Liquibase/PostgreSQL，无 Playwright API route mock。

结果：3/3 PASS（7.6s）。覆盖 BUSINESS_FIRST 已发布 FACT 与稳定指标版本、ASSET_FIRST 同计划四类表和已发布 FACT、旧深链 `NEEDS_CLASSIFICATION`、旧写 410/退役响应头、1440x960 与 390x844。pageerror、requestfailed 和非预期 HTTP >=400 均为 0。

- `journey-a/business-first-metric-owner-chromium95.png`、`business-first-metric-owner-chromium95-narrow.png`
- `journey-b/asset-first-four-models-chromium95.png`、`asset-first-four-models-chromium95-narrow.png`
- `journey-c/legacy-recovery-and-freeze-chromium95.png`、`legacy-recovery-and-freeze-chromium95-narrow.png`

真实性边界：A/B 的编译、测试、评审、发布和 catalog/BI/lineage 注册均有持久化记录；运行入口也保存了 run/event，但部署配置返回 `RUNTIME_DISABLED`，不代表外部 Airflow/dbt 已提交。C 的退出门禁为 `NO-DROP`。只读/跨租户账号、自动映射旧对象和故障注入尚未验证，T03 因此保持 IN_PROGRESS。完整 ID 见 `../runtime/e2e-record-ids.json`。
