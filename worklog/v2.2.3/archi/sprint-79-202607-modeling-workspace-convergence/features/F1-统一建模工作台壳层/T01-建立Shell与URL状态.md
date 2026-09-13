# T01：建立 Shell 与 URL 状态

**优先级**：P0  
**状态**：PASS_WITH_GAPS
**依赖**：F0/T01 认证子门禁（已通过）；Chrome 95 在本 Task 完成时补验

## 目标

让用户在一个页面内切换七模块，并通过 URL 稳定恢复计划和对象上下文。

## 技术设计

- **输入**：query `planId?:uuid,module?:enum,assetKind?:enum,assetId?:string`。
- **输出**：规范化 URL；非法 module 回 `home`，无效 asset 清除并显示可恢复空态。
- **数据流**：router → context parser → Shell → active panel；Shell 不调用所有模块 API。
- **复用**：`useSearchParams`、现有 plan selector、loading/error 组件、portal layout。
- **错误路径**：计划 404/403 显示错误卡且不选择其他计划冒充成功。
- **实现**：先抽 `WorkspaceRouteState` 纯函数和契约测试，再建 Shell/TopNav/ObjectTree slots。

## UI

模块键盘可聚焦；对象树可折叠；双滚动容器禁止。屏宽不足时对象树抽屉化，Chrome95 可用。

## 验证

- [x] URL parse/serialize、非法值和刷新测试。
- [x] lazy panel source-contract 证明只装配活动模块。
- [ ] Chrome95 四态截图；当前 Chrome 150 的真实空态/成功态证据见 IT-01。

## Definition of Done

- [x] `/modeling/workbench` 默认 home，`module/workspaceView` 深链刷新无上下文丢失。
- [x] 新增 Shell/Panel/route state 均 ≤800 行，source-contract 已更新。
- [ ] 有计划的 `planId` 与对象 `assetKind/assetId` live 恢复等待代表数据/F2。
