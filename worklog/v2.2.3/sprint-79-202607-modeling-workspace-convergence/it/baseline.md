# 交付基线探针结果（Gate G0）

**探针日期**：2026-07-30  
**环境**：本地 v2.2.3 Compose + PostgreSQL + 静态原型  
**结论**：PASS_WITH_GAPS（正式 DTS 的认证、受保护 API 调用和浏览器入口已恢复；F1 可启动，四类代表数据与物化链仍按 Task 阻断）

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | `docker compose -f docker-compose-app.yml ps`：platform/admin/pg/keycloak/proxy 均 healthy | - |
| P2 | 登录路径 | PASS | 2026-07-30 使用一次性 Keycloak 测试身份完成 `/api/keycloak/auth/login`；退出后 Keycloak 用户、审批快照、认证缓存均为 0 残留 | - |
| P3 | Schema | PASS | canonical/legacy 表均存在；画像查询成功 | - |
| P4 | 代表数据 | GAP | 4 plan、7 dimension、6 个 DIMENSION ModelSpec；无其他三类、Candidate/implementation | F0/T01 |
| P5 | API harness | PASS | Playwright 认证会话加载 workbench/model center；网络监听未发现 `/api/**` 4xx/5xx、request failure 或 page error | - |
| P6 | UI harness | PASS_WITH_GAP | 正式 DTS `/modeling/workbench` 与 `/modeling/models` 在系统 Chrome 150 真实通过；Chrome 95 兼容证据仍待 F1 UI 完成后补 | F1/T01 |
| P7 | 构建/测试 | PENDING | 立项仅文档变更，未执行 webapp/backend 构建 | 各实施 Task |
| P8 | 外部依赖 | GAP | Airflow webserver healthy，但当前 Candidate/run/relation observation 均为 0 | F4/T02 |

## 阻断项与处置

| 阻断 | 影响 Feature | 处置 | Task |
|---|---|---|---|
| 缺少四类表和发布链代表数据 | F2、F4 | 使用已批准的项目/财务 Demo fixture，通过 API/UI 创建；不代写客户元数据 | F0/T01 |
| 客户规模与旧入口访问未知 | F3、F5 | 客户环境只读画像与 90 天观测 | F0/T02 |
| Chrome 95 兼容证据未补 | F1～F3 UI | Shell 与面板实现后执行专用 Chrome 95 回归，避免把 Chrome 150 结果外推 | F1/T01 |

## 本轮认证验收结果

- 用例：`source/dts-platform-webapp/e2e/sprint79-workspace-baseline.spec.ts`。
- 浏览器：主机 `/usr/bin/google-chrome`，版本 `150.0.7871.128`。
- 结果：1 passed；正式工作台和模型中心的 canonical `data-testid` 均可见。
- 清理断言：`sprint79-e2e-*` Keycloak 用户 0、`admin_keycloak_user` 快照 0、`e2e/.auth/user.json` 不存在。
- 截图：`it/evidence/baseline/formal-workbench-authenticated.png`、`formal-model-center-authenticated.png`。
- 安全：随机口令只存在于测试进程；证据未保存口令、token 或 storage state。

## 本 Sprint 验收路径

- 后端：认证 REST → canonical service → PostgreSQL；发布链再到 dbt/Airflow/目标 PostgreSQL。
- UI：登录 → `/modeling/workbench` → 项目/财务 Demo → 维度/四类模型 → 指标 → Build/Publish Intent。
- 证据：`it/evidence/IT-xx/`；截图、API 摘要、SQL 断言均脱敏，禁止保存口令/token/profile。
