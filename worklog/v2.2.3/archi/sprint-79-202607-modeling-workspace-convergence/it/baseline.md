# 交付基线探针结果（Gate G0）

**探针日期**：2026-07-30～31
**环境**：本地 v2.2.3 Compose + PostgreSQL + DTS 主线候选镜像
**结论**：PASS_WITH_GAPS（正式 DTS 构建、部署、认证工作台、F3 关系图和 F5 菜单回滚已通过；四类代表数据、真实物化和 Chrome95 仍按 Task 阻断）

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | `docker compose -f docker-compose-app.yml ps`：platform/admin/pg/keycloak/proxy 均 healthy | - |
| P2 | 登录路径 | PASS | 2026-07-30 使用一次性 Keycloak 测试身份完成 `/api/keycloak/auth/login`；退出后 Keycloak 用户、审批快照、认证缓存均为 0 残留 | - |
| P3 | Schema | PASS | canonical/legacy 表均存在；画像查询成功 | - |
| P4 | 代表数据 | GAP | 4 plan、7 dimension、6 个 DIMENSION ModelSpec；无其他三类、Candidate/implementation | F0/T01 |
| P5 | API harness | PASS | Playwright 认证会话加载 workbench/model center；网络监听未发现 `/api/**` 4xx/5xx、request failure 或 page error | - |
| P6 | UI harness | PASS_WITH_GAP | Chrome 150 认证旅程切换七模块、刷新保持、指标 owner、工具导入和非零建设计划关系图 1/1 通过；Chrome95 待补 | F1/T01 |
| P7 | 构建/测试 | PASS | platform/admin 生产 Maven 包、webapp TypeScript/Vite、三个 Docker 候选镜像均构建成功；定向 Java/Python/前端测试通过 | - |
| P8 | 外部依赖 | GAP | Airflow scheduler/triggerer/webserver healthy，F4 bind mount 已装载；真实 Candidate/dbt run/relation/Catalog 仍未执行 | F4/T02 |

## 阻断项与处置

| 阻断 | 影响 Feature | 处置 | Task |
|---|---|---|---|
| 缺少四类表和发布链代表数据 | F2、F4 | 使用已批准的项目/财务 Demo fixture，通过 API/UI 创建；不代写客户元数据 | F0/T01 |
| 客户规模与旧入口访问未知 | F3、F5 | 客户环境只读画像与 90 天观测 | F0/T02 |
| Chrome 95 兼容证据未补 | F1～F3 UI | Shell 与面板实现后执行专用 Chrome 95 回归，避免把 Chrome 150 结果外推 | F1/T01 |

## 本轮认证验收结果

- 用例：基线 `sprint79-workspace-baseline.spec.ts`；最终 `sprint79-modeling-workspace.spec.ts`。
- 浏览器：主机 `/usr/bin/google-chrome`，版本 `150.0.7871.128`。
- 结果：最终 1 passed；七模块、规划上下文、标准、维度/模型、指标、工具和非零关系图均可见，页面/API 错误为 0。
- 清理断言：`sprint79-e2e-*` Keycloak 用户 0、`admin_keycloak_user` 快照 0、`e2e/.auth/user.json` 不存在。
- 截图：`it/evidence/baseline/formal-workbench-authenticated.png`、`formal-model-center-authenticated.png`。
- 安全：随机口令只存在于测试进程；证据未保存口令、token 或 storage state。

## 本 Sprint 验收路径

- 后端：认证 REST → canonical service → PostgreSQL；发布链再到 dbt/Airflow/目标 PostgreSQL。
- UI：登录 → `/modeling/workbench` → 项目/财务 Demo → 维度/四类模型 → 指标 → Build/Publish Intent。
- 证据：`it/evidence/IT-xx/`；截图、API 摘要、SQL 断言均脱敏，禁止保存口令/token/profile。
