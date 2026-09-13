# F5: 纵向集成与验收

**优先级**: P0
**状态**: BLOCKED_INPUT（依赖 G0 的 B1/B2/B3）

## 目标

在运行实例上跑通「采集 → 发现缺边 → 补录 → 核验 → 影响分析可见 → 导出」的完整治理闭环，并完成 Chrome 95 兼容与发布安全检查，产出 `it/` 下的真实证据。

## 契约定义

本 Feature 不新增契约，验证前四个 Feature 的契约在真实链路上成立。

## 交付物

| 交付物 | 落点 |
|---|---|
| IT-01 ~ IT-09 真实证据 | `it/README.md` |
| Chrome 95 兼容结论 | `it/` + IT-09 |
| 发布计划与回滚预案 | `assets/release-plan.md`（**待建**，Gate G3） |
| 运维手册（血缘采集异常排查） | `assets/runbook.md`（**待建**，Gate G4） |

## UI/UX 规格

不新增界面。验收脚本直接引用各 Feature README 的「操作走查」段落，不重写。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 血缘治理闭环端到端集成测试 | P0 | BLOCKED_INPUT | F1、F2、F3 全部完成；G0 的 B1/B2 |
| T02 | 真实浏览器四态验收与发布安全 | P0 | BLOCKED_INPUT | T01；G0 的 B3 |

## Definition of Ready

- [x] 契约已钉死（验证对象即前四 Feature 的契约）
- [x] 竖切片已画通
- [x] UI 落点已命名（引用各 Feature 走查）
- [ ] 依赖已就绪 —— B1/B2/B3 均为 GAP
- [x] 验收可验证

## 完成标准

- [ ] IT-01 ~ IT-09 全部为真实证据，无 PENDING、无占位
- [ ] Gate G3（发布安全）、G4（可运维性、DoD 验收）转 PASS
- [ ] Sprint README 追溯矩阵的「验收证据位置」列全部填实
- [ ] 集中执行一次构建 + E2E，失败只做针对性重跑，不整轮重跑
