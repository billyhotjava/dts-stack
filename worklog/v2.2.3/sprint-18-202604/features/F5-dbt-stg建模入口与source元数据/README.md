# F5: dbt stg 建模入口与 source 元数据

**优先级**: P1  
**状态**: DONE
**依赖**: F1, F2, F3

## 目标

让 dbt 明确从 ODS source 开始建模，`stg` 承担业务字段标准化和类型修正，同时提升 `ods_sources.yml` 的元数据可用性，并为 Phase 4 的 stg 自动生成打基础。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | dbt source 列级元数据生成 | P1 | DONE |
| T02 | stg 模型命名与目录规范 | P1 | DONE |
| T03 | ODS 技术字段在 stg 中的处理规则 | P1 | DONE |
| T04 | dbt source 刷新回归测试 | P1 | DONE |
| T05 | stg 自动生成蓝图与质量 freshness 契约 | P1 | DONE |

## 完成标准

- [x] `ods_sources.yml` 可反映 ODS 表和关键字段。
- [x] stg 模型命名和路径稳定。
- [x] 技术字段默认保留但不进入业务指标口径。
- [x] stg 自动生成蓝图覆盖字段重命名、类型标准化、数据质量测试、source freshness 和 DWD/DWS 承接。
