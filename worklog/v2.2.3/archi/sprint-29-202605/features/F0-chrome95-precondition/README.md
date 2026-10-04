# F0: Chrome 95 前置修复（structuredClone polyfill）

**优先级**: P0（阻断后续所有 F1-F5 实现，必须先做）
**状态**: DONE（前置历史提交已完成，本 Sprint 复核确认）
**依赖**: 无

## 目标

修复 `@xyflow/react@12.10.2` 在 Chrome 95 上因调用原生 `structuredClone()` 直接抛 `ReferenceError` 的崩溃问题，让画布在 chrome 95（项目 `legacySupportedBrowsers` 下限）可正常进行连线、节点克隆、状态快照等操作。

## 背景

- xyflow v12 在内部 connection state、node copy 等多处直接调用 `globalThis.structuredClone(...)`。
- `structuredClone` 是 Chrome 98+ / Firefox 94+ / Safari 15.4+ 才有的 API；项目最低支持 Chrome 95，**实测连线时即崩**。
- 现有 SemanticModelCanvas / VisualFlowCanvas 用户量小未爆出，新画布将成数据入湖主入口，**不能带病上线**。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 引入 `@ungap/structured-clone` polyfill 并在 main.tsx 顶部全局挂载 | P0 | DONE | - |

## 完成标准

- [x] `package.json` 已含 `"@ungap/structured-clone": "^1.3.0"`（生产依赖）
- [x] `src/main.tsx` 第 1 行 `import "./polyfills/legacy-browser";`，早于所有业务 import
- [x] 业务代码 grep `structuredClone\(` 仅返回注释，无直接调用
- [x] 现有 SemanticModelCanvas / VisualFlowCanvas 共用同款 polyfill 路径，已在线验证连线不崩
- [x] 证据：`assets/chrome95-polyfill-test-evidence.md`
