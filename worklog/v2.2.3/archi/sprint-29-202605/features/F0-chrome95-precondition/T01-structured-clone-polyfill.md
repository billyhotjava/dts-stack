# T01: 引入 `@ungap/structured-clone` polyfill 并在 main.tsx 顶部全局挂载

**优先级**: P0
**状态**: DONE（已在历史 `fix:chrome95` 中完成，本 Sprint 无需重复实现）
**依赖**: 无

## DONE 说明（2026-05-04 复核）

历史排查时已为 `SemanticModelCanvas` / `VisualFlowCanvas` 落地同一方案，本 Sprint 直接复用：

| 组件 | 现状 |
|------|------|
| `package.json` | `"@ungap/structured-clone": "^1.3.0"`（生产依赖） |
| `src/polyfills/legacy-browser.ts` | 含 `ensureStructuredClone()` + `ensureUrlCanParse()` + `ensureRandomUUID()` 三个 polyfill |
| `src/main.tsx` 第 1 行 | `import "./polyfills/legacy-browser";`（早于业务 import） |
| `src/types/structured-clone.d.ts` | `@ungap/structured-clone` 模块类型声明 |
| 重复挂载位置 | `src/analytics/polyfills/legacy-browser.ts`（analytics 子入口同款） |

业务代码 grep 结果（仅注释引用，无直接调用）：

```text
src/components/sql-ide/result/QueryPlanView.tsx:1:// Chrome 95 兼容:@xyflow/react v12 内部使用 structuredClone,polyfill 通过入口已注入。
src/analytics/pages/semantic/SemanticModelCanvas.tsx:1:// Chrome 95 兼容性:@xyflow/react v12 内部使用 structuredClone(连接拖拽路径)。
```

→ Sprint-29 工作流画布直接受益，无需新增任何代码。证据归档见 `assets/chrome95-polyfill-test-evidence.md`。

## 目标

让 `globalThis.structuredClone` 在 Chrome 95 上可用，使 `@xyflow/react@12.10.2` 内部的连线/克隆代码不再崩溃。

## 技术设计

### 依赖

```jsonc
// source/dts-platform-webapp/package.json
{
  "dependencies": {
    "@ungap/structured-clone": "^1.2.0"   // ~1KB gz, W3C spec-compliant
  }
}
```

### 注入位置

`src/main.tsx` **第一行**（必须在所有业务 import 之前）：

```ts
import structuredCloneShim from "@ungap/structured-clone";

if (typeof globalThis.structuredClone !== "function") {
  // 仅在缺失时挂载，原生可用时不覆盖（Chrome 98+/Firefox 94+/Safari 15.4+）
  (globalThis as { structuredClone?: typeof structuredCloneShim }).structuredClone = structuredCloneShim;
}

// ... 其余 import
```

### 为什么不用 vite-plugin-legacy 自动 polyfill？

- legacy plugin 只处理语法（class fields、optional chaining 等），**不处理新增 Web API**
- core-js 的 `structuredClone` polyfill 体积更大（依赖 transferable 全套），不符合"只补缺"原则

## 影响范围

| 类型 | 路径 |
|------|------|
| 配置 | `source/dts-platform-webapp/package.json`、`pnpm-lock.yaml` |
| 源码 | `source/dts-platform-webapp/src/main.tsx` |
| 文档 | sprint README 已记录 |

不影响后端，不影响其他子模块。

## 验证

- [ ] `pnpm install` 后 lockfile 包含 `@ungap/structured-clone`
- [ ] `pnpm build` 通过；bundle 增量 < 2KB（gzipped）
- [ ] Chrome 95 真机 / DevTools "Chrome 95" 模拟下访问 `/explore/etl` 任一画布页：连线一次 → 不抛 `ReferenceError`
- [ ] grep 业务代码确认未直接调用 `structuredClone`，所有 deep clone 走 lodash 或 polyfill
- [ ] DevTools console 无 `__esModule` 注入告警

## 完成标准

- [x] `package.json` 声明 `@ungap/structured-clone@^1.3.0`
- [x] `src/main.tsx` 首行加载 `polyfills/legacy-browser`
- [x] 业务代码 grep 无直接 `structuredClone(` 调用（仅注释）
- [x] sprint-29 assets 下补充 `assets/chrome95-polyfill-test-evidence.md`
- [x] 现有 SemanticModelCanvas / VisualFlowCanvas 已使用同一 polyfill 路径，新画布零额外动作受益
