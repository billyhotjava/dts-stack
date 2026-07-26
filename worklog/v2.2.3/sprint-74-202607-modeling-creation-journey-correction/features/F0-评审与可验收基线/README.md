# F0：评审与可验收基线

**优先级**：P0  
**状态**：DRAFT

## 目标

在任何运行时代码开始前，确认 Sprint-74 架构获批，并让真实认证 API、Chrome95 和代表性数据具备可重复验收条件。

## 契约定义

| 类型 | 契约 | 关键内容 |
|---|---|---|
| 架构 | `assets/architecture-review.md` | 四条复审决定全部确认 |
| 环境 | `it/baseline.md` | P1～P8 有真实命令和结果 |
| 数据 | 隔离验收计划 | 四类模型、两种 implementation ownership、至少一条发布结果 |
| 证据 | `it/` | 真实认证，不用 mock 关闭 Sprint |

## UI/UX 规格

本 Feature 不新增产品 UI。验收入口复用 `/modeling/models` 和模型详情三阶段页面。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 冻结架构与恢复认证验收链 | P0 | DRAFT | 用户确认 Sprint 复审 |
| T02 | 建立四类模型代表性验收数据 | P0 | DRAFT | T01 |

## Definition of Ready

- [x] 架构冲突和在途代码风险已定位
- [x] DNS/login/API/数据缺口已记录
- [ ] 用户确认 `assets/architecture-review.md` 四条决定
- [ ] Sprint-73 owning files 与基线 commit 已冻结

## 完成标准

- [ ] G0 交付基线由 BLOCKED 变为 PASS
- [ ] 代表性 fixture 可重复创建和清理
- [ ] 证据中不包含凭据

