# Sprint-27 架构基线设计

**状态**: DRAFT
**目的**: 在重构 webapp 信息架构前，先固定服务职责、前端入口、跨服务调用边界和后续预留口，避免 UI 重构反向拉乱后端架构。

## 总体判断

当前 Docker 模式下的核心架构边界是清晰的：`admin`、`platform`、`ingestion`、`analytics` 各自独立部署，`platform-webapp` 承担统一业务入口，Traefik 负责域名和路径路由。Sprint-27 的 UI 重构应遵守这个边界，不把多个后端的职责揉成一个新后端，也不把前端页面直接绑定到某个执行引擎细节。

推荐架构口径：

```text
用户
  |
  v
platform-webapp 统一业务入口
  |
  +--> dts-platform      控制面 / 资产 / 治理 / 指标 / SQL / 聚合 API
  +--> dts-analytics     BI / 报表 / 大屏 / 查询分析运行面
  +--> dts-admin         身份 / 组织 / 用户 / Keycloak / 运维配置
  +--> dts-ingestion     采集任务 / 文件导入 / API/JDBC 入湖 / 回滚 / 执行状态

Kafka 仅作为可选事件基础设施，不进入 Sprint-27 UI 主链路。
```

## 服务职责

| 服务 | 架构角色 | 当前职责 | Sprint-27 边界 |
|------|----------|----------|----------------|
| `dts-admin` | 管理与身份控制面 | 用户、组织、Keycloak、目录、部分运维与 MDM 网关 | UI 重构不直接扩展业务数据能力；只作为身份/组织/目录数据来源 |
| `dts-platform` | 数据平台控制面 | 资产目录、治理、指标、SQL、dbt、权限审计、服务化、跨模块聚合 | ELT/指标可视化的主要聚合 API 应放在这里 |
| `dts-ingestion` | 数据采集执行面 | 采集任务、执行记录、预检、增量、回滚、Airflow/Addax/OpenMetadata 同步 | 继续保持执行服务定位；不承载平台级总览页面逻辑 |
| `dts-analytics` | BI 与分析运行面 | 报表、大屏、卡片、数据集、查询 trace、语义查询、协作 | 提供 BI/消费/查询分析状态；不承载指标治理主数据 |
| `dts-platform-webapp` | 统一业务入口 | 数据门户、治理、指标、开发、BI 挂载入口 | Sprint-27 UI 重构主战场 |
| `dts-admin-webapp` | 管理入口 | 管理员操作、身份与平台基础配置 | 不作为数据平台业务入口 |
| `dts-kafka` | 可选事件基础设施 | 当前仅容器已启动 | 不作为业务功能前置依赖 |

## 前端入口原则

Sprint-27 先重构 `dts-platform-webapp`，不是重构所有前端。页面归属建议：

| 页面/中心 | 前端归属 | 后端主数据源 | 说明 |
|-----------|----------|--------------|------|
| ELT 控制台 | `source/dts-platform-webapp/src/pages/elt/**` 或现有开发中心下独立模块 | `dts-platform` 聚合 API，必要时代理 `dts-ingestion` 状态 | 展示采集、dbt、质量、血缘、发布全链路 |
| 指标运营台 | `source/dts-platform-webapp/src/pages/metrics/**` | `dts-platform` 指标与治理 API，必要时读取 `analytics` 消费状态 | 延续 Sprint-26 的 metrics 所有权 |
| 审计证据 | `source/dts-platform-webapp/src/pages/audit/**` 或治理/安全中心内页 | `dts-platform` 审计聚合 API | 先统一审计口径，不提前固化策略 |
| BI/大屏消费 | platform webapp 内挂载 analytics 路由 | `dts-analytics` | 页面可以集成状态，但不要把 BI 主实现搬进 platform 后端 |
| 身份组织配置 | admin webapp | `dts-admin` | platform 仅消费目录结果 |

## 后端聚合原则

UI 可以先用现有 API 拼装，但最终应补 `dts-platform` 聚合层，而不是让前端长期直接编排多个服务。

建议聚合 API 方向：

