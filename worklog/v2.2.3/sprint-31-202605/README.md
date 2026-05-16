# Sprint-31: 企业级数据平台主链路补齐（202605）

**时间**: 2026-05
**状态**: READY
**类型**: Architecture / Implementation（dts-platform + dts-ingestion + dts-analytics + dts-platform-webapp）
**目标**: 基于全量模块评审，把 DTS 从“数据接入、dbt 建模、资产目录、语义指标、大屏消费的初级功能集合”收敛成一条可验收的企业级数据产品主链路：连接器接入、ODS/DWD/DWS/ADS、发布门禁、运行血缘、资产治理、语义指标、BI/大屏消费、平台统一权限。

## 背景

当前 DTS 已经具备多个关键能力：

- Connector Center 已有数据源管理、JDBC Schema Discover、ODS 生成、任务向导、预检和运行中心。
- dts-ingestion 已能根据 platform 数据源解析运行凭据，生成 Addax 作业并接入 Airflow。
- dts-platform 已接入 OpenMetadata、本地 Catalog、dbt source 刷新、dbt 发布接口和资产门户。
- 语义指标中心已有主题域、业务对象、维度、指标、DWS/ADS、dbt 生成、审核发布、BI Dataset 注册、血缘注册的基础接口。
- analytics 大屏权限已优先走 platform `asset_grant`，本地权限表保留 fallback。

但这些能力目前仍是“模块可用”，还没有形成企业级主链路。核心问题是：缺少一条强契约的黄金路径，缺少阻断型质量门禁，非 JDBC 接入和大规模文件接入还不完整，血缘/资产/权限存在多个事实源，语义指标中心离可交付的低代码指标建模仍有距离。

## 目标架构

```text
数据源 / 文件 / API
  -> Connector Center
  -> ODS 落地契约
  -> dbt STG / DWD / DWS / ADS
  -> 发布门禁（compile/test/build + schema contract + lineage diff）
  -> Catalog / OpenMetadata / OpenLineage
  -> platform asset_grant / 数据密级 / 审计 / 运行观测
  -> platform-webapp 基础版入口
  -> 可选增值能力：dts-metrics / dts-analytics
```

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | 黄金链路契约与端到端验收 | P0 | 5 | READY | - |
| F2 | Connector Center 企业级补齐 | P0 | 5 | READY | F1 |
| F3 | 运行血缘与资产治理闭环 | P0 | 6 | READY | F1, F2 |
| F4 | dbt 发布门禁与模型资产同步 | P0 | 5 | READY | F1, F3 |
| F5 | 语义指标服务拆分准备 | P0 | 6 | READY | F1, F4 |
| F6 | 消费层发布与 platform 权限统一 | P0 | 5 | READY | F5 |
| F7 | 观测、审计与性能准入 | P1 | 5 | READY | F1-F6 |

**统计**: READY=37, IN_PROGRESS=0, DONE=0, BLOCKED=0

## 非目标

- 本 Sprint 不剥离 IAM 独立安全模块；只把 platform 作为权限事实源的边界补齐。
- 不替换 dbt 和 Airflow；本版本继续强化现有生产组合。
- 不引入 SQLMesh / Dagster / Kestra 作为生产依赖；只保留下一代方向预研。
- 不重写 analytics；只收敛 analytics 本地权限为只读 fallback，并让 platform-webapp 保持入口和消费体验。
- 不承诺 500MB / 百万行 CSV 正式交付，除非 F7 性能准入证据通过。

## 完成标准

- [ ] 有一份可重复执行的黄金链路验收脚本，覆盖数据源 -> ODS -> dbt -> Catalog -> 语义模型 -> BI Dataset -> 大屏权限。
- [ ] Connector Center 对 JDBC / 文件 / API 的能力边界在页面和 API 上明确，JDBC 主链路无 silent failure，文件/API 有可追踪任务契约。
- [ ] OpenLineage / dbt manifest / Addax lineage 统一写入 Catalog，自动创建的资产必须有治理状态，不能默默成为可用资产。
- [ ] dbt 发布从 warning-only 升级为可配置阻断门禁，至少对生产模式阻断失败测试、缺失 schema 合约和过期构建证据。
- [ ] 语义指标能力从 platform 基础链路中抽出清晰边界：基础版可关闭，platform 只保留入口、权限、资产、审计和发布契约，完整 `dts-metrics` 独立服务进入 Sprint-32。
- [ ] analytics 大屏读写权限以 platform `asset_grant` 为唯一事实源；本地 fallback 命中必须有告警和迁移报表。
- [ ] 关键链路具备审计、运行指标、性能边界和 IT 证据。

## 相关材料

- 全量评审: `worklog/v2.2.3/sprint-31-202605/assets/full-code-review.md`
- 集成测试计划: `worklog/v2.2.3/sprint-31-202605/it/README.md`
- Sprint 队列: `worklog/v2.2.3/sprint-queue.md`
