# F5: API 缺口与验收证据

**优先级**: P0  
**状态**: READY

## 目标

把后端补 API 控制在现有页面真实缺口内，并为每个页面重构建立 source-contract、Chrome95 和 smoke 验收。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | API 缺口实测与登记 | P0 | READY | F0 |
| T02 | Source-contract 验收基线 | P0 | READY | F1 F2 F3 |
| T03 | Chrome95 与页面 smoke 证据 | P0 | READY | T02 |

## 完成标准

- [ ] 每个后端新增都有页面来源、字段契约和测试证据。
- [ ] 每个 P0 页面重构都有 source-contract。
- [ ] Chrome95 构建和浏览器 smoke 记录到 `it/README.md`。
