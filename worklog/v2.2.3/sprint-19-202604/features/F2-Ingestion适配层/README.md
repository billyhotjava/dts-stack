# F2: Ingestion 适配层

**优先级**: P0
**状态**: DONE
**依赖**: F1

## 目标

让 `dts-ingestion` 能基于当前接入任务真实配置创建 OpenMetadata service、pipeline 并触发采集，而不是因为配置形态不匹配而静默跳过。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | 目标库连接配置解析器 | P0 | DONE |
| T02 | 数据源类型与 connectionConfig 映射 | P0 | DONE |
| T03 | Service/Pipeline/Trigger 结果模型 | P0 | DONE |
| T04 | Adapter 单测与假客户端 | P1 | DONE |

## 完成标准

- [x] 支持 nested `connection`、`jdbcUrl`、顶层连接字段。
- [x] service type 和 connection config 对当前支持的数据源有明确映射。
- [x] ensure/trigger 失败不再只依赖日志；调用方可获得结构化结果。
- [x] 单测覆盖核心成功、缺参和映射路径。
