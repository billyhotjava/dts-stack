# F1: DTS 前端重构 skills 固化

**优先级**: P0
**状态**: DONE

## 目标

把 DTS 后续前端重构的项目规则沉淀成可复用 skills，让后续编码默认遵守“页面优先、少新增入口、按钮真实闭环、客户语言、Chrome95 验证”。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 页面能力审计 skill | P0 | DONE | - |
| T02 | feature/菜单/客户语言/Chrome95 skills | P0 | DONE | T01 |
| T03 | skills 验证与项目使用规则 | P0 | DONE | T02 |

## 完成标准

- [x] 新增 `dts-page-capability-audit`
- [x] 新增 `dts-frontend-feature-matrix`
- [x] 新增 `dts-menu-route-convergence`
- [x] 新增 `dts-customer-language-polish`
- [x] 新增 `dts-chrome95-regression`
- [x] 所有 skill 通过 `quick_validate.py`
