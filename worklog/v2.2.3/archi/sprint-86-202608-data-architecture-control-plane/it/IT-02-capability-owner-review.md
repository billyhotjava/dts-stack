# IT-02：能力边界与 Canonical Owner 评审记录

**状态**：PASS
**目标日期**：2026-08-11
**Accountable roles**：数据架构负责人、各 canonical owner
**评审人**：xiezm（兼任产品决策、数据架构、各 canonical owner 与安全/权限负责人）

## 输入

- `assets/capability-boundary.md`
- `assets/decision-register.md` ADR-86-08/12/17
- `assets/f1-t01-decision-pack.md`
- F1/T01 owner/consumer 矩阵

## 必须到场

- 产品决策负责人、数据架构负责人
- 建模、资产、指标、质量、主数据 owner
- 安全/权限负责人（ADR-86-17）

## 评审与通过标准

- [x] 每个实体只有一个写 owner，消费者只读稳定契约
- [x] 不新增第二套域、分层、资产身份、模型或发布控制面
- [x] 六类业务职责与产品粗粒度授权、后端宽角色/对象 guard 的差距被显式连接
- [x] 审计后置检测未被误写成预防性权限强制
- [x] MDM 只保留边界和后续 Sprint 输入

## 评审前输入基线

- 平台数据架构是**逻辑 canonical owner**，不自动等于新增一级菜单；页面形态由 ADR-86-09 另审。
- 数仓计划属于建模建设范围；计划对数据集市等字典的 baseline 选择不是第二个字典写 owner。
- 候选写边界为“唯一 application command service + 兼容 adapter”；现存 `CatalogDomainResource` 直写 Repository 是后续实施缺口。
- 冻结顺序要求 ADR-86-17 先于 ADR-86-08；实际评审已按该顺序完成，结果见下方记录。

## 产品决策确认

- 确认时间：2026-08-09
- 确认人：xiezm（产品决策负责人）
- ADR-86-12：只冻结 MDM 边界，具体能力后续实现。
- ADR-86-17：同意唯一 application command boundary + 现有权限 guard，并选择方案 A。
- 方案 A：`ROLE_ADMIN/ROLE_OP_ADMIN/ROLE_INST_DATA_OWNER` 可写平台全局架构字典，部门角色只读。
- 当前效力：xiezm 已明确兼任全部评审角色；ADR-86-08/12/17 均升为 `ACCEPTED`。

## 真实决议记录

- 实际时间：2026-08-09
- 参与者真实姓名：xiezm（兼任产品决策、数据架构、各 canonical owner 与安全/权限负责人）
- 结论：PASS；批准 ADR-86-08/12/17
- 异议与剩余风险：无未关闭异议；read/write/export 粗粒度与尚未实施的 command boundary 作为具名实施风险保留
- 行动项/负责人/截止日期：F5/T01 将 command boundary、负向授权与审计契约拆入下一实施 Sprint；xiezm；2026-08-20
- 证据链接：`assets/decision-register.md`、`assets/f1-t01-decision-pack.md`、`assets/review-findings.md` RF-86-09

本记录已满足能力边界评审证据要求；command boundary 与权限测试仍须由下一实施 Sprint 落地。
