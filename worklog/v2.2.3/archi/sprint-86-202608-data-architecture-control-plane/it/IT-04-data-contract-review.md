# IT-04：资产与指标数据契约评审记录

**状态**：PASS（架构数据契约）
**目标日期**：2026-08-17
**Accountable roles**：资产、指标、质量 canonical owner
**已登记评审人**：xiezm（兼任本记录全部 Accountable roles）

## 输入

- F2/T01、F3/T01
- `assets/domain-profile.md` §4～6
- `assets/f2-f3-data-contract-pack.md`
- `assets/consolidated-approval-pack.md` D01～D03、D09～D11、N01～N03
- ADR-86-04/05/06/07/13/16/18/19

## 评审与通过标准

- [x] `CatalogAssetType + CatalogAssetKey` 保持唯一资产身份
- [x] ProducerRef 与 RegistrationEvidence 分轴，多渠道重复发现可幂等归并
- [x] 发现、治理、发布、服务健康、生命周期分别记录，消费资格可解释
- [x] SOURCE/DIM 兼容、回填失败和回滚规则明确
- [x] 指标分类、类型、分组、域、过程和来源版本契约互不混用
- [x] 资产/指标/质量消费方均有下一实施测试矩阵

## 真实决议记录

- 实际时间：2026-08-09
- 参与者真实姓名：xiezm（兼任本记录全部 Accountable roles）
- 选择与理由：批准 D01～D03、D09～D11；复用唯一 CatalogAssetKey，拆分来源/登记/状态，并用稳定业务上下文替代指标自由文本
- 异议、反例和剩余风险：无调整；客户存量、容量与迁移歧义仍是 Sprint-87 G0/运行验证输入
- 行动项/负责人/截止日期：Sprint-87 F3/F4/F6 完成迁移、容量和逐消费者回归；xiezm；对应 Task DoD 前
- 证据链接：`assets/consolidated-approval-pack.md`、`assets/f2-f3-data-contract-pack.md`、`assets/nfr-budget.md`

## 已备评审结论

- 资产纳管、SOURCE/DIM 归一化、ProducerRef/RegistrationEvidence、五轴状态与 eligibility reason codes 已形成完整候选。
- 增量统计投影、24 小时 reconciliation、100,000 资产/50 域设计容量与查询/新鲜度预算已形成候选。
- 指标单业务分类及 ATOMIC/DERIVED/COMPOSITE 上下文矩阵、旧字段兼容与逐消费者测试矩阵已形成候选。
- 所有候选已获 xiezm 集中批准；本状态仍不代表数据迁移或消费者回归已执行。

本 PASS 只覆盖架构数据契约；运行数据迁移、性能和消费者回归须由 Sprint-87 提供证据。
