# Sprint-1: 数据采集稳定性 & 建模工作流完善

## 目标

在 `customer/2.2.1` 分支上完成两项核心改进：

1. **数据采集稳定性与容错** — 消除单次 HTTP 失败即报错、僵尸执行、无自动重试等生产级痛点
2. **建模文件测试/排错/提交** — 补齐 dbt 执行日志展示、测试结果细节、Git 版本控制等关键缺失

## 范围

### 后端
- `source/dts-platform` — 代理层容错、Git 集成、数据预览
- `source/dts-ingestion` — 僵尸恢复、自动重试队列、Resilience4j
- `source/dts-common` — 如需共享重试/审计定义

### 前端
- `source/dts-platform-webapp/src/pages/explore/etl/` — 采集任务轮询优化、静默失败可见化
- `source/dts-platform-webapp/src/pages/modeling/` — 日志展示、测试结果、Git 面板、数据预览

## Task 列表

### 批次一：采集稳定性（BE-001 ~ BE-003, FE-001 ~ FE-002）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| BE-001 | Resilience4j 重试 + 断路器引入 | 后端 | 2-3天 | DONE |
| BE-002 | 僵尸执行恢复机制 | 后端 | 1天 | DONE |
| BE-003 | 失败自动重试队列 | 后端 | 3天 | DONE |
| FE-001 | 前端轮询策略优化（自适应退避、去掉硬超时） | 前端 | 1天 | DONE |
| FE-002 | 静默轮询失败可见化 | 前端 | 0.5天 | DONE |

### 批次二：建模工作流（BE-004 ~ BE-007, FE-003 ~ FE-007）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| BE-004 | dbt 执行日志从 Airflow 拉取 | 后端 | 1天 | DONE |
| BE-005 | 丰富 dbt 测试结果解析（per-test 详情） | 后端 | 2天 | DONE |
| BE-006 | JGit 集成 — 提交/历史/回滚 API | 后端 | 3天 | DONE |
| BE-007 | dbt 数据预览端点 | 后端 | 1天 | DONE |
| FE-003 | 底部面板 Tab 重构（分离编译/测试/日志） | 前端 | 2天 | DONE |
| FE-004 | dbt 执行日志 Tab 展示 | 前端 | 1天 | DONE |
| FE-005 | 测试结果 Tab（per-test 表格） | 前端 | 1天 | DONE |
| FE-006 | Git 变更状态 + 提交面板 | 前端 | 2天 | DONE |
| FE-007 | 数据预览 Tab | 前端 | 1天 | DONE |

## 本 Sprint 不做

- 不升级 Airflow 到 CeleryExecutor（留给后续迭代）
- 不引入消息队列（Kafka/RabbitMQ）
- 不实现 CDC 实时监控卡片
- 不做 SQL 格式化/lint 集成
- 不做分布式追踪（Zipkin/Jaeger）

## 集成测试

`it/` 目录存放集成测试用例和验证脚本，覆盖：
- 采集任务全链路：创建 → 执行 → 失败 → 自动重试 → 成功
- 断路器触发与恢复验证
- 僵尸执行恢复验证
- dbt 编译/测试/运行 → 日志展示验证
- Git 提交/回滚流程验证
- 数据预览端到端验证

## 状态跟踪

各 Task 进度在本文件的 Task 列表中更新状态标记：
- 空白 = 未开始
- WIP = 进行中
- DONE = 已完成
- BLOCK = 阻塞
