# F1: ODS 原样落地契约与技术字段

**优先级**: P0  
**状态**: READY  
**依赖**: 无

## 目标

固化 DTS 接入中心第一阶段的 ODS 契约：源业务字段原样落地，允许追加统一 DTS 技术字段，所有业务清洗和建模从 dbt `stg` 开始。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | ODS Landing Contract v1 | P0 | READY |
| T02 | `_dts_*` 技术字段标准与兼容策略 | P0 | READY |
| T03 | 源字段保护与 ODS 禁止业务计算规则 | P0 | READY |
| T04 | Schema Snapshot v1 与 Phase 2 扩展边界 | P0 | READY |
| T05 | 历史任务兼容与迁移说明 | P1 | READY |

## 完成标准

- [ ] 有明确文档说明 ODS 与 stg 的职责边界。
- [ ] 技术字段命名、类型、默认值和冲突策略被固化。
- [ ] 旧字段 `source_system/import_time` 有兼容路径。
- [ ] 数据库、Excel、CSV 后续实现都引用同一契约。
