# Sprint-3: 三级数据回退机制

## 目标

在 `customer/2.2.1` 分支上实现完整的数据回退能力，覆盖三种粒度：

1. **Level 1 — 清空数据** — TRUNCATE 指定表，保留结构
2. **Level 2 — 重建表结构** — DROP + 重建，修复字段错误
3. **Level 3 — 全链路回退** — 级联清除采集任务、ODS 表、模型、产出表、资产（支持任务级和数据源级）

特别考虑 Excel/CSV 一次性导入场景的回退。

设计文档：`docs/plans/2026-03-09-data-rollback-design.md`

## 范围

### 后端 — dts-ingestion
- `DataRollbackService` — 回退编排（影响分析 + 执行）
- `TableOperationService` — 物理表 TRUNCATE/DROP 操作
- `RollbackAuditService` — 审计日志
- `ConfirmationPolicy` — 可插拔确认策略接口
- Liquibase — rollback_audit_log 表 + ingestion_task 扩展

### 后端 — dts-platform
- `RollbackProxyResource` — 代理 API 层
- `RollbackCascadeService` — 级联清理 SQL 模型 + dbt 文件 + 资产数据集

### 前端 — dts-platform-webapp
- 采集任务详情页 — 回退操作按钮组
- 数据源管理页面 — 数据源级回退入口
- 建模页面 — dbt 产出表清空/重建操作

## Task 列表

### 批次一：后端基础设施（RB-001 ~ RB-005）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| RB-001 | Liquibase: rollback_audit_log 表 + ingestion_task 关联字段 | 后端 | 0.5天 | |
| RB-002 | TableOperationService — TRUNCATE/DROP 物理表操作 | 后端 | 1天 | |
| RB-003 | ConfirmationPolicy 接口 + ModalConfirmationPolicy 默认实现 | 后端 | 0.5天 | |
| RB-004 | RollbackAuditService — 审计日志写入与查询 | 后端 | 0.5天 | |
| RB-005 | DataRollbackService — 影响分析(dryRun)核心逻辑 | 后端 | 1天 | |

### 批次二：三级回退执行逻辑（RB-006 ~ RB-010）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| RB-006 | Level 1 执行 — 按表 TRUNCATE（支持多选） | 后端 | 0.5天 | |
| RB-007 | Level 2 执行 — DROP + syncMode 切换 + 重跑采集触发 | 后端 | 1天 | |
| RB-008 | Level 2 扩展 — dbt run --full-refresh 下游表重建 | 后端 | 0.5天 | |
| RB-009 | Level 3 任务级 — 级联回退（ODS 表 + 映射 + 执行记录 + 上传文件 + Job/DAG） | 后端 | 1.5天 | |
| RB-010 | Level 3 数据源级 — 批量任务发现 + 逐任务回退编排 | 后端 | 1天 | |

### 批次三：dts-platform 代理层 + 级联清理（RB-011 ~ RB-014）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| RB-011 | RollbackProxyResource — 影响分析 + 执行代理 API | 后端 | 0.5天 | |
| RB-012 | RollbackCascadeService — 级联删除 SQL 模型定义 + dbt 文件 | 后端 | 1天 | |
| RB-013 | RollbackCascadeService — 级联 DROP DWD/DWS/ADS 产出表 | 后端 | 0.5天 | |
| RB-014 | RollbackCascadeService — 级联删除关联资产数据集 | 后端 | 0.5天 | |

### 批次四：Excel/CSV 特殊场景处理（RB-015 ~ RB-016）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| RB-015 | Excel/CSV 上传文件清理 + 列映射重置逻辑 | 后端 | 0.5天 | |
| RB-016 | Excel/CSV Level 2 回退流程 — DROP 表后引导用户重新上传 | 后端 | 0.5天 | |

### 批次五：前端 API 层 + 采集任务详情页（RB-017 ~ RB-021）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| RB-017 | platformApi.ts — 回退相关 API 函数（analyze/execute/auditLog） | 前端 | 0.5天 | |
| RB-018 | 影响分析弹窗组件 — RollbackImpactModal（通用） | 前端 | 1天 | |
| RB-019 | 采集任务详情页 — Level 1 清空数据按钮 + 表选择器 | 前端 | 1天 | |
| RB-020 | 采集任务详情页 — Level 2 重建表结构按钮 + 确认流程 | 前端 | 0.5天 | |
| RB-021 | 采集任务详情页 — Level 3 全链路回退按钮 + 影响分析展示 | 前端 | 0.5天 | |

### 批次六：数据源管理 + 建模页面入口（RB-022 ~ RB-025）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| RB-022 | 数据源管理页面 — Level 3 数据源级回退入口 | 前端 | 1天 | |
| RB-023 | 建模页面 — dbt 产出表 Level 1 清空操作 | 前端 | 0.5天 | |
| RB-024 | 建模页面 — dbt 产出表 Level 2 full-refresh 重建操作 | 前端 | 0.5天 | |
| RB-025 | 回退审计日志查看面板（嵌入采集详情/数据源详情） | 前端 | 0.5天 | |

## 本 Sprint 不做

- 不实现 ADMIN_APPROVE 确认策略（等权限体系完善后演进）
- 不实现 INPUT_NAME 确认策略（同上）
- 不做跨服务分布式事务（回退操作允许部分失败 + 审计记录）
- 不做定时自动回退或过期数据自动清理
- 不做回退操作的撤回（回退是不可逆操作）

## 集成测试

`it/` 目录存放集成测试用例和验证脚本，覆盖：
- Level 1: 创建任务 → 采集数据 → TRUNCATE → 验证表为空 → 重跑采集 → 数据恢复
- Level 2: 创建任务 → 采集数据 → DROP + 修改列映射 → 重跑 → 验证新结构
- Level 2 dbt: 建模 → dbt run → full-refresh 重建 → 验证产出表结构更新
- Level 3 任务级: 创建任务 → 采集 → 生成模型 → dbt run → 全链路回退 → 验证所有产物清除
- Level 3 数据源级: 多任务 → 数据源级回退 → 验证所有子任务产物清除
- Excel 场景: 上传 Excel → 采集 → Level 2 回退 → 重新上传 → 验证
- 审计日志: 每次回退操作产生审计记录 → 验证记录完整性

## 状态跟踪

各 Task 进度在本文件的 Task 列表中更新状态标记：
- 空白 = 未开始
- WIP = 进行中
- DONE = 已完成
- BLOCK = 阻塞
