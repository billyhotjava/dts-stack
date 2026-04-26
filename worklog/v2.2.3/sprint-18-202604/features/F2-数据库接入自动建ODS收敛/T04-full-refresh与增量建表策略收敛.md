# T04: full_refresh 与增量建表策略收敛

**优先级**: P1  
**状态**: READY  
**依赖**: T01

## 目标

统一 full_refresh 和 incremental 的 ODS 建表、清表和变更处理策略。

## 范围

- 明确 full_refresh 是否 drop/recreate 或 truncate/reload。
- incremental 默认不破坏已有 ODS 表。
- schema 变化本 Sprint 只做检测、告警和阻断，不做完整自动迁移。
- 避免同一次执行同时 drop 和 truncate 的重复行为。

## 完成标准

- [ ] full_refresh 行为有明确配置和测试。
- [ ] incremental 不静默删除表或字段。
- [ ] schema drift 会进入失败或待确认状态。
