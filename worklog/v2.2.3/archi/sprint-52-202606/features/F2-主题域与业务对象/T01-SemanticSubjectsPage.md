# T01: SemanticSubjectsPage 真实实现

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

替换 `SemanticSubjectsPage.tsx` 重定向壳，实现主题域列表管理（列表 + 新建 + 编辑），展示与治理域的映射关系。

## 技术设计

```tsx
// 替换 src/pages/modeling/SemanticSubjectsPage.tsx
// PageHeader: "语义建模 · 主题域"
// CompactTable 列: code, name, description, governanceDomainName, status
// 操作: [新建主题域] Modal → createSemanticSubjectDomain(data)
//        行内 [编辑] → updateSemanticSubjectDomain(id, data)
// 治理域下拉: 复用现有 governance 域 API（只读下拉，不新增接口）
// data-testid: "semantic-subjects-page", "semantic-subjects-create"
```

API 使用：
- `listSemanticSubjectDomains()` → 列表
- `createSemanticSubjectDomain(data)` → 新建
- `updateSemanticSubjectDomain(id, data)` → 编辑

新增路由（`static-routes.tsx` + `dynamic-resolver.tsx`）：
- `path: "modeling/semantic/subjects"` → `<SemanticSubjectsPage />`

## 影响范围

- 覆盖：`src/pages/modeling/SemanticSubjectsPage.tsx`（从 6 行壳扩展为 ~180 行）
- 修改：`static-routes.tsx`, `dynamic-resolver.tsx`

## 验证

- [ ] 页面不含 `window.location.replace`
- [ ] CompactTable 展示主题域列表
- [ ] [新建] Modal 提交成功后列表刷新
- [ ] tsc 零报错，source-contract 不新增失败

## 完成标准

- [ ] `data-testid="semantic-subjects-page"` 存在
- [ ] 路由已注册且可访问
