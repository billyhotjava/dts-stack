# F2: ScreenConfig v2 schema

**优先级**: P0
**状态**: READY
**依赖**: —

## 目标

定义 v2 数据模型（TypeScript 类型 + 前端 validation + 后端存储）。v2 与 v1 共存一段时间（demo 期），通过 `version` 字段区分。

## 关键差异

| 维度 | v1 | v2 |
|---|---|---|
| `version` | 缺省 / `1` | `2` |
| `width / height` | 固定像素（1920 / 1080 等） | 可选；若有则作为参考，不绑布局 |
| 组件 `x, y` | 像素坐标 | grid units |
| 组件 `width, height` | 像素 | grid units |
| 布局语义 | 绝对定位 + scale | Grid layout（12 列默认） |
| `layout` 字段 | 无 | 新增：`{ cols, rowHeight, gap }` |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | v2 TypeScript 类型定义 + 默认值 | P0 | READY | — |
| T02 | normalizeScreenConfigV2 / validateScreenConfigV2 | P0 | READY | T01 |
| T03 | 后端 DTO 支持 v2 存储 + 版本字段透传 | P0 | READY | T01 |

## 完成标准

- [ ] 类型定义 export，编辑器 / 渲染器都用同一套
- [ ] 给错误的 v2 JSON 能产出清晰 validation error
- [ ] 后端能原样存取 v2 大屏（不做字段级校验 — 前端负责）
