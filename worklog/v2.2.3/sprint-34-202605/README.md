# Sprint-34: 审计目录 DB 化与 Platform/Analytics 人工操作审计重构（202605）

**时间**: 2026-05
**状态**: DONE（代码与 focused 验证完成；现场 smoke 待部署补证）
**类型**: Architecture / Implementation / Compliance（dts-admin + dts-platform + dts-analytics + dts-common）
**目标**: 将审计模块、动作、路由映射的运行时事实源从中心 JSON/路径猜测迁移到 dts-admin 数据库目录，确保 platform 与 analytics 持续新增模块时可以通过可治理目录注册审计动作，并只记录人工操作痕迹。

## 背景

现场反馈 auditadmin 登录 dts-admin 后，普通用户的 platform 审计日志存在漏记和误分类：查看/修改报表没有稳定记录，新建主题域被记成新建数据资产，业务端日志显示成管理端审计。前序排查确认当前链路同时存在中心 JSON、按钮注册表、URL 映射表、资源字典、前端翻译和 platform fallback 多套语义来源，导致分类漂移。

本 Sprint 采用“数据库作为唯一运行时事实源”的方案：JSON 不再作为线上分类权威，只作为初始化 seed/export 兼容材料；platform 新模块提交稳定 actionCode，dts-admin 通过数据库目录完成模块、动作、资源和操作类型解析。未知动作不再伪装成系统管理或数据资产，而是进入待治理列表。

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | DB 审计目录模型与迁移 | P0 | 4 | DONE | 现有 audit_entry、audit-button-registry |
| F2 | dts-admin 运行时分类与入库链路 | P0 | 4 | DONE | F1 |
| F3 | platform 人工操作审计动作收敛 | P0 | 4 | DONE | F1, F2 |
| F4 | 验证、review 与 IT 证据 | P0 | 3 | DONE | F1-F3 |
| F5 | dts-analytics 人工操作审计动作收敛 | P0 | 4 | DONE | F1, F2 |

**统计**: READY=0, IN_PROGRESS=0, DONE=19, BLOCKED=0

## 核心约束

1. 审计日志是人工操作痕迹，不是系统日志，不记录内部服务通信、模块间消息、轮询、下拉框、字典查询等支撑请求。
2. 运行时分类权威只允许来自数据库审计目录；JSON 仅作为 seed/export/兼容迁移材料。
3. platform 持续新增模块时，新增 actionCode 必须可注册、可审批、可禁用、可审计。
4. 未注册 actionCode 不允许被路径猜测成业务模块；必须记录为未分类并进入治理队列。
5. 历史审计记录保存入库时的模块名和操作名快照，不因目录后续改名而改变历史解释。
6. dts-admin 与 dts-platform 的 sourceSystem 必须保真，业务端审计不能落成管理端审计。
7. dts-analytics 的大屏、语义层和 fallback 审计必须提交稳定 actionCode，不能退成 platform generic。

## 非目标

- 不在本 Sprint 做完整审计目录管理 UI；先提供数据库模型、服务、迁移和后端治理入口。
- 不重写三员职责分离和审计可见性规则。
- 不把算法/LLM 放到线上决定模块名；算法只允许作为离线建议和待治理辅助。
- 不手工维护 platform 全量历史动作；已有 common catalog 仅作为启动 seed，运行时分类仍以 DB catalog 为准。

## 完成标准

- [x] dts-admin 有数据库审计目录表，覆盖模块、动作和分类 miss。
- [x] `AuditV2Service` 不再把所有事件写死为 `sourceSystem=admin`。
- [x] platform ingest 通过 DB 目录解析 actionCode；未知动作进入 miss 记录，不伪装分类。
- [x] 主题域、语义主题域、报表查看/新增/修改/删除等现场问题动作分类正确。
- [x] HTTP fallback 只作为漏埋点保护网，不生成大量支撑查询审计。
- [x] dts-analytics 大屏、语义层、仪表板等人工操作通过 DB catalog 分类，显示为分析端审计。
- [x] focused tests 覆盖 sourceSystem 透传、DB catalog 解析、未知动作 miss、主题域和报表动作。
- [x] review 发现写入 `it/evidence/`，完成后根据 review 再补一轮修正。

## Loop 状态

- Loop 1 完成核心入库链路和现场问题动作修复。
- Loop 2 完成审计中心模块/分组/分类选项 DB catalog 化，并收紧 HTTP fallback 支撑查询降噪边界。
- Loop 3 完成 review 补强：未分类 miss 按同类累计，common catalog 作为一次性缺失 seed 导入 DB，避免升级后大量既有 platform 动作全部落入未分类。
- Loop 4 完成 dts-analytics 审计重构：稳定 actionCode 转发、analytics DB catalog seed、analytics 未分类治理和审计中心展示映射。
- Loop 5 完成现场二次反馈补强：审计中心模块列改用业务模块，raw actionCode 操作内容回落 DB catalog 中文名，大屏字体/图片 GET 支撑资源不再进入人工审计。
- 剩余事项只保留部署后的现场 smoke 补证，不再阻塞代码交付。

## 验证策略

- `source/dts-admin`: catalog service / ingest / AuditV2 focused tests。
- `source/dts-platform`: AuditService / AuditForwarderService / AuditLoggingFilter focused tests。
- `source/dts-analytics`: AnalyticsAuditLoggingFilter / AnalyticsAuditForwarderService focused tests。
- 迁移检查：Liquibase changelog 可重复执行，新增表有唯一约束和必要索引。
- 静态检查：扫描 platform `auditAction(...)` 与 dts-admin `ButtonCodes`，确保关键动作能命中目录。
- 手工 smoke：auditadmin 查询业务端审计，确认日志类型、模块名称、操作内容和操作类型正确。

## 相关文件

- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/audit/`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/AuditIngestResource.java`
- `source/dts-admin/src/main/resources/config/liquibase/changelog/`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/audit/`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/filter/AuditLoggingFilter.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDomainResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ReportsResource.java`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/filter/AnalyticsAuditLoggingFilter.java`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/audit/AnalyticsAuditForwarderService.java`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenAuditService.java`
