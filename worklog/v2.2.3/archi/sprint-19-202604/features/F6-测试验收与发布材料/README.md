# F6: 测试验收与发布材料

**优先级**: P1
**状态**: DONE
**依赖**: F1, F2, F3, F4, F5

## 目标

为 Sprint-19 建立可执行发布门禁，避免 OpenMetadata 集成再次出现“代码存在但现场不可用”的状态。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | 自动化测试矩阵 | P1 | DONE |
| T02 | Compose 冒烟与样例数据 | P1 | DONE |
| T03 | 发布、升级与回滚说明 | P1 | DONE |

## 完成标准

- [x] Java 单测覆盖 FQN、parser、adapter 核心错误处理。
- [x] compose 冒烟覆盖 OpenMetadata server、ingestion、platform、ingestion-service。
- [x] 验收证据留存在 `it/README.md` 或其引用文件。
- [x] 发布说明包含存量 `.env` 修复和回滚策略。

## 已知限制

- OpenMetadata PostgreSQL ingestion 仅允许采集数仓/分析侧数据库，默认目标为 `DTS_OPENMETADATA_INGEST_DATABASE=biadmin`；`dts_platform`、`dts_admin`、`dts_common`、`dts_analytics` 等平台业务/内部库被启动前阻断。
- 当前 dbt artifacts 中存在 `hive.biadmin.public.*` FQN；上线前需确保 PostgreSQL ingestion 已采集同一数仓/分析库，或调整 dbt/OpenMetadata service/database/schema 口径。
- owner/domain/tags 目前由 ingestion 响应显式回传 `not_supported`，不静默丢弃；表级写入留作后续增强。
