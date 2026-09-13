# T01：接入统一 context 与双视图共享状态

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：F1/T02、F2/T01

## 目标

让模型工作台通过一个 authoring context/provider 管理 visual/code 的 pins、draft、dirty、权限和错误状态，切换视图不触发写入。

## 技术设计（Contract-first）

- **输入契约**：route `modelSpecId/view`；`AuthoringContextView`/`AuthoringDraftView`；账本 L01～L04。
- **输出契约**：`ModelAuthoringDraftState {context,draftId,etag,modelSnapshot,files,projection,dirtyByView,busy,diagnostics}`；组件只消费 provider。
- **数据流**：URL → GET context → lazy draft/file load → provider → visual/code components。
- **错误路径**：context 失败不猜测 editability；无技术权限不发 files 请求；切换前后 dirty 保留；刷新有 dirty 时确认。
- **复用点**：`ModelingWorkbenchPage/Editor`、`AdvancedDbtWorkspace`、现有 URL normalization/request failure adapter。
- **兼容**：`open=advanced` 归一化为 `view=code`；不新增路由/菜单。

## UI 交互规格

模型上下文条显示状态、pins、来源、projection；模式按钮保持同一位置；loading/permission/error 使用既有 RequestState。

## 影响范围

`ModelingWorkbenchPage.tsx`、`ModelingWorkbenchEditor.tsx`、authoring API/client/state adapter 及 source-contract/Vitest；不新增路由、菜单或页面。

## 验证（RED→GREEN）

- [ ] source-contract 先断言新 context 调用、无 ownership disabled 和无重复入口。
- [ ] Vitest：前进/后退/刷新/切换、dirty 保留、无权限不请求正文。
- [ ] bundle analysis：visual 首屏不含 Monaco。

## Definition of Done

- [ ] 视图切换零写请求、零 revision。
- [ ] 三种来源进入相同 provider 和模式控件。
- [ ] 空/加载/错误/权限/成功/PUBLISHED 状态可测试。
