# T01: 用共享 Monaco 能力替换 dbt 文本框

**优先级**: P1
**状态**: IN_PROGRESS（源码、聚焦自动化与兼容构建完成；真实 Chrome 95 待补）
**依赖**: F0/T01、F1/T02、F2/T03

## 目标

将 `AdvancedDbtWorkspace` 的 `<textarea>` 替换为职责单一的 `DbtCodeEditor`，提供语法高亮、行号、查找、撤销和保存快捷键，而不引入第二套 SQL IDE。

## 技术设计 (Contract-first)

- **输入契约**: `path:string`、`content:string`、`readOnly:boolean`、`diagnostics:Diagnostic[]`。
- **输出契约**: `onChange(nextContent)`、`onSave()`；组件不直接访问 API，不拥有 draft/transition/release 状态。
- **数据流**: AdvancedDbtWorkspace 选择文件 → DbtCodeEditor 根据 path 建立 Monaco model URI → 编辑回调更新现有 `files[]` → 现有 save API 持久化。
- **错误路径**: 未选文件显示空态；未知扩展名使用 plaintext；超过服务端上限仍由保存 API 返回稳定错误，前端不截断；Monaco 加载失败显示可重试错误，不退回隐藏式可编辑 textarea。
- **复用点**: `@monaco-editor/react`、`configureMonacoLoader`、现有主题；不直接复用带执行命令的 `SqlEditor`，必要时只抽取无业务行为的基础 wrapper。
- **资源约束**: 只为选中文件挂载活跃 editor model；卸载时释放 disposable；文件列表状态由父组件持有。
- **体积约束**（ADR-91-06）: `monaco-editor@0.52` 目前只被 sql-ide 引用，**建模页首屏尚未包含它**（账本 #23）。`DbtCodeEditor` 必须经 `React.lazy` + 动态 `import()` 装载，且只在 `view=code` 且用户具备维护权限时触发。禁止在 `ModelingWorkbenchPage`/`ModelingWorkbenchEditor` 的模块顶层 import monaco。
- **Worker 约束**（ADR-91-06）: 仓库内无 `MonacoEnvironment`/`getWorker` 配置，Monaco 当前运行在主线程（账本 #23）。本 Task **沿用主线程模式，不引入 worker**——`@vitejs/plugin-legacy` 默认不转译 worker chunk，引入 worker 会成为 Chrome 95 上的新增风险面。语法高亮与诊断标记不依赖 worker。

## Definition of Ready

- [x] F1/F2 code view、草稿与只读 preview 状态已稳定。
- [x] editor props、懒加载、容量和 worker 禁止项已冻结。
- [ ] F0/T01 Chrome 95/browser harness 可执行最终 smoke。

## 影响范围

- 新增 `DbtCodeEditor.tsx` 及测试
- `AdvancedDbtWorkspace.tsx`
- 现有 Monaco loader/theme（仅复用或做最小通用抽取）
- workspace CSS

## 验证 (RED→GREEN)

- [ ] SQL/YAML/plaintext language 映射单测。
- [ ] **建模页首屏包不含 monaco**：构建后比对建模路由 chunk，monaco 只出现在懒加载 chunk 中；`view=visual` 时不发起该 chunk 请求。
- [ ] 无 `MonacoEnvironment`/`getWorker` 新增配置（source-contract 断言）。
- [ ] Ctrl/Cmd+S 仅调用 onSave；Ctrl+Enter 不触发执行。
- [ ] 文件 A/B 切换后内容、光标/undo state 不串文件。
- [ ] 128 文件列表与 2 MiB 选中文件的 focused 组件测试不创建 128 个活跃 editor。
- [ ] unmount 后 Monaco listeners/disposables 被释放。

## Definition of Done

- [ ] `<textarea>` 不再是 dbt 实现编辑 owner。
- [ ] 编辑器组件无 API/发布副作用，可独立测试。
- [ ] Chrome95 兼容构建与 IT-05 smoke 可执行。
