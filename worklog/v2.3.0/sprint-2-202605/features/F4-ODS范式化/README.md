# F4: ODS 范式化（v3 升级）

**优先级**: P1
**状态**: READY
**设计阶段**: 骨架
**依赖**: F2

## 目标

将现有 9 张 `ods_*_v2` 反范式化宽表重构为结构化、引用 MDM 的关系模型；DWD 层引用 MDM 稳定维表；稳定代理键替代 md5 拼串；**所有层加 classification 字段与 dbt meta，给下游做 ABAC 过滤**；顺带清掉 `feedback_dbt_pjm_v3_pending.md` 里 12 项遗留债务。

## 设计红线（brainstorm 已对齐）

- **ODS 保留贴源**：原始 Excel → `ods_*` 不动（审计/回溯），范式化发生在新增 STG 层或 ODS 的重做副本
- **主数据走 MDM**：不再在 ODS 表里以 varchar 冗余 project_no / dept / subsystem；以稳定 code 引用 MDM
- **字典走 MDM**：所有枚举字段引用 MDM 字典的 `standard_code`；枚举类型来自 `SecurityLevelCatalog` + MDM 业务字典
- **信息-跟进 拆分**：4 对冗余（progress / quality / tech_state / risk 的 info vs measure）做主从拆分
- **事件/流水独立**：完成事件、归零事件、签署事件、整改事件、风险释放事件等从主表剥离
- **代理键稳定**：对主数据用 MDM 的 ASCII code；对事件用 `(业务主键, 时间戳)` 组合或 UUID
- **密级贯通**：每个 ODS / STG / DWD 表**都加 `classification VARCHAR(20)` 列**；dbt 模型 yaml meta 里声明 `meta.classification`，给下游（platform / Trino ABAC / 大屏）做密级过滤

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | [反范式问题梳理与目标模型](T01-目标模型.md) | P0 | READY | F2 |
| T02 | [MDM 引用层（替代硬编码）](T02-MDM引用层.md) | P0 | READY | T01 |
| T03 | [信息-跟进 主从拆分](T03-主从拆分.md) | P0 | READY | T02 |
| T04 | [事件与流水独立建模](T04-事件独立建模.md) | P0 | READY | T02 |
| T05 | [稳定代理键策略](T05-稳定代理键.md) | P0 | READY | T03, T04 |
| T06 | [DWD 改造 + classification 贯通](T06-DWD改造.md) | P0 | READY | T05 |
| T07 | [dbt 12 项遗留债务清零](T07-dbt债务清零.md) | P1 | READY | T06 |

## 完成标准

- [ ] 新结构化 ODS / STG 模型上线，DWD 全量引用 MDM
- [ ] 所有表有 `classification` 列（默认 `INTERNAL`）；dbt yaml meta 有 `classification` 声明
- [ ] 同一指标在重构前后数据一致（对比脚本证据）
- [ ] 现有 ADS / 大屏下游零改动或仅最小适配
- [ ] `feedback_dbt_pjm_v3_pending.md` 12 项全部关闭
