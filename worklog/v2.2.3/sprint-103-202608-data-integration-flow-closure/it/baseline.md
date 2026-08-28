# G0 交付基线证据

检查日期：2026-08-28。
结论：`PASS_WITH_ENV_NOTE`，允许进入实施。真实登录、受保护页面、本地 Chrome、核心服务、数据库和 Airflow 已打通；Chrome 95 executable 与三角色隔离会话仍缺失，因此最终不能把本机 Chrome 150 结果表述为 Chrome 95 通过。用户已明确要求本轮使用本地 Chrome 集中验收。

## Probe 结果

| Probe | 结果 | 证据摘要 | 判定 |
|---|---|---|---|
| P1 Runnable Instance | 平台、接入、管理、分析、PostgreSQL、Keycloak、代理、Airflow 运行；核心容器健康 | `docker compose -p v223 -f docker-compose-app.yml ps` | PASS |
| P2 Authentication & API | 真实门户登录 200，`portal_session=true`；受保护页面可达 | 交互式凭据只注入测试进程，不落盘 | PASS |
| P3 Schema & Migration | task/revision/execution/graph_dsl 可查询；Airflow 无 import error；线上尚未应用 Sprint-102 的 quality workflow execution 字段迁移 | PostgreSQL/Airflow 只读 SQL + source changelog 对账 | PASS_WITH_GAPS |
| P4 Representative Data | 13 个任务、88 个执行；7 条 ACTIVE 已全量分类；现有样本均为线性单任务 EL | `assets/workflow-complexity-evidence.md` | PASS |
| P5 Browser/Role Harness | 本地 Chrome 150 真实登录并打开受保护任务编排页面，无 page error；仅提供一个具备当前页面权限的验收账号 | `/tmp/sprint103-baseline.png`（临时证据，不入库） | PASS_WITH_ENV_NOTE |
| P6 External Dependencies | Airflow 2.9.3 health 全绿；本机只有 Chrome 150，没有 Chrome 95 | 容器 health + 本机 executable/version | PASS_WITH_ENV_NOTE |
| P7 Build/Test Entry | Java 聚焦测试使用 Maven `-Dtest=... test`；前端使用 Vitest/source-contract；最终使用 legacy `pnpm build`；E2E 使用 `@playwright/test` + `/usr/bin/google-chrome` | 仓内真实工具链 | PASS |
| P8 Secrets/Config | 文档未记录凭据；受保护 API 未登录返回 401 | 输出脱敏 | PASS |

## 运行态快照

- 接入任务：13；ACTIVE 7，已删除 6。
- 活跃源类型：http 1、MySQL 2、文本文件 4。
- `graph_dsl`：12 SQL NULL、1 JSON null、0 对象；这是自由画布需求未被证明的信号，不是新样本目标。
- revision：ACTIVE 11、DRAFT 2、SUPERSEDED 30；没有同任务多个 ACTIVE revision。
- execution：88；FAILED 81、SUCCESS 7，时间范围 2026-08-03～2026-08-07。
- Airflow：57 个 DAG，其中 ingestion 34；import errors 0。
- 当前 task/revision DAG 标识与注册 ingestion DAG 交集为 3；其余 31 个保持只读 `KEEP`，本 Sprint 不自动删除、暂停或接管。
- 审计：task create/update/admit/execute/retry 与 ETL Airflow read/trigger 动作码启用且有记录。
- 源代码已证明接入成功后存在持久化质量触发意图、有限重试和 workflow/run 关联；线上 `ingestion_execution` 仍缺少对应字段，必须在部署 ingestion 后验证 changelog 已应用。
- 同一物理目标 `ods_api_api_query_s10_fin_cost_center` 在 catalog 中出现 `POSTGRES` 与 `POSTGRESQL` 两条身份漂移记录。本 Sprint 不自动合并数据；设计投影必须按明确 `datasetId` fail-closed，避免创建第三条资产。

## 解阻动作

1. 部署前先验证 Sprint-102 ingestion migration 在目标数据库可重跑并成功应用。
2. 选择不会影响客户数据的金丝雀；若无法安全连续写两批，则以自动化集成测试证明证据切换，并把真实双批运行标为现场验收项。
3. 完成合法 design、非法 design、并发 checksum 冲突三类自动化样本。
4. 最终用本地 Chrome 150 跑单一旅程并标记 `PASS_WITH_ENV_NOTE`；Chrome 95 仍作为甲方环境复验项。
5. 31 个未匹配 DAG 保持 `KEEP`，只输出对账，不做运行态变更。

## 退出条件

交付链已具备进入实施的最低条件；以上环境说明进入最终验收记录，不能被构建成功或 Chrome 150 截图掩盖。
