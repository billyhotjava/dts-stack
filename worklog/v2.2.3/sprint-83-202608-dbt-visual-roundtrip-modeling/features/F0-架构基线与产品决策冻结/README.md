# F0：架构基线与产品决策冻结

**优先级**：P0  
**状态**：DRAFT

## 目标

在不写产品代码的前提下，冻结所有权、可视化范围、输入兼容矩阵、验收路径和精确契约，使 F1～F6 不需要边编码边决定架构。

## 输出契约

- `assets/decision-register.md`：D01～D12 全部 ACCEPTED/REJECTED_WITH_REPLACEMENT。
- `assets/domain-profile.md`：FX-01～05 实测画像。
- `it/baseline.md`：P1～P8 的真实探针结果。
- Sprint README 的端到端 API/DTO/错误码从提案升级为 FROZEN。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 冻结事实所有权与可视化能力等级 | DRAFT | - |
| T02 | 建立真实 dbt 包兼容画像与 golden fixtures | DRAFT | T01 |
| T03 | 验证交付与验收基线 | DRAFT | T02 |
| T04 | 冻结端到端契约并完成 DoR 评审 | DRAFT | T01～T03 |

## Definition of Ready

- [ ] 用户确认 D01～D12。
- [ ] FX-01～05 的来源、授权与脱敏方式明确。
- [ ] 当前共享工作树不被本 Sprint 回退或吸收。

## 完成标准

- [ ] 所有后续 Feature 的输入、输出、错误路径、UI 落点和验证命令无 TBD。
- [ ] Gate Registry 的 G0/G1 均为 PASS，或存在明确 BLOCKED Task。
