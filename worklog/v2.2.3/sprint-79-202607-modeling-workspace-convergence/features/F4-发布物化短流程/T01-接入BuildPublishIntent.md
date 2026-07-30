# T01：接入 Build / Publish Intent

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F2/T01；Sprint-76 稳定契约

## 目标

从单页编辑器安全发起构建和提交上线，并显示 Candidate 的真实下一动作。

## 技术设计

- **Build 输入**：ModelSpec ETag、Idempotency-Key、body `{planId:string,environment:string}`。
- **Publish 输入**：Candidate ETag、Idempotency-Key、body `{candidateId:string,reason:string}`。
- **输出**：复用 `ModelBuildIntentResult` / `ModelPublicationIntentResult`，以 `outcome,nextHumanAction,blocker,workbenchUrl` 驱动 UI。
- **错误路径**：逻辑/实现门禁、批量候选冲突、quality fail、stale candidate、403 均保留真实 code。
- **禁止**：前端串行调用 approve/publish；前端提交 selector/target/actor。

## UI

按钮状态由 stage-gates 和 Candidate 投影决定。点击后显示阻断、当前步骤和“进入交付工作台”，不做虚假进度动画。

## 验证

- [ ] If-Match/幂等重放/批量冲突/职责分离契约测试。
- [ ] UI outcome 映射覆盖全部枚举。
- [ ] 403 与 blocker 可操作提示。

## Definition of Done

- [ ] IT-05 通过。
- [ ] Candidate/ModelSpec 状态只有服务端 owner 写入。
