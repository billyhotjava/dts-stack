# F0: 交付基线与缺口冻结

**优先级**: P0
**状态**: READY

## 目标
在写第一行实现代码前，证明本 Sprint 的验收路径真实可走：有可运行实例、能登录、能打开数据安全页、Chrome95 可用；并把 v4 缺口复核冻结为本 Sprint 的唯一对账基准。

> 出处：domain-dts「已知未闭合缺口」——浏览器验收基线（登录/DNS）长期不稳（Sprint-61~64），UI 验收须先过 delivery-baseline。本 Sprint 有 3 个 Feature 带 UI，基线不过则它们全部停在 DRAFT。

## 契约定义 (Contracts)

| 类型 | 契约 | 关键字段/签名 |
|------|------|---------------|
| 文档 | `it/baseline.md` | 实例地址、登录账号形态（本地/PKI）、四个待验证页面的可达性、Chrome95 executable 路径 |
| 文档 | `assets/protocol-gap-frozen.md` | 从 `worklog/v2.2.3/protocol-gap/protocol-gap-review-20260821.md` 冻结的 11 P0 + 25 P1 清单快照 |

## UI/UX 规格
本 Feature 无新界面。需验证的既有页面：
- 数据安全页 `pages/security/data-security.tsx`（F2/F3 的挂载点）
- 资产台账（F3 扫描范围选择的上游）

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 交付基线核验与缺口冻结 | P0 | READY | - |

## Definition of Ready
- [x] 契约已钉死（两份产物）  - [x] 竖切片：无代码路径  - [x] UI 落点：既有页面  - [x] 依赖：无  - [x] 验收可验证

## 完成标准
- [ ] `it/baseline.md` 四项全部有实测结论（不是"应该可以"）
- [ ] 基线任一项失败 → 在本文件登记阻断原因，F2/F3/F4 保持 DRAFT
