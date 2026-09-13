# IT-01：统一语言评审记录

**状态**：PASS
**目标日期**：2026-08-10
**Accountable roles**：产品决策负责人、数据架构负责人
**评审人**：xiezm（兼任产品决策、数据架构及受影响 canonical owner）

## 输入

- `assets/domain-profile.md` §1～2
- `assets/decision-register.md` ADR-86-01/02/03/11/12
- `assets/f1-t01-decision-pack.md` §1～3
- F1/T01 的术语、scope 与 owner 候选

## 必须到场

- 产品决策负责人
- 数据架构负责人
- 建模、资产、指标、质量 canonical owner 代表

## 评审与通过标准

- [x] 每个术语只有一个定义，冲突旧称有迁移映射
- [x] 平台全局 scope 与遗留 tenant 字段兼容口径明确
- [x] 数据架构元数据、建模建设范围和业务 MDM 三类对象不混称
- [x] 未决项有 accountable role、截止日期和后续 ADR

## 评审前输入基线

- ADR-86-01/02/03/11 已是 `ACCEPTED`，本次只复核其与 owner 矩阵是否一致。
- ADR-86-12 已有“架构字典与业务 MDM 分离”的方向；本次只决定是否把**边界**冻结，通用 MDM 实现仍转后续 Sprint。
- `f1-t01-decision-pack.md` 已给出术语唯一定义、反例、兼容路径和验收方式。
- 以上为评审前基线；实际批准结果与参与者已记录在下方“真实决议记录”。

## 产品决策确认

- 确认时间：2026-08-09
- 确认人：xiezm（产品决策负责人）
- ADR-86-12：只冻结 MDM 边界，具体主数据能力后续实现。
- 当前效力：xiezm 已明确兼任全部评审角色；ADR-86-12 升为 `ACCEPTED`。

## 真实决议记录

- 实际时间：2026-08-09
- 参与者真实姓名：xiezm（兼任产品决策、数据架构及受影响 canonical owner）
- 结论：PASS；复核 ADR-86-01/02/03/11，批准 ADR-86-12
- 异议与反例：无未关闭异议；反例与拒绝条件见 `assets/f1-t01-decision-pack.md` §3
- 行动项/负责人/截止日期：F1/T02 消费冻结术语与 owner；xiezm；2026-08-14
- 证据链接：`assets/decision-register.md`、`assets/f1-t01-decision-pack.md`

本记录已满足统一语言评审证据要求，可作为 F1/T01 DONE 证据；不代表 F1/T02 或运行时实现已完成。
