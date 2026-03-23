# ELT First Batch Fix Requirements

## 目标

定义本 Sprint 首批修复范围，确保“诊断 + 质量基线 + 首批修复”能够一起交付，而不是范围持续扩张。

## 接入中心首批修复

### 范围

- 恢复接入中心后端最小回归面，使 `IngestionTaskService*` 相关测试重新可运行
- 明确 `executeAsync / retryExecutionAsync` 的失败传播策略
- 明确页面“submitted / running / failed / success”与后端执行记录的映射
- 收口执行日志、latest execution、历史记录三条反馈链的最小一致性
- 校验重试 / 重建 DAG / 删除后的状态残留问题

### 不纳入

- 全量 UI 改版
- 非阻断型表单体验优化

### 验收要求

- 接入中心最小回归命令不再卡在 `testCompile`
- 页面能够区分：
  - 已提交
  - 执行中
  - 成功
  - 失败
  - 部分成功 / 同步延迟
- 后端状态与页面状态不再明显脱节
- 相关单测 / 资源层测试 / 冒烟流覆盖首批修复链路

## 开发中心首批修复

### 范围

- 恢复开发中心后端最小回归面
- 明确 `waitForDagRegistration()` 对 404、401/403、500、网络错误的区分
- 收口 compile / test / build 的 DAG visible / unpause / trigger 错误传播
- 收口 `DbtQualityGateService` / `DbtReleaseGateService` / `DbtOutputRelationService` 的语义与测试
- 明确回滚后二次触发 full-refresh 失败时的接口表现

### 不纳入

- 大规模页面信息架构调整
- dbt 模型本身的业务口径重构

### 验收要求

- 开发中心最小回归命令重新进入稳定红绿状态，不能再被超时掩盖真实失败
- 接口能够区分：
  - DAG 不存在
  - DAG 已存在但不可用
  - Airflow 真正错误
  - dbt 执行失败
- 页面状态与后端返回保持一致
- 关键链路的后端测试恢复并进入最小门禁

## Sprint 级验收

本 Sprint 完成时必须同时满足：

1. 已完成诊断材料
2. 已建立最小质量基线
3. 已落首批修复范围
4. 已明确未修问题的 backlog 和优先级
