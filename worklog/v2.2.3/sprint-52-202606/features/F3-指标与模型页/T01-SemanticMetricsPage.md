# T01: SemanticMetricsPage 指标设计器

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

替换 `SemanticMetricsPage.tsx` 壳，实现指标列表 + 内联编辑公式 + 新建指标表单。

## 技术设计

```tsx
// CompactTable 列: name, code, formulaType(Tag), unit, status(Tag), objectId
// formulaType Tag 颜色:
//   aggregation/sum → blue
//   aggregation/count_distinct → green
//   aggregation/avg → orange
//   其他 → default
//
// 行内 [编辑公式] → Drawer:
//   formulaType Select（预设选项）
//   formulaJson Input.TextArea（原始 JSON，宽松模式不校验格式）
//   unit Input, objectId Select（来自 listSemanticBusinessObjects()）
//   [保存] → updateSemanticMetric(id, data)
//
// [新建指标] Modal → createSemanticMetric(data)
// data-testid: "semantic-metrics-page", "semantic-metrics-create"
```

API: `listSemanticMetrics()`, `createSemanticMetric()`, `updateSemanticMetric()`

## 影响范围

- 覆盖：`src/pages/modeling/SemanticMetricsPage.tsx`（~200 行）
- 修改：`static-routes.tsx`, `dynamic-resolver.tsx`

## 验证

- [ ] 不含 `window.location.replace`
- [ ] formulaType Tag 颜色正确
- [ ] [编辑公式] Drawer 保存后列表刷新
- [ ] tsc 零报错

## 完成标准

- [ ] `data-testid="semantic-metrics-page"` 存在
- [ ] 路由 `/modeling/semantic/metrics` 可访问
