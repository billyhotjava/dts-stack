# F2: 主题域与业务对象页

**优先级**: P0
**状态**: READY

## 目标

将 `SemanticSubjectsPage` 和 `SemanticObjectsPage` 两个重定向壳替换为真实页面，分别实现主题域管理和业务对象 join 配置。

## 路由

```
/modeling/semantic/subjects  → SemanticSubjectsPage（新实现）
/modeling/semantic/objects   → SemanticObjectsPage（新实现）
```

两条路由须同步注册到 `static-routes.tsx` + `dynamic-resolver.tsx`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | SemanticSubjectsPage 真实实现 | P0 | READY | - |
| T02 | SemanticObjectsPage 真实实现（含 join 画布） | P0 | READY | T01 |

## 完成标准

- [ ] 两个页面不含 `window.location.replace`
- [ ] 页面标题、CompactTable、操作按钮均可见
- [ ] SemanticObjectsPage 的 join 画布（VisualFlowCanvas）渲染表映射关系
- [ ] tsc 零报错
