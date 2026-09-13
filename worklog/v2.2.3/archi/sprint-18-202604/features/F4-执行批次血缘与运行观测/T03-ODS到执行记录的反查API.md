# T03: ODS 到执行记录的反查 API

**优先级**: P1  
**状态**: DONE
**依赖**: T01, F2, F3

## 目标

支持从一条 ODS 数据反查接入任务、执行批次、原始来源和日志。

## 范围

- 提供按 batch_id/execution_id 查询执行详情接口。
- 文件源支持返回文件名、sheet、行号。
- 数据库源支持返回源系统、源表、同步模式。

## 完成标准

- [x] 输入 `_dts_batch_id` 可找到 execution。
- [x] 输入 `_dts_execution_id` 可找到任务和日志。
- [x] 前端或 API 文档给出排障查询示例。
