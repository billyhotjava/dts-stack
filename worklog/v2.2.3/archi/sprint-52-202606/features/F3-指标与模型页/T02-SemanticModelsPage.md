# T02: SemanticModelsPage DWS/ADS 模型管理

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

替换 `SemanticModelsPage.tsx` 壳，实现 DWS/ADS 模型列表，支持预览数据、生成制品、触发 dbt 运行。

## 技术设计

```tsx
// 顶部 Radio.Group: DWS | ADS | 全部（过滤 listSemanticModels({ type })）
//
// CompactTable 列:
//   name, tableName, type(Tag: DWS=blue/ADS=green), grain, status, reviewStatus
//
// 行操作（Space.Compact）:
//   [预览数据]   → previewSemanticModelData(modelId, 50)
//                  Modal 展示前 50 行（antd Table，列动态）
//   [生成制品]   → generateSemanticModelArtifacts(modelId)
//                  成功 toast + 展示 artifact 路径列表
//   [触发运行]   → triggerSemanticModelRun(modelId)
//                  成功 toast
//   [提交审核]   → submitSemanticModelReview(modelId, comment)
//                  Popconfirm + comment Input
//
// [新建模型] Modal → createSemanticModel(data)
// data-testid: "semantic-models-page", "semantic-models-create"
```

API: `listSemanticModels()`, `createSemanticModel()`, `previewSemanticModelData()`, `generateSemanticModelArtifacts()`, `triggerSemanticModelRun()`, `submitSemanticModelReview()`

## 影响范围

- 覆盖：`src/pages/modeling/SemanticModelsPage.tsx`（~300 行）
- 修改：`static-routes.tsx`, `dynamic-resolver.tsx`

## 验证

- [ ] DWS/ADS 过滤 Radio 正常
- [ ] [预览数据] Modal 展示动态列
- [ ] [生成制品] 成功后 toast + 路径列表
- [ ] 不含 `window.location.replace`
- [ ] tsc 零报错

## 完成标准

- [ ] `data-testid="semantic-models-page"` 存在
- [ ] 路由 `/modeling/semantic/models` 可访问
