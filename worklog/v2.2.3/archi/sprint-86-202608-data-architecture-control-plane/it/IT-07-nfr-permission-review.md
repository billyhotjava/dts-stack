# IT-07：规模、可靠性与权限约束评审记录

**状态**：PASS（NFR 架构设计；运行 fitness functions 转 Sprint-87）
**目标日期**：权限前置结论 2026-08-11；完整 NFR 结论 2026-08-17
**Accountable roles**：数据架构、资产、安全/权限 owner
**评审人**：xiezm（兼任数据架构、资产、安全/权限及产品决策负责人）

## 输入

- `assets/nfr-budget.md`
- `assets/f1-t01-decision-pack.md` §1、§3～5
- `assets/consolidated-approval-pack.md` N01～N12
- ADR-86-04/15/16/17/18/19
- RF-86-08/09 与客户/生产画像

## 评审与通过标准

- [x] NFR-86-01～13 均有确定值/方案、owner 和可执行 fitness function
- [x] 纳管范围与统计架构同批冻结，触顶时不返回伪精确值
- [x] 批量上限、DAG 边界、超时、取消、幂等和重试授权可验证
- [x] 预防性权限控制与审计侦测控制明确区分
- [x] 无法强制的风险由产品/安全负责人显式接受并具名登记
- [x] 下一实施 Sprint 有对应容量、性能、权限与审计测试 Task

## ADR-86-17 已批准选择与后续实现项

1. xiezm 已确认唯一 application command boundary 作为“单一代码 owner”的预防控制。
2. xiezm 已选择方案 A：`ROLE_ADMIN/ROLE_OP_ADMIN/ROLE_INST_DATA_OWNER` 可写平台全局架构字典。
3. xiezm 已确认 `DEPT_DATA_OWNER/DEPT_LEADER` 等部门角色对平台全局架构字典只读。
4. xiezm 已确认 UI 按钮隐藏与审计日志均不是权限强制，不能替代负向 403/拒绝测试。
5. xiezm 已确认把 `ArchitectureOwnerAuthorizationIT`、依赖规则和审计契约作为下一实施 Sprint 的强制准入。

权限子评审于 2026-08-09 PASS，ADR-86-17 升为 `ACCEPTED`，RF-86-09 转为 `MITIGATED`。xiezm 同日集中批准 N01～N12；NFR-86-01～13 均为 `ACCEPTED_DESIGN`，Gate G1 的架构设计通过。

## 真实决议记录

- 实际时间：2026-08-09（权限子评审 + NFR 集中审批）
- 参与者真实姓名：xiezm（兼任数据架构、资产、安全/权限及产品决策负责人）
- 预算与方案选择：权限方案 A 与 N01～N12 全部批准，无替代值
- 接受/拒绝的剩余风险：接受首版继续依赖 read/write/export 粗粒度产品权限；禁止将其表述为细粒度动作集，依靠方案 A allowlist 与唯一 command boundary 补足预防控制
- 行动项/负责人/截止日期：Sprint-87 F1/F2/F3/F5/F6 执行授权负向测试、依赖规则、审计、容量、性能和故障注入；xiezm；对应 Task DoD 前
- 证据链接：`assets/consolidated-approval-pack.md` N01～N12、`assets/nfr-budget.md`、`assets/decision-register.md` ADR-86-17、`assets/review-findings.md` RF-86-09

本 PASS 是 NFR/权限**设计评审**证据；运行时 fitness functions 尚未执行，Sprint-87 对应 Task 在测试通过前不得标记 DONE。

## 已备评审结论

- 统计：100,000 资产/50 域、P95 1.5 秒、≤3 SQL、5/10 分钟 freshness/stale、24 小时 reconciliation。
- 批量：root 100、节点 500、边 2,000、深度 20、候选 API P95 5 秒。
- 长任务：前台/后台轮询 5/30 秒、10 分钟 STALE、默认 120 分钟、取消 5 秒受理/5 分钟终态。
- 并发/兼容：每 plan+environment 一个 active candidate、每 entry 一个 active attempt；Contract 至少 14 天且跨一个发布周期。
- 上述设计值均已批准，但不是运行验证；仍由 Sprint-87 fitness functions 执行。
