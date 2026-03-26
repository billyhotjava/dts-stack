# F1: 渲染器分层拆分

**优先级**: P0
**状态**: READY

## 目标
将 ComponentRenderer.tsx (4004行) 拆分为三层架构：DataLayer + InteractionLayer + RenderLayer(6个族渲染器)

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 抽取 DataLayer | P0 | READY | - |
| T02 | 抽取 InteractionLayer | P0 | READY | T01 |
| T03 | 拆分 RenderLayer 为6个族渲染器 | P0 | READY | T02 |

## 完成标准
- [ ] ComponentRenderer 缩减为 ~100 行 Shell
- [ ] 6 个族渲染器各 200-500 行
- [ ] 所有 49 种组件正常渲染
