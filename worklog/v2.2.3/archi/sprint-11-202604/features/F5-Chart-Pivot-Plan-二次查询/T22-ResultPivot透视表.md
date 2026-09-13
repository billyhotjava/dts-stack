# T22: ResultPivot（透视表）

**优先级**: P1
**状态**: READY
**依赖**: F4

## 目标

实现 Excel 式透视表：拖拽字段到 行/列/值 区域，支持 6 种聚合函数，客户端计算（≤10k 行）。

## 技术设计

### 技术

- 用 `@tanstack/react-table` 的 grouping + aggregation 原生能力
- 若不够用，fallback 到 `react-pivottable`（MIT，成熟方案）

### UI 布局

```
┌──────────────┬─────────────────────┐
│ 可用字段     │  透视结果表格       │
│ ─ name       │                     │
│ ─ region     │  (rows/cols/vals    │
│ ─ category   │   拖拽后的透视)     │
│ ─ sales      │                     │
├──────────────┤                     │
│ ROWS         │                     │
│ [region ✕]   │                     │
│              │                     │
│ COLUMNS      │                     │
│ [category ✕] │                     │
│              │                     │
│ VALUES       │                     │
│ [SUM(sales)] │                     │
└──────────────┴─────────────────────┘
```

### 聚合函数

- SUM
- COUNT
- AVG
- MIN
- MAX
- DISTINCT COUNT

### 规模限制

- 客户端透视上限 10k 行
- 超限禁用 + 提示"数据量过大，请在 SQL 中用 GROUP BY 预聚合"

### 额外功能

- 小计 / 总计行切换
- 透视结果可导出（CSV）
- 支持"交换行列"按钮

## 影响范围

- 新增 `result/ResultPivot.tsx`
- 可能新增 `react-pivottable` 依赖

## 验证

- [ ] 拖拽三个字段到行/列/值，结果正确
- [ ] 6 种聚合函数输出正确
- [ ] 10k 行以内流畅
- [ ] 超限提示生效

## 完成标准

- [ ] 功能可用
- [ ] 性能达标
