# Chrome 95 兼容性 polyfill — 复核证据（F0-T01 DONE）

> 复核日期：2026-05-04
> 分支：`feat/sprint-29-workflow-canvas`
> 结论：F0-T01 在历史 `fix:chrome95` 提交链中已完成落地，本 Sprint 无需重复实现。

## 1. 依赖确认

`source/dts-platform-webapp/package.json`:

```jsonc
{
  "dependencies": {
    "@ungap/structured-clone": "^1.3.0"
  }
}
```

W3C spec-compliant 的 structuredClone polyfill，gzipped 体积约 1KB。

## 2. 入口加载顺序

`source/dts-platform-webapp/src/main.tsx` 顶部 10 行实测：

```tsx
import "./polyfills/legacy-browser";   // ← Line 1：早于一切业务 import
import "./global.css";
import "./theme/theme.css";
import "./locales/i18n";

import { loader } from "@monaco-editor/react";
import * as monaco from "monaco-editor";
loader.config({ monaco });
```

→ 任何后续模块在 evaluation 时 `globalThis.structuredClone` 已可用。

## 3. polyfill 主体

`source/dts-platform-webapp/src/polyfills/legacy-browser.ts` 含三个能力修补：

| 函数 | 兜底 API | 受益场景 |
|------|----------|----------|
| `ensureStructuredClone()` | `globalThis.structuredClone` | xyflow v12 内部 connection clone / node copy |
| `ensureUrlCanParse()` | `URL.canParse` | 解析外部 connection 字符串，避免 try/catch 噪声 |
| `ensureRandomUUID()` | `crypto.randomUUID` | 节点 ID、DSL 序列化 fallback |

实现策略：仅在缺失时挂载，原生可用时不覆盖（Chrome 98+ / FF 94+ / Safari 15.4+ 零成本）。

类型声明：`source/dts-platform-webapp/src/types/structured-clone.d.ts`。

## 4. 双入口对齐

analytics 子包入口同款 polyfill：`source/dts-platform-webapp/src/analytics/polyfills/legacy-browser.ts`。
新工作流画布按 main 入口走，已自动覆盖。

## 5. 业务代码 grep 验证

```sh
$ grep -rn "structuredClone" source/dts-platform-webapp/src/ \
    --include="*.ts" --include="*.tsx" | grep -v node_modules
```

返回（节选，**全部为注释/类型声明，无直接调用**）：

```text
src/components/sql-ide/result/QueryPlanView.tsx:1: // Chrome 95 兼容:@xyflow/react v12 内部使用 structuredClone,polyfill 通过入口已注入。
src/analytics/pages/semantic/SemanticModelCanvas.tsx:1: // Chrome 95 兼容性:@xyflow/react v12 内部使用 structuredClone(连接拖拽路径)。
src/polyfills/legacy-browser.ts:1: import structuredClonePolyfill from "@ungap/structured-clone";
src/types/structured-clone.d.ts:2: export default function structuredClonePolyfill<T>(
src/analytics/polyfills/legacy-browser.ts:1: import structuredClonePolyfill from "@ungap/structured-clone";
```

→ 业务侧 deep clone 走 `lodash.cloneDeep` 或 polyfill；不依赖原生 `structuredClone`。

## 6. 在线验证（来自历史排查）

历史 `fix:chrome95` 修复后，DevTools 模拟 Chrome 95 在 `SemanticModelCanvas` 反复连线 / 拖拽 / 复制粘贴节点，未再出现 `ReferenceError: structuredClone is not defined`。本 Sprint 工作流画布与之共用 polyfill，因此**继承**该验证结果。

## 7. Sprint-29 衍生约束

- 业务代码（含新画布的 store / DSL / 节点）**不得**直接调用 `structuredClone(...)`：必须走 `lodash.cloneDeep` 或显式 spread。
- 任何引入 `@xyflow/react` ≥ v12 子模块的新页面继承 polyfill，无需复制。
- 后续若升级 `legacySupportedBrowsers` 下限至 Chrome 98+，可通过 `if (typeof globalScope.structuredClone === "function") return;` 守卫天然失效，无需移除 polyfill 文件。
