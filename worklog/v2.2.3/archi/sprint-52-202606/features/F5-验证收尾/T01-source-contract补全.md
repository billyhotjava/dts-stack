# T01: source-contract 测试补全

**优先级**: P0
**状态**: READY
**依赖**: F1/T01, F2/T01, F3/T01, F4/T01

## 目标

新建 `metricWorkbench.source-contract.test.ts`，并在 `dataDevelopmentWorkbench.source-contract.test.ts` 追加路由断言。

## 技术设计

### 新建: `src/pages/modeling/metricWorkbench.source-contract.test.ts`

```typescript
// 读取 MetricWorkbenchPage.tsx
assert.match(SOURCE, /ReactFlow/)                           // 画布引擎
assert.match(SOURCE, /BizObjectNode/)                       // 节点类型
assert.match(SOURCE, /MetricNode/)                          // 节点类型
assert.match(SOURCE, /listSemanticSubjectDomains/)          // API 接入
assert.match(SOURCE, /listSemanticBusinessObjects/)
assert.match(SOURCE, /listSemanticMetrics/)
assert.doesNotMatch(SOURCE, /window\.location\.replace/)    // 非跳转壳
assert.doesNotMatch(SOURCE, /oklch|:has\(|@container/)      // Chrome 95

// 读取全部 Semantic*Page.tsx（6个）
// 对每个文件断言:
assert.doesNotMatch(FILE, /window\.location\.replace/)      // 壳已替换
```

### 追加到: `dataDevelopmentWorkbench.source-contract.test.ts`

```typescript
// 在 "data development workbench routes converge" test 追加:
assert.match(staticRoutes, /path: "modeling\/metric-workbench"/)
assert.match(dynamicResolver, /"\/modeling\/metric-workbench"/)
assert.match(staticRoutes, /path: "modeling\/semantic\/subjects"/)
assert.match(staticRoutes, /path: "modeling\/semantic\/objects"/)
assert.match(staticRoutes, /path: "modeling\/semantic\/metrics"/)
assert.match(staticRoutes, /path: "modeling\/semantic\/models"/)
assert.match(staticRoutes, /path: "modeling\/semantic\/publish"/)
assert.match(staticRoutes, /path: "modeling\/semantic\/runs"/)
```

## 影响范围

- 新建：`src/pages/modeling/metricWorkbench.source-contract.test.ts`
- 修改：`src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts`

## 验证

- [ ] `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts` 全部通过
- [ ] `dataDevelopmentWorkbench.source-contract.test.ts` 新增断言通过
- [ ] 不引入新的 baseline 失败

## 完成标准

- [ ] 两个 source-contract 文件均绿
