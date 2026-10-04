# F3: 指标与模型页

**优先级**: P0
**状态**: READY

## 目标

替换 `SemanticMetricsPage` 和 `SemanticModelsPage` 两个重定向壳，实现指标设计器和 DWS/ADS 模型管理页。

## 路由

```
/modeling/semantic/metrics  → SemanticMetricsPage（新实现）
/modeling/semantic/models   → SemanticModelsPage（新实现）
```

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | SemanticMetricsPage 指标设计器 | P0 | READY | - |
| T02 | SemanticModelsPage DWS/ADS 模型管理 | P0 | READY | T01 |

## 完成标准

- [ ] 两页均不含 `window.location.replace`
- [ ] 指标列表展示 formulaType/formulaJson
- [ ] 模型列表支持预览 SQL、生成制品、触发运行
- [ ] tsc 零报错
