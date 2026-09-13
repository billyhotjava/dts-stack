# T02: SemanticRunsPage 运行历史监控

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

替换 `SemanticRunsPage.tsx` 壳，实现跨模型运行历史列表，支持按 status 过滤和手动触发运行。

## 技术设计

```tsx
// 顶部 Select: 选择模型（listSemanticModels() 下拉）
// 选中模型后: listSemanticModelRuns(modelId)
//
// CompactTable 列:
//   modelId, runType, status(Tag), triggeredBy, startedAt, finishedAt, durationMs
// status Tag:
//   PENDING → default
//   RUNNING → processing（蓝色）
//   SUCCESS → success（绿色）
//   FAILED  → error（红色）
//   CANCELLED → warning（橙色）
//
// 操作:
//   [手动触发] → triggerSemanticModelRun(modelId, { runType: "MANUAL" })
//   行 [查看日志] → Drawer 展示 run.message + run.payloadJson（只读 JSON）
//
// 轮询: 有 RUNNING 状态时每 10s 刷新（setInterval + clearInterval on unmount）
// data-testid: "semantic-runs-page"
```

## 影响范围

- 覆盖：`src/pages/modeling/SemanticRunsPage.tsx`（~220 行）
- 修改：`static-routes.tsx`, `dynamic-resolver.tsx`

## 验证

- [ ] status Tag 颜色正确
- [ ] 有 RUNNING 状态时自动轮询（10s）
- [ ] [手动触发] 成功后 toast + 列表刷新
- [ ] 不含 `window.location.replace`
- [ ] tsc 零报错

## 完成标准

- [ ] `data-testid="semantic-runs-page"` 存在
- [ ] 路由 `/modeling/semantic/runs` 可访问
