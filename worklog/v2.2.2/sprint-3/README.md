# Sprint-3：入湖任务体验优化与 UI 重构

## 目标

解决入湖任务模块积累的体验痛点：修复已知 Bug、补齐功能缺失、重构 4300+ 行巨型组件。

## 本 Sprint 已完成（在 sprint-3 启动前已合入 v2.2.2 分支）

| # | 问题 | 类型 | 状态 |
|---|------|------|------|
| S3-001 | 重跑失败任务弹错误提示，但实际在跑 | Bug | ✅ 已修复 |
| S3-002 | 重试接口改为异步，避免 51s 超时 | UX | ✅ 已修复 |
| S3-003 | Excel 字段批量粘贴填充 | 功能 | ✅ 已完成 |

### S3-001 详情

**根因：** `execute()` 方法中 Airflow 触发成功后，如果后续操作（DB 状态更新、审计记录）抛异常，错误会冒泡到前端，但 DAG 已经在 Airflow 中运行了。

**修复：**
- 在 `IngestionTaskService.execute()` 中增加 `airflowTriggered` 哨兵
- Airflow 触发确认后，后续操作包裹在嵌套 try-catch 中
- 外层 catch 检测到已触发则返回成功，不再标记 failed

**文件：** `source/dts-ingestion/.../service/IngestionTaskService.java`

### S3-002 详情

**问题：** 重试调用的是同步端点，DAG 准备最长可等 51 秒，前端干等。

**修复：**
- 后端新增 `POST /tasks/{id}/executions/{executionId}/retry/async` 异步端点
- 后端新增 `retryExecutionAsync()` 方法
- 前端 `submitRetry()` 改为调用异步端点，立即返回并轮询

**文件：**
- `source/dts-ingestion/.../service/IngestionTaskService.java`
- `source/dts-ingestion/.../web/rest/IngestionTaskResource.java`
- `source/dts-platform-webapp/src/api/ingestion.ts`
- `source/dts-platform-webapp/src/pages/explore/etl/TransformExecutionHistoryPage.tsx`

### S3-003 详情

**需求：** Excel 导入时每个字段要逐一编辑，希望从 ODS 表复制字段名后一键粘贴填充。

**实现：**
- 字段映射区域新增"批量输入"按钮
- Modal 弹窗支持逗号/换行分隔的字段名
- 按顺序匹配填充到已有列的 name 字段

**文件：** `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`

---

## 已完成（全部）

### S3-004：入湖任务页面 UI 重构（P1） ✅

**问题：** `TransformCreatePage.tsx` 单文件 4368 行，承载了所有数据源类型的配置、字段映射、同步模式、执行设置，页面间跳转过多。

**目标：**
- 拆分为分步向导（Stepper）
- 按职责拆分子组件
- 减少页面跳转，将执行历史内嵌到详情页

**拆分方案：**

#### Step 1: 基础信息 → `IngestionBasicInfoStep.tsx`
- 任务名称、描述
- 数据源类别选择（文件 / JDBC / API）
- 数据源连接选择
- 约 300 行

#### Step 2: 源配置 → `IngestionSourceConfigStep.tsx`
- 文件上传 + Excel 解析配置
- JDBC 表选择
- JSON 编辑模式
- 约 800 行

#### Step 3: 字段映射 → `IngestionFieldMappingStep.tsx`
- 字段列表表格（含批量输入）
- ODS 匹配
- 字段类型/长度配置
- 附加列配置
- 约 600 行

#### Step 4: 同步与目标 → `IngestionSyncConfigStep.tsx`
- 同步模式（全量/增量）
- 目标表名配置
- 写入器参数
- 约 400 行

#### Step 5: 调度与治理 → `IngestionScheduleStep.tsx`
- 调度类型（手动/Cron/间隔）
- 执行治理（并发、窗口、优先级）
- 约 300 行

#### Step 6: 确认与提交 → `IngestionReviewStep.tsx`
- 配置摘要
- 提交/保存草稿
- 约 200 行

#### 共享逻辑 → `ingestionFormHelpers.ts`
- `mapTaskToForm()`, `extractFileUploadResult()`, `buildReaderConfig()` 等辅助函数
- 约 500 行

#### 主编排 → `TransformCreatePage.tsx`（重构后）
- Stepper 容器 + 步骤切换
- 约 300 行

### S3-005：详情页内嵌执行历史 Tab（P2） ✅

**实现：**
- 从 `TransformExecutionHistoryPage.tsx` 抽取核心表格为 `components/ExecutionHistoryTable.tsx`
- `TransformDetailPage.tsx` 增加 Tabs：任务配置 + 执行历史
- 独立执行历史页面保持可用（使用共享组件）

**文件：**
- 新增 `source/dts-platform-webapp/src/pages/explore/etl/components/ExecutionHistoryTable.tsx`
- 修改 `TransformDetailPage.tsx`（增加 Tabs）
- 简化 `TransformExecutionHistoryPage.tsx`（薄包装器）

### S3-006：执行按钮 Loading 状态优化（P2） ✅

**实现：**
- `TransformPage.tsx`（列表页）：执行按钮显示 loading + "执行中" 文字，覆盖整个轮询周期
- `TransformDetailPage.tsx`（详情页）：三态文字"执行任务" → "提交中..." → "执行中"，覆盖提交+轮询全周期

### S3-007：DAG 预热机制（P3） ✅

**实现：**
- 新增 `DagPreheatService.java`：`@Async` 后台轮询 Airflow DAG 注册状态（最多 3 次，5s 间隔）
- `AirflowClient.java`：新增 `isDagRegistered()` 公开方法
- `IngestionTaskService.java`：在 `create()` 和 `update()` 末尾触发异步预热
- 执行时如果预热已完成，`waitForDag()` 第一次检查即通过，跳过等待

**文件：**
- 新增 `source/dts-ingestion/.../service/etl/DagPreheatService.java`
- 修改 `AirflowClient.java`、`IngestionTaskService.java`

---

## 里程碑

| 阶段 | 内容 | 预计 |
|------|------|------|
| Phase 1 | S3-004 组件拆分（Step 1-3） | 第 1 周 |
| Phase 2 | S3-004 组件拆分（Step 4-6 + 主编排） | 第 2 周 |
| Phase 3 | S3-005 详情页 Tab + S3-006 Loading | 第 3 周 |
| Phase 4 | S3-007 DAG 预热 + 回归测试 | 第 4 周 |

## 文件清单

| 新增文件 | 用途 |
|---------|------|
| `src/pages/explore/etl/steps/IngestionBasicInfoStep.tsx` | 步骤 1 |
| `src/pages/explore/etl/steps/IngestionSourceConfigStep.tsx` | 步骤 2 |
| `src/pages/explore/etl/steps/IngestionFieldMappingStep.tsx` | 步骤 3 |
| `src/pages/explore/etl/steps/IngestionSyncConfigStep.tsx` | 步骤 4 |
| `src/pages/explore/etl/steps/IngestionScheduleStep.tsx` | 步骤 5 |
| `src/pages/explore/etl/steps/IngestionReviewStep.tsx` | 步骤 6 |
| `src/pages/explore/etl/ingestionFormHelpers.ts` | 共享逻辑 |
| `src/pages/explore/etl/components/ExecutionHistoryTable.tsx` | 执行历史表格 |

| 修改文件 | 变更 |
|---------|------|
| `TransformCreatePage.tsx` | 从 4368 行缩减到 ~300 行（Stepper 编排） |
| `TransformDetailPage.tsx` | 增加 Tabs + 执行历史 Tab |
| `TransformExecutionHistoryPage.tsx` | 抽取表格组件 |
