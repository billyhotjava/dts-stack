# 数据治理中心代码总览（v2.2.1）

生成时间：2026-02-22

## 模块边界

- 菜单与路由入口在 `dts-admin`：治理中心菜单定义于 `portal-menu-seed.json`。
- 前端页面在 `dts-platform-webapp`：主要位于 `pages/governance/*`，质量报告复用 `pages/catalog/QualityPage.tsx`。
- 后端能力在 `dts-platform`：
  - `GovernanceResource`（规则/运行/合规/问题）
  - `GovernanceIndicatorResource`（指标/维度）
  - `GovernanceReferenceCodeResource`（公共码表）
  - `GovernanceQualityTaskResource`（质量巡检计划）
  - `ModelingAuxResource` + `MetadataStandardResource`（主题域/术语/数据元/模板）

## 已具备能力

- 质量规则：CRUD、绑定、执行、运行记录。
- 质量执行：异步执行 + 审计 + 失败自动转问题单。
- 巡检计划：定时触发能力与手工触发能力。
- 指标/维度：字典、发布、版本、引用、SQL 校验预览。
- 公共码表：目录、码值、映射、批量导入。
- 标准管理：主题域、术语、数据元、模板管理。

## 当前关键问题

1. “质量报告”数据源与治理运行结果割裂：当前读 OpenMetadata 快照，不读 `gov_quality_run`。
2. 前端能力未接全：质量巡检、合规检查、问题闭环 API 已有，页面主流程缺失。
3. 状态字典不一致：前端使用 `ARCHIVED`，服务端部分写 `DEPRECATED`。
4. 调度配置键存在前缀不一致风险：`dts.governance.*` vs `dts.platform.governance.*`。
5. 自动化测试覆盖不足：治理核心以集成手测为主，缺系统化回归矩阵。
