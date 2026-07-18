# T03：实现统一规划与基线 API

- **状态**：IN_PROGRESS
- **优先级**：P0
- **依赖**：T02
- **影响模块**：dts-platform REST、DTO、异常映射、OpenAPI/source-contract

## 目标

提供统一计划与规划基线 API，替代前端 session 事实源，并为 F5 的阶段投影定义共享的引用、错误和新鲜度契约。

## 实施内容

1. 实现计划 list/create/detail/update/archive。
2. 实现 business-scope、sources、source-mappings、policy 和 baseline confirm 命令；每个编辑单元使用自己的 ETag/If-Match，不提供第二套绑定路径。
3. 定义 models、stage-projection、evidence 和 deliverables 的查询 DTO/错误契约，由 F5 实现真实聚合；本 Task 不注册返回占位状态的假接口。
4. 统一分页、过滤、租户、权限、乐观锁和错误响应。
5. 维护前后端 source-contract，禁止前端自造未返回字段。

## 验收标准

- Create API 的 onboardingMode 只影响首屏建议；
- confirm API 返回缺失项而非部分写入完成；
- 查询聚合失败返回 UNKNOWN/STALE，不把异常吞成空数组；
- 未完成真实聚合前不存在伪造 COMPLETE 或固定样例的查询端点；
- 错误码和 HTTP 状态稳定；
- 同一编辑单元冲突返回 409，不同编辑单元并发提交均可成功；
- API 契约测试和无权访问测试通过。

## 验证证据

- API contract tests；
- 脱敏请求/响应样例；
- 权限、404、409 和下游失败用例；
- source-contract 测试结果。
