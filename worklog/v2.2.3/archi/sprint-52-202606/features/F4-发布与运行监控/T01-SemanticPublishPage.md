# T01: SemanticPublishPage 审核发布流

**优先级**: P1
**状态**: READY
**依赖**: F3/T02

## 目标

替换 `SemanticPublishPage.tsx` 壳，实现 ADS 模型审核 → dbt 发布 → BI 注册 → 血缘注册的完整发布流。

## 技术设计

```tsx
// 列表: listSemanticModels({ type: "ADS" }) 过滤 reviewStatus !== "APPROVED"
//
// CompactTable 列: name, tableName, reviewStatus(Tag), submittedBy, submittedAt
// reviewStatus Tag:
//   DRAFT → default
//   SUBMITTED → processing（蓝色）
//   APPROVED → success（绿色）
//   REJECTED → error（红色）
//
// 行操作:
//   [审核通过] → approveSemanticModelReview(modelId, comment)
//                Popconfirm + comment Input
//   [审核拒绝] → rejectSemanticModelReview(modelId, comment)（comment 必填）
//   [发布 dbt] → publishSemanticModelToDbt(modelId)
//                成功后串行: registerSemanticBiDataset(modelId)
//                           registerSemanticLineage(modelId)
//                全程 loading + toast.success/error
//   [查看制品] → Drawer 展示 listSemanticGeneratedArtifacts({ modelId })
//                每条 artifact: path + content（代码块，只读）
//
// [审核日志] Drawer → listSemanticModelReviewLogs(modelId)
// data-testid: "semantic-publish-page"
```

## 影响范围

- 覆盖：`src/pages/modeling/SemanticPublishPage.tsx`（~280 行）
- 修改：`static-routes.tsx`, `dynamic-resolver.tsx`

## 验证

- [ ] [发布 dbt] 成功后串行调 register 两个接口
- [ ] reviewStatus Tag 颜色正确
- [ ] [审核日志] Drawer 展示 review log
- [ ] 不含 `window.location.replace`
- [ ] tsc 零报错

## 完成标准

- [ ] `data-testid="semantic-publish-page"` 存在
- [ ] 路由 `/modeling/semantic/publish` 可访问
