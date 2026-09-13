# 交付基线探针结果（Gate G0）

**探针日期**：2026-07-31
**环境**：本地 v2.2.3 Compose + PostgreSQL；客户生产环境未画像
**结论**：PASS_WITH_GAPS（平台运行健康、当前 schema 可画像；客户存量、备份恢复、真实质量运行和最终 E2E 未完成）

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | `docker compose -f docker-compose-app.yml ps --format json`：`dts-platform` running/healthy，PG/Keycloak/Airflow webserver 等关键依赖运行 | - |
| P2 | 登录/认证 | DEFERRED | Sprint-80 已有 UI 验收记录；本 Sprint 按约定不在编码前重复全链登录 | F6/T03 最终认证 E2E |
| P3 | Schema | PASS_WITH_GAP | canonical/legacy/quality/迁移 ledger 可做只读统计；客户 schema revision 未取证 | F0/T02 |
| P4 | 代表数据 | GAP | canonical：ModelSpec 6/revision 10、dimension 7/revision 21、plan 4、mart 2、glossary 42、template 4；quality run=0 | F3/T02、F6/T03 |
| P5 | API harness | DEFERRED | canonical route 清单已冻结；旧 route 删除后的真实 API 走查延后到编码完成 | F6/T03 |
| P6 | UI harness | DEFERRED | Sprint-80 页面已部署验收；本 Sprint 不改 UI，最终只验证新 UI 到新后台 | F6/T03 |
| P7 | 构建/测试 | PENDING | 本 Sprint 尚未改源码；按模块定向测试、构建和共享 Maven lock 在实施期执行 | F1～F6 |
| P8 | 外部依赖 | PASS_WITH_GAP | Airflow/dbt 容器运行；dts-admin/PG 健康；真实 quality→candidate→dbt→catalog/audit 链未执行 | F4、F6/T03 |
| P9 | 客户迁移/备份 | BLOCKED | 客户表数量、引用、30/90 天调用、备份窗口均未知 | F0/T02、F0/T03 |

## 当前数据基线

| 分类 | 当前实测 | 结论 |
|---|---|---|
| canonical | ModelSpec 6/10 revisions；Dimension 7/21 revisions；WarehousePlan 4；DataMart 2；glossary 42；template 4 | canonical owner 不重建 |
| legacy | semantic/sql/business_object/old plan 及 run/candidate/materialization 当前均 0 | 仅当前环境可走空表路径；客户环境不得外推 |
| quality | template 10；run 0 | 模板不能作为门禁证据，最终 E2E 必须产生隔离真实 run |

## 阻断项与处置

| 阻断 | 影响 Feature | 处置 | Task |
|---|---|---|---|
| 客户环境存量与调用方未知 | F5 全部物理删除 | 只读画像、外部调用清单、停机窗口二次复核 | F0/T02 |
| 备份恢复未演练 | F5 表删除、F6 Go | 精确 pg_dump/restore 到隔离库，核对 manifest checksum | F0/T03 |
| 质量运行事实为 0 | F3 StageGate、F6 E2E | 创建隔离测试 rule/version/binding/run，不写客户业务数据 | F3/T02、F6/T03 |
| 最终 API/UI/dbt/Airflow 未执行 | Sprint DoD | 全部编码和 schema contract 完成后集中一次执行 | F6/T03 |

## 本 Sprint 验收路径约定

- 架构：ArchUnit/module dependency → ModelSpec revision/state machine → outbox/execution gateway IT。
- 迁移：只读 profile → backup → dry-run manifest → apply → verify → drop → restore rehearsal。
- 后端：canonical REST → application port → PostgreSQL → quality/catalog → ReleaseCandidate/Materialization → DbtExecutionGateway。
- 外部：Airflow DagRun → dbt build → relation probe → CatalogAssetKey → dts-admin 审计。
- UI：Sprint-80 `/data-modeling/**` → canonical API；最终只在 F6/T03 集中走查。
- 证据：`it/IT-xx/commands.md` + `result.md`，必要时附脱敏 `api.json`、`db.txt`、截图；禁止保存口令、token、profile 或数据源 secret。

## 基线状态解释

`PASS_WITH_GAPS` 只表示可以继续做架构与定向实现；不批准客户环境 DROP，也不代表 Sprint 已验收。F5 每个删除批次必须重新检查对应环境的 R0～R6。
