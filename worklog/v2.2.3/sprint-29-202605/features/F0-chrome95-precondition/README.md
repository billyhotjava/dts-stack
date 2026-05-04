# F0: Chrome 95 前置修复（structuredClone polyfill）

**优先级**: P0（阻断后续所有 F1-F5 实现，必须先做）
**状态**: READY
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
| T01 | 引入 `@ungap/structured-clone` polyfill 并在 main.tsx 顶部全局挂载 | P0 | READY | - |

## 完成标准

- [ ] `package.json` 增加 `@ungap/structured-clone` 依赖（生产依赖，非 devDep）
- [ ] `src/main.tsx` 在所有业务 import 之前注入 polyfill
- [ ] Chrome 95 真机/模拟器验证：在任意现有画布（SemanticModelCanvas）尝试连线 → 不再抛 `ReferenceError: structuredClone is not defined`
- [ ] grep 整个仓库确保**业务代码**未直接调用 `structuredClone`（只有 polyfill 与 node_modules）；所有业务侧 deep clone 走 `lodash.cloneDeep` 或 polyfill
- [ ] 启动开发服务器 + production build 双跑通过，bundle 增量 < 2KB（@ungap 实测 ~1KB gz）
