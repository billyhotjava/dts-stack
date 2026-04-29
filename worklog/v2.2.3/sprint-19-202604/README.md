# Sprint-19: OpenMetadata 元数据采集闭环修复

**时间**: 2026-04
**状态**: DONE
**类型**: Implementation（OpenMetadata 集成修复 + 元数据采集闭环 + 运维验收）
**目标**: 把现有 OpenMetadata 相关配置、采集、血缘、质量和平台查询能力从“部分接入但不稳定”收敛为可交付闭环：服务可用、采集可触发、FQN 可解析、血缘可注册、失败可观测、本地 catalog 回退边界清晰。

## 背景

代码和运行时已经确认本平台接入了 OpenMetadata：

- `imgversion.conf` 定义 `openmetadata/server:1.11.5` 与 `openmetadata/ingestion:1.11.5`。
- `docker-compose-app.yml` 定义 `dts-openmetadata` 与 `dts-openmetadata-ingestion`。
- `dts-platform` 通过 `OpenMetadataClient` / `OpenMetadataService` 查询表元数据、血缘和质量测试。
- `dts-ingestion` 通过 `OpenMetadataAdapter` / `OpenMetadataClient` 尝试创建 service、创建 ingestion pipeline、触发 pipeline 和注册血缘。
- 当前运行时 `dts-openmetadata` 已启动；`dts-openmetadata-ingestion` 已通过 token 模式完成 PostgreSQL 与 dbt one-shot 冒烟。

当前主要问题不是“没有 OpenMetadata”，而是已有集成没有形成稳定闭环：

- `.env` 中 `DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN` 被初始化为 `{service`，导致平台侧候选 FQN 拼接错误。
- 接入任务调用 `registerLineage(..., List.of())`，而适配器在 streams 为空时直接跳过，血缘注册实际没有发生。
- `OpenMetadataAdapter` 只读取目标库顶层 `host/port/database/username/password`，无法覆盖 Addax 常见的 nested `connection` / `jdbcUrl` 结构。
- 一次性 dbt / OpenMetadata ingestion 脚本默认要求 token，当前部署未配置 token 或 no-auth 显式开关。
- `lineage.domain/tags/owner` 已在任务 schema 中出现，但适配器只消费 `enabled`，治理元数据没有写入 OpenMetadata。

## 产品原则

1. **OpenMetadata 是平台元数据采集的标准外部系统**：不再把它当成可有可无的旁路；当开关启用时必须有明确成功或失败状态。
2. **本地 catalog 是降级来源**：OpenMetadata 不可用时可以回退本地 catalog，但 UI/API 必须能表达数据来源和缺失原因。
3. **FQN 规则可配置但必须安全默认**：默认 pattern 要覆盖当前 service/database/schema/table 口径，并对坏 pattern 做诊断。
4. **接入任务产出真实血缘**：血缘来源于 reader/writer/tableMapping/schema snapshot，而不是空 streams 或静态占位。
5. **治理字段要么落库，要么显式不支持**：owner/domain/tags 不能悄悄丢弃。
6. **采集范围必须隔离业务运行库**：OpenMetadata PostgreSQL 采集只允许采集数仓/分析侧资产，禁止 `dts_platform`、`dts_admin`、`dts_common`、`dts_analytics` 等平台业务/内部库进入数仓分析平台。
7. **运维可验收**：OpenMetadata server、ingestion 容器、API、pipeline trigger、日志和重试都要有现场验收步骤。

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|----|---------|--------:|--------|------|
| F1 | 配置与部署基线 | 4 | P0 | DONE |
| F2 | Ingestion 适配层 | 4 | P0 | DONE |
| F3 | 血缘注册与标签治理 | 4 | P0 | DONE |
| F4 | OpenMetadata 采集作业运维化 | 4 | P0 | DONE |
| F5 | 平台读路径与本地回退 | 4 | P1 | DONE |
| F6 | 测试验收与发布材料 | 3 | P1 | DONE |

**合计 23 个 task。**

## 依赖图

```text
F1 配置与部署基线
  -> F2 Ingestion 适配层
    -> F3 血缘注册与标签治理
  -> F4 OpenMetadata 采集作业运维化
    -> F5 平台读路径与本地回退
      -> F6 测试验收与发布材料
```

F1 先修正 `.env`、`init.sh` 和部署开关，避免后续功能建立在错误配置上。F2/F3 负责写路径，F4 负责 OpenMetadata ingestion 运行路径，F5 负责平台读路径和 UI/API 表达，F6 统一验收证据和发布门禁。

## 完成标准

- [x] 新安装和已有环境的 `DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN` 不再生成 `{service` 这类截断值。
- [x] OpenMetadata server、ingestion 容器、API、认证/no-auth 策略有一致配置和启动前检查。
- [x] `OpenMetadataAdapter` 能解析当前接入任务的目标库配置，包括 nested `connection`、`jdbcUrl` 和顶层连接字段。
- [x] 创建 service、创建 pipeline、触发 pipeline、注册 lineage 都有结构化结果和可观测失败原因。
- [x] 接入任务完成后能基于真实 table mapping / stream mapping 注册表级血缘。
- [x] owner/domain/tags 暂不写入 OpenMetadata，但以 `not_supported` 显式回传并记录在发布说明中。
- [x] 平台侧元数据、血缘、质量查询能用正确 FQN 命中 OpenMetadata；未命中时能说明回退来源。
- [x] OpenMetadata ingestion 的一键/定时采集可在现场执行并留存日志证据。
- [x] OpenMetadata PostgreSQL ingestion 默认采集库与平台业务库解耦，并对禁止库做启动前阻断。
- [x] Java 单测、集成冒烟、compose 运行检查和发布回滚说明齐备。

## 非目标

- 不替换 OpenMetadata 为其他元数据系统。
- 不在本 Sprint 重构全部 catalog 数据模型。
- 不新增连接器市场或 SaaS 连接器编排。
- 不做列级血缘的深度解析；本 Sprint 先保证表级血缘稳定，列级血缘只预留结构。
- 不把 OpenMetadata 故障变成接入任务硬失败；默认只在配置要求强一致时阻断。

## 关键代码触点

- `init.sh`
- `.env` / `.env.example` / `imgversion.conf`
- `docker-compose-app.yml`
- `services/dts-openmetadata/ingestion/`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/openmetadata/OpenMetadataAdapter.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/openmetadata/OpenMetadataClient.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/config/OpenMetadataProperties.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/openmetadata/OpenMetadataService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/openmetadata/OpenMetadataClient.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/CatalogDatasetResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/`
- `source/dts-platform-webapp/src/pages/catalog/`
- `source/dts-admin-webapp/src/admin/views/infra-settings.tsx`

## 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 现场 OpenMetadata 认证模式不统一 | ingestion 容器跳过采集或 API 401 | F1 统一 token/no-auth 开关，F4 增加启动前检查 |
| 不同数据源连接配置结构差异大 | service/pipeline 创建失败 | F2 做专门 parser 和 fixture，不在业务代码里散落取值 |
| FQN 与 OpenMetadata 实际命名不一致 | 平台查不到表、血缘、质量 | F1/F5 建立 pattern 校验、候选 FQN 日志和单测 |
| 血缘写入成功但源/目标表未先存在 | OpenMetadata lineage 断链 | F3 先 ensure table/service，再 register lineage；失败不静默 |
| 本地 catalog 回退掩盖 OpenMetadata 失败 | 用户误以为采集成功 | F5 在响应和 UI 中暴露 metadata source 与 fallback reason |
