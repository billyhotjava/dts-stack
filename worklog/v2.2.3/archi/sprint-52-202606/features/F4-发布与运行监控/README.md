# F4: 发布与运行监控页

**优先级**: P1
**状态**: READY

## 目标

替换 `SemanticPublishPage` 和 `SemanticRunsPage` 两个重定向壳，实现 dbt 发布审核流程和运行历史监控。

## 路由

```
/modeling/semantic/publish  → SemanticPublishPage（新实现）
/modeling/semantic/runs     → SemanticRunsPage（新实现）
```

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | SemanticPublishPage 审核发布流 | P1 | READY | F3/T02 |
| T02 | SemanticRunsPage 运行历史监控 | P1 | READY | T01 |

## 完成标准

- [ ] 两页均不含 `window.location.replace`
- [ ] 发布流：审核 → publish dbt → register BI → register lineage 顺序调用
- [ ] 运行历史：status Tag 时序、支持筛选
- [ ] tsc 零报错
