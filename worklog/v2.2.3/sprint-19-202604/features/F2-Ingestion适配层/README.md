# F2: Ingestion 适配层

**优先级**: P0  
**状态**: READY  
**依赖**: F1

## 目标

让 `dts-ingestion` 能基于当前接入任务真实配置创建 OpenMetadata service、pipeline 并触发采集，而不是因为配置形态不匹配而静默跳过。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | 目标库连接配置解析器 | P0 | READY |
| T02 | 数据源类型与 connectionConfig 映射 | P0 | READY |
| T03 | Service/Pipeline/Trigger 结果模型 | P0 | READY |
| T04 | Adapter 单测与假客户端 | P1 | READY |

## 完成标准

- [ ] 支持 nested `connection`、`jdbcUrl`、顶层连接字段。
- [ ] service type 和 connection config 对当前支持的数据源有明确映射。
- [ ] ensure/trigger 失败不再只依赖日志；调用方可获得结构化结果。
- [ ] 单测覆盖成功、缺参、认证失败、OpenMetadata 不可用。
