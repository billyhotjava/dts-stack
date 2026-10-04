# T01：接入 Build / Publish Intent

**优先级**：P0  
**状态**：IN_PROGRESS（canonical Intent 边界已冻结；统一 UI/真实 Candidate 旅程待 IT-05）
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

## 当前实现边界

- 工作台仍只调用既有 Build/Publish Intent 与 Candidate owner，不新增发布状态表或前端 approve/publish 串联。
- 本轮完成的是下游运行时租约安全收口，不把它等同于 IT-05 发布旅程通过。

## 2026-08-09 日期维度物化回归证据

- 修复 `MATERIALIZATION_ARTIFACT_MISSING`：工作台先读取已保存的 `ModelImplementation` 并调用 canonical lifecycle compile，编译成功后才创建、锁定 Candidate；编译失败或实现缺失时不创建 Candidate。
- 定向 Vitest：3 个文件、17 个用例通过；`pnpm build`（TypeScript + legacy browser 产物）通过。
- 受控浏览器回归：1/1 通过，写调用严格为 `lifecycle/compile → release-candidates → candidate/lock`，无非预期写请求、控制台错误或请求失败；1366×768 与 768×900 截图位于 `/tmp/dts-modeling-workbench-results/`。
- 本机只有 Chrome 150，未提供 Chrome 95 可执行文件；本轮已验证 Chrome 95 构建目标与现代 Chromium 运行，但不替代 Chrome 95 真机回归。
- 保存的真实认证态已失效并返回 401，因此浏览器写链采用受控 API fixture，未改动共享租户数据；IT-05 的真实认证 Candidate 旅程仍保持 PENDING。
