# F4: TDD 与 Chrome95 验收基线

**优先级**: P0
**状态**: DONE

## 目标

为后续 DTS UI 编码定义测试优先和浏览器验收基线，防止“改了页面但没有证据”的交付方式。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | source-contract TDD 入口 | P0 | DONE | - |
| T02 | Chrome95 与表格布局验收 | P0 | DONE | T01 |
| T03 | IT 证据记录规范 | P0 | DONE | T02 |

## 完成标准

- [x] 每个页面整改前先写失败的 source-contract 或单测
- [x] 表格、抽屉、弹窗、长中文按钮必须做浏览器验收
- [x] 验收证据必须记录到 sprint `it/README.md`
