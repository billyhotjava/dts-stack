# T05: 灰度发布、迁移与回滚 runbook

**优先级**: P0
**状态**: DRAFT
**依赖**: F1-F6

## 目标

定义 API 接入从隐藏能力到正式开放的发布路径，以及失败时的关闭和回滚策略。

## 范围

- Feature flag：隐藏入口、只读数据源、允许 preview、允许调度。
- 数据库迁移：props 扩容、secret 表、checkpoint 表、execution plan 表。
- 回滚：关闭 API 入口、停止调度、保留历史任务、禁用 runner。
- 运维 runbook：常见故障、排查命令、数据修复和客户侧协同。

## 当前临时阻断（必须在 Phase 2 上线前显式拆除）

`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java:721-723` 当前对 API 类型 task 抛 `IllegalStateException("API 数据接入运行时尚未启用，请先保存草稿")`。该硬阻断保证 F4 未完成前 API 任务不会被误执行。

发布门禁项（必查）：
- [ ] Phase 2 上线前：用 feature flag `dts.ingestion.api.runtime.enabled=true` 替换硬抛异常，并保留旧分支作为 flag=false 时的拒绝路径。
- [ ] Phase 2 灰度阶段：flag 仅对名单租户开放；CI 必须存在 flag-on/flag-off 双向单测。
- [ ] Phase 3 全量前：flag 仍然是杀手锏（kill switch），不可被删除；运维 runbook 写明回退命令。
- [ ] Phase 2 / Phase 3 发布 PR 模板必须包含本 checkbox 勾选证据。

## Feature flag 清单

| flag | 默认值 | 含义 |
|---|---|---|
| `dts.ingestion.api.entrypoint.enabled` | false | 前端是否暴露 API 数据源/任务入口 |
| `dts.ingestion.api.preview.enabled` | false | 是否允许 preview/dry-run |
| `dts.ingestion.api.runtime.enabled` | false | 是否解除 IngestionTaskService 硬阻断、允许实际执行 |
| `dts.ingestion.api.runtime.tenants` | "" | 灰度租户白名单（CSV，空表示全部） |

## 完成标准

- [ ] 可以按租户或环境灰度开放（依据上面 4 个 flag）。
- [ ] 回滚不会删除已落 ODS 数据 —— rollback 仅关闭 flag、停止 DAG，不 drop 已建 ODS 表。
- [ ] 停用 API runner 后现有 DB/File 任务不受影响 —— SourceConnector 路由仅对 connectorType=api 生效；回归测试覆盖 JDBC/File 任务。
- [ ] runbook 覆盖上线、验证、降级和回滚 —— 至少包括：开关切换命令、Liquibase 回滚命令、checkpoint reset 命令、secret 撤销命令、DAG pause/resume 命令。
- [ ] 上线前 checklist：所有 Phase 2/3 门禁项已勾选并 link 到证据 PR。

