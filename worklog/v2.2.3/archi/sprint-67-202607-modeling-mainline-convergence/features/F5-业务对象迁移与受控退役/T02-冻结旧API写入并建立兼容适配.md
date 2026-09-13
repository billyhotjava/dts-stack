# T02：冻结旧 API 写入并建立兼容适配

**优先级**：P0
**状态**：DONE
**依赖**：F5-T01、F4-T02

## 目标与用户结果

新用户只走 ModelSpec；旧链接和只读消费者仍可定位数据，但不能继续制造新的业务对象或双写。

## 范围与不做

- 范围：semantic/vNext business-object CRUD、旧 subject domain 写、read adapter、Sunset/telemetry、feature flag。
- 不做：兼容 API 不接受新对象，不把旧 API 永久当 canonical。

## 输入契约

旧 GET 接受 legacy objectId 并返回 deprecation metadata/targetRef；POST/PUT/PATCH 默认拒绝。管理员迁移批次使用独立内部接口和权限，不复用公共写 API。

## 输出产物

- 旧写 `410 BUSINESS_OBJECT_RETIRED`（或冻结期 409）稳定响应；
- legacy read projection/redirect target；
- Deprecation/Sunset/Link headers；
- 调用方、租户、route、时间的审计统计；
- 回滚读开关。

## 详细设计

1. `/api/semantic/business-objects` 和 `/api/modeling/vnext/business-objects` 写方法先 feature-flag 冻结，默认生产开启冻结。
2. GET 通过 migration mapping 返回 DIMENSION/FACT 目标；NEEDS_CLASSIFICATION 只返回处理状态和安全修复入口。
3. `/api/semantic/subject-domains` 写入冻结，分类写统一到 catalog domain。
4. 前端旧页面只执行 redirect/read export，不再渲染创建抽屉或调用写 API。
5. 审计指标用于退出判断；不记录敏感 payload。

## 影响范围

- `SemanticModelingResource/Service`
- `ModelingVNextResource/ApplicationService`
- API gateway/response headers/audit
- `semanticModelingApi.ts`、`modelingApi.ts`
- `SemanticObjectsPage.tsx` compatibility component

## 异常、权限与回滚

- 无映射返回 NEEDS_CLASSIFICATION，不猜目标。
- 原对象权限继续约束目标投影；无权返回 404/403 按现策略。
- 回滚只打开旧 GET/read view；旧 POST/PUT 不恢复。

## 实施与测试设计

1. 先写旧写拒绝、header、审计和映射状态测试。
2. 实现 flag、read adapter 和 redirect。
3. 逐一修改调用者并观察调用统计。
4. 验证 rollback flag 不引发双写。

## 验证证据

- `it/evidence/api/business-object-write-freeze.txt`
- `it/evidence/runtime/legacy-api-usage.json`
- `it/evidence/frontend/legacy-object-redirect.txt`

## 完成标准

- [x] 旧公共 API 无新写。
- [x] 有映射对象可安全定位目标。
- [x] 无映射对象不会自动错误迁移。
- [x] 调用统计足以判断消费者归零。