| API | 归属 | 用途 |
|-----|------|------|
| `/api/elt/overview` | `dts-platform` | ELT 总览、阶段统计、异常分布 |
| `/api/elt/assets/{assetId}/timeline` | `dts-platform` | 指定资产的采集、dbt、质量、血缘时间线 |
| `/api/elt/tasks/{taskId}/diagnostics` | `dts-platform` | 采集任务失败诊断聚合 |
| `/api/metrics/operations/overview` | `dts-platform` | 指标运营总览 |
| `/api/metrics/{id}/operations` | `dts-platform` | 指标定义、运行、质量、血缘、消费聚合 |
| `/api/audit/evidence` | `dts-platform` | 统一审计证据查询 |

`dts-ingestion` 和 `dts-analytics` 继续提供领域 API；跨领域视图由 `dts-platform` 聚合，前端不要长期承担跨服务编排。

## UI 重构边界

本次 UI 重构可以做：

- 重建菜单和页面结构，让操作流更符合“数据进入平台到被消费”的顺序。
- 新增页面壳、状态卡片、时间线、异常下钻、空状态和预留槽位。
- 用现有 API 或 mock adapter 识别后端缺口。
- 把安全策略、审批、脱敏做成状态位和扩展位。

本次 UI 重构不要做：

- 不绕过 `dts-platform` 直接大量调用 `dts-ingestion` 和 `dts-analytics` 形成前端编排。
- 不把 `dts-admin` 的身份管理页面搬到 platform webapp。
- 不把 analytics 的 BI 运行逻辑复制到 platform。
- 不为 UI 临时造一套不可复用的后端接口。
- 不让 Kafka 成为页面加载或操作提交的依赖。

## 操作流设计

目标操作流：

```text
1. 数据接入
   数据源 / 文件 / API / JDBC -> 采集任务 -> 预检 -> 执行记录

2. 数据加工
   ODS -> DWD -> DWS/ADS -> dbt 运行 -> 发布检查

3. 数据治理
   质量规则 -> 质量运行 -> 问题定位 -> 影响分析 -> 修复/重跑

4. 指标建设
   指标定义 -> 指标生成/运行 -> 质量校验 -> 订阅/消费

5. 数据消费
   SQL / API / BI / 大屏 -> 访问审计 -> 消费统计

6. 运维审计
   任务状态 -> 异常诊断 -> 审计证据 -> 后续事件外送
```

## 预留口

| 领域 | 当前处理 | 预留方式 |
|------|----------|----------|
| 审批 | 客户模式未定 | UI 展示 `approvalStatus` 槽位，后端预留 `approvalRef` |
| 端到端权限 | 暂不固化 | UI 展示策略状态，后端预留 `policyContext` |
| 脱敏 | 暂不固化全出口 | UI 展示 `maskingDecision` 摘要位，后端审计字段预留 |
| Kafka | 容器已接入 | 先保留 topic/envelope/outbox 设计，不进入主链路 |
| 观测 | 先按页面状态展示 | 后续接 Prometheus/OpenTelemetry 指标 |
| 发布治理 | 先做检查清单 | 后续接审批和环境 promotion |

## 推荐重构顺序

1. 固化前端 IA：菜单、路由、页面所有权和模块边界。
2. 建 ELT 控制台页面壳：总览、任务、资产、时间线、异常。
3. 建指标运营台页面壳：健康、定义、运行、质量、消费。
4. 用现有 API 接入第一版数据，缺口用 adapter 标注。
5. 补 `dts-platform` 聚合 API，替换前端临时拼装。
6. 补审计一致性字段和证据查询。
7. 再评估 Kafka/outbox 事件化接入。

## 架构结论

当前四个后端服务边界可以支撑 Sprint-27 的 UI 重构：

- `admin` 管人和组织。
- `platform` 管数据平台控制面和聚合视图。
- `ingestion` 管采集执行。
- `analytics` 管 BI/分析运行。

重构重点不是改变服务拆分，而是让 `platform-webapp` 的操作流和 `dts-platform` 的聚合 API 把这些能力组织成一个连贯的数据平台体验。
