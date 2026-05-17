# Sprint-32: dts-metrics 独立服务落地（202605）

**时间**: 2026-05
**状态**: DONE
**类型**: Architecture / Implementation（dts-metrics + dts-platform + dts-platform-webapp）
**目标**: 在 Sprint-31A 完成企业级资产事实源、Sprint-31 完成基础链路和拆分边界后，把语义指标中心从 platform 增值业务中抽出为独立 `dts-metrics` 服务。当前版本先不在部署配置层限制 `dts-metrics`，商务限制暂由交付和合同控制；待 license 模块完善后，再统一收口版本授权。

**前置依赖**: `dts-metrics` 必须通过 Sprint-31A 提供的 platform 资产、权限、审计和发布契约工作，不直接读取 platform 内部 Catalog/semantic 表。

## 背景

Sprint-31A 先聚焦企业级数据资产事实源：资产身份、生命周期、治理字段、血缘、权限和读取契约。Sprint-31 再聚焦企业级数据平台主链路：数据源接入、ODS 契约、dbt 建模、资产治理、发布门禁、platform 权限事实源和 analytics 权限消费。随着语义指标、DWS/ADS 低代码生成、BI Dataset 注册和合作方行业包逐步增强，platform 承担了过多业务增值能力。

新的产品分层要求：

- 当前交付版：`dts-platform`、`dts-ingestion`、`dts-analytics`、`dts-metrics` 都按核心服务方式部署，权限和事实源仍由 platform 统一管理。
- 商务分级：基础能力、指标语义能力和大屏分析能力先通过交付边界控制，不通过 compose profile 或 `DTS_METRICS_ENABLED` 控制。
- 后续 license 版：license 模块完善后，再把指标语义、行业包、大屏、AI 辅助等能力纳入统一授权判断。

## 目标架构

```text
platform-webapp shell
  -> dts-platform
       IAM / tenant / org / role
       data-source registry / secret custody
       catalog / asset_grant / audit / approval / event
       dbt publish gateway / capability registry

  -> dts-ingestion
       connector / Addax / file / API / Airflow trigger

  -> dts-metrics
       subject domain / business object / dimension / metric
       formula DSL / metric-pack import / DWS-ADS generator
       preview / review / publish / BI Dataset registration

  -> dts-analytics
       screen / dashboard / chart consumption
       permission check by platform asset_grant
```

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | dts-metrics 服务骨架与默认部署 | P0 | 5 | DONE | Sprint-31A, Sprint-31 F5 |
| F2 | platform 契约、服务鉴权与事实源边界 | P0 | 5 | DONE | Sprint-31A, F1 |
| F3 | 指标领域模型、DSL 与安全生成 | P0 | 6 | DONE | Sprint-31A, F1, F2 |
| F4 | metric-pack 合作方交付工作流 | P0 | 5 | DONE | Sprint-31A, F3 |
| F5 | platform-webapp 入口、版本开关与兼容代理 | P0 | 5 | DONE | Sprint-31A, F1, F2 |
| F6 | 迁移、回滚、集成测试与运维验收 | P0 | 6 | DONE | Sprint-31A, F1-F5 |

**统计**: READY=0, IN_PROGRESS=0, DONE=32, BLOCKED=0

## 交付分级

Sprint-32 的必达目标是“服务独立 + 契约打通 + 最小行业包跑通”，不是一次性完成完整低代码指标产品。

### MVP 必达

- `dts-metrics` 可独立构建、启动、健康检查，并随应用栈默认部署。
- platform 继续作为 IAM、资产、权限、审计、审批、dbt 发布的唯一事实源。
- 不再通过配置层区分 foundation/professional 是否启用 metrics；版本限制后续交给 license 模块。
- 可以导入并校验一个 `metric-pack v0.1`。
- `metric-pack` 只能引用 platform 已登记资产，只能使用受控 DSL，不能携带任意 SQL。
- 可从示例包生成最小 DWS/ADS 候选 artifact，并提交 platform/dbt 发布网关。
- platform-webapp 能基于 capability 展示、隐藏或友好提示指标入口。

### 延展目标

- 完整 BI Dataset 远端注册。
- 完整 dashboard 自动生成。
- 复杂跨主题域指标和多事实表 join 优化。
- 合作方在线配置台。
- 历史 `semantic_*` 数据的生产级自动迁移。

## 非目标

- 不剥离 IAM；platform 仍是用户、角色、组织、租户和权限事实源。
- 不让 `dts-metrics` 持有数据源密码；运行凭据仍由 platform 管理。
- 不允许合作方写平台源码或提交任意 SQL；合作方交付物限定为受控 `metric-pack`。
- 不替换 dbt 和 Airflow；`dts-metrics` 通过 platform/dbt 发布契约生成和提交模型。
- 不把 analytics 合并进 `dts-metrics`；analytics 仍是消费层。

## 完成标准

- [x] `dts-metrics` 具备独立 Spring Boot 服务骨架、Dockerfile、默认 Compose 服务、健康检查和服务鉴权。
- [x] 不依赖 license/profile 开关时，platform、ingestion、metrics、analytics 的基础启动链路可正常交付。
- [x] `dts-metrics` 只通过 platform API 读取资产、数据源引用、权限、审计和 dbt 发布能力，不直接绕过 platform 事实源。
- [x] 指标领域模型支持主题域、业务对象、维度、指标、公式 DSL、DWS/ADS 候选数据集定义和版本状态口径。
- [x] metric-pack v0.1 可导入预检、校验、预览候选 artifact，且无法携带危险 SQL 或不安全资产引用。
- [x] platform-webapp 按 platform 菜单权限展示指标入口；license 接入前不做版本禁用提示。
- [x] 现有 platform 内语义接口保留一个 Sprint 兼容窗口，回滚时可恢复到 Sprint-31 行为。
- [x] IT 证据覆盖默认启动 metrics、导入行业包、生成 DWS/ADS 候选 artifact、提交 dbt 发布网关、权限校验和回滚；BI Dataset 远端注册作为延展目标。

## 验证策略

按当前执行决策，本 Sprint 未在中途执行编译、镜像构建或容器重建。最终统一执行：

- `worklog/v2.2.3/sprint-31-202605/it/scripts/golden-path-smoke.sh`
- `worklog/v2.2.3/sprint-31-202605/it/scripts/observability-admission-check.sh`
- `worklog/v2.2.3/sprint-32-202605/it/scripts/metrics-mvp-admission-check.sh`
- 模块级 Java/前端编译、Docker 镜像构建和对应容器重建

## 相关材料

- Sprint-31: `worklog/v2.2.3/sprint-31-202605/README.md`
- Sprint-31 评审: `worklog/v2.2.3/sprint-31-202605/assets/full-code-review.md`
- 服务拆分设计: `worklog/v2.2.3/sprint-32-202605/assets/dts-metrics-service-design.md`
- Sprint-32 规划评审: `worklog/v2.2.3/sprint-32-202605/assets/sprint-32-review.md`
- 集成测试计划: `worklog/v2.2.3/sprint-32-202605/it/README.md`
