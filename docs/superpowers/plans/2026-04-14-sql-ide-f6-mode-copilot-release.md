# SQL IDE F6: 简洁/高级模式 + Copilot 插槽 + Sprint 收尾 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 加入简洁/高级双模态切换（单组件 + `mode` 字段条件渲染，不搞两套组件），用户偏好持久化到 localStorage；AI Copilot 占位面板 + 清晰的契约预定义（future NL2SQL/explain/optimize 对接点）；更新 Sprint 文档落地灰度上线 + 回滚 SOP + 回归对照表。

**Architecture:** 新增 `useUiModeStore` Zustand 全局 UI 模式 store（`simple | advanced`），localStorage 持久化。以 prop 方式把 `mode` 传到 SqlEditor、SchemaTree、BottomTabs，在现有组件内用 mode 值门控差异（minimap、activity icons、context menu 数量、快捷键 help、log rewrittenSql 等），不新增组件树。Copilot 契约在 `copilot/types.ts` 里定义 TypeScript 类型（CopilotRequest、CopilotResponse、CopilotProvider），CopilotSlot 占位面板已存在（F3 T12），本 Sprint 加接入文档 + `copilot/README.md`。T28 交付文档清单（回归、性能、灰度/回滚 SOP），不含代码。

**Tech Stack:** Zustand 4（已有）+ localStorage + TypeScript。

---

## Spec Reference

- Sprint README: `worklog/v2.2.3/sprint-11-202604/README.md`
- Feature README: `worklog/v2.2.3/sprint-11-202604/features/F6-简洁高级模式与Copilot插槽/README.md`
- Tasks: T26–T28

## Dependencies

- F1–F5 全部完成
- F3/T12 已经创建 `copilot/CopilotSlot.tsx` 占位面板（本 Sprint 扩展其接口定义）
- F3/T13 SchemaTree 有右键菜单（本 Sprint 按 mode 缩减简洁模式菜单项）
- F4/T17 ResultGrid 已就位
- F4/T18 useSqlExecution + ShortcutsHelp 已就位
- F5/T25 LogPanel 已有 `showAdvanced?` prop（本 Sprint 通过 mode 驱动）

## Scope Check

T28 is largely documentation (regression matrix / perf report / gray rollout SOP / rollback SOP / architecture doc / user guide). No code changes. It's small enough to fit in the same plan rather than decomposing.

## File Structure

**Frontend (新增):**

| 路径 | 职责 |
|---|---|
| `src/components/sql-ide/store/useUiModeStore.ts` | Zustand: `{ mode: "simple"\|"advanced", setMode(), hydrate(), __resetForTest() }` |
| `src/components/sql-ide/ModeSwitcher.tsx` | 右上角 toggle: `简洁 ⇄ 高级` |
| `src/components/sql-ide/store/__tests__/useUiModeStore.test.ts` | TDD 5 tests: 默认值、setMode、persist、hydrate、reset |
| `src/components/sql-ide/copilot/types.ts` | `CopilotRequest`, `CopilotResponse`, `CopilotProvider` 接口 |
| `src/components/sql-ide/copilot/README.md` | 接入指南（NL2SQL / explain / optimize 三种模式说明 + Provider 实现示例） |

**Frontend (修改):**

| 路径 | 改动 |
|---|---|
| `editor/SqlEditor.tsx` | 已接收 `mode` prop，确保 minimap 基于 mode 开关 + simple 模式下工具栏按钮减少 |
| `layout/ActivityBar.tsx` | simple 模式只显示 Schema/History/Saved 3 个 icon（隐藏 Search + Copilot） |
| `schema/SchemaContextMenu.tsx` | simple 模式菜单项: `Generate SELECT` + `Copy Name` 两个；advanced 添加 `Generate INSERT` |
| `result/LogPanel.tsx` | `showAdvanced` prop 改成读 mode store（不用 prop 了），或保留 prop 且上游传 mode === "advanced" |
| `SqlIde.tsx` | 引入 useUiModeStore + 顶部渲染 ModeSwitcher，往下层组件传 mode |
| `copilot/CopilotSlot.tsx` | 简洁模式下不渲染（return null），以及在占位文案里引用 README 路径 |

**Sprint 文档（T28 交付）:**

| 路径 | 内容 |
|---|---|
| `worklog/v2.2.3/sprint-11-202604/it/e2e-report.md` | Playwright E2E 用例清单 + 执行结果（占位/计划表） |
| `worklog/v2.2.3/sprint-11-202604/it/perf-report.md` | 性能指标目标 + 实测数据（首渲/切Tab/10k行滚动/100k导出） |
| `worklog/v2.2.3/sprint-11-202604/it/regression-matrix.md` | 老 SqlWorkbenchExperimental 功能 → 新版对照（17+ 项） |
| `worklog/v2.2.3/sprint-11-202604/it/audit-coverage.md` | 13 类审计实测清单 |
| `worklog/v2.2.3/sprint-11-202604/it/grayscale-runbook.md` | 灰度上线 SOP |
| `worklog/v2.2.3/sprint-11-202604/it/rollback-runbook.md` | 回滚 SOP（Feature flag 一键） |
| `worklog/v2.2.3/sprint-11-202604/assets/user-guide.md` | 用户手册（快捷键 / 简洁高级模式 / 常见场景） |
| `worklog/v2.2.3/sprint-11-202604/assets/architecture.md` | 开发者文档（组件树 + 数据流） |

---

## Task 1 — T26: 简洁/高级模式切换 + 用户偏好持久化 (TDD store)

**Files:**
- Create: `src/components/sql-ide/store/useUiModeStore.ts`
- Create: `src/components/sql-ide/store/__tests__/useUiModeStore.test.ts`
- Create: `src/components/sql-ide/ModeSwitcher.tsx`
- Modify: `src/components/sql-ide/SqlIde.tsx` (render ModeSwitcher + pass mode down)
- Modify: `src/components/sql-ide/layout/ActivityBar.tsx` (simple hides Search + Copilot)
- Modify: `src/components/sql-ide/schema/SchemaContextMenu.tsx` (simple drops "Generate INSERT")
- Modify: `src/components/sql-ide/result/LogPanel.tsx` (read mode from store; drop `showAdvanced` prop)
- Modify: `src/components/sql-ide/result/BottomTabs.tsx` (drop `showAdvanced` prop pass)

### - [ ] Step 1.1: TDD useUiModeStore tests

`src/components/sql-ide/store/__tests__/useUiModeStore.test.ts`:

```typescript
import { beforeEach, describe, expect, it } from "vitest";
import { useUiModeStore } from "../useUiModeStore";

describe("useUiModeStore", () => {
  beforeEach(() => {
    useUiModeStore.getState().__resetForTest();
  });

  it("defaults to simple mode", () => {
    expect(useUiModeStore.getState().mode).toBe("simple");
  });

  it("setMode changes the mode", () => {
    useUiModeStore.getState().setMode("advanced");
    expect(useUiModeStore.getState().mode).toBe("advanced");
  });

  it("setMode persists to localStorage", () => {
    useUiModeStore.getState().setMode("advanced");
    const raw = window.localStorage.getItem("sqlide.ui-mode.v1");
    expect(raw).toBeDefined();
    expect(JSON.parse(raw!).mode).toBe("advanced");
  });

  it("__resetForTest clears localStorage and restores default", () => {
    useUiModeStore.getState().setMode("advanced");
    useUiModeStore.getState().__resetForTest();
    expect(useUiModeStore.getState().mode).toBe("simple");
    expect(window.localStorage.getItem("sqlide.ui-mode.v1")).toBeNull();
  });

  it("setMode rejects invalid values via TypeScript — compile-time only", () => {
    // This test asserts that setMode accepts only "simple" | "advanced" — compile-time.
    // At runtime nothing is validated, so we just verify no throw with valid values.
    expect(() => useUiModeStore.getState().setMode("simple")).not.toThrow();
    expect(() => useUiModeStore.getState().setMode("advanced")).not.toThrow();
  });
});
```

### - [ ] Step 1.2: Run vitest → FAIL

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp
pnpm vitest run src/components/sql-ide/store/__tests__/useUiModeStore.test.ts
```

**Expected:** FAIL — module not found.

### - [ ] Step 1.3: Implement useUiModeStore

`src/components/sql-ide/store/useUiModeStore.ts`:

```typescript
import { create } from "zustand";

export type UiMode = "simple" | "advanced";

const STORAGE_KEY = "sqlide.ui-mode.v1";
const DEFAULT_MODE: UiMode = "simple";

function loadMode(): UiMode {
  if (typeof window === "undefined") return DEFAULT_MODE;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_MODE;
    const parsed = JSON.parse(raw) as { mode?: UiMode };
    return parsed.mode === "advanced" ? "advanced" : DEFAULT_MODE;
  } catch {
    return DEFAULT_MODE;
  }
}

function persistMode(mode: UiMode): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ mode }));
  } catch {
    /* ignore quota */
  }
}

interface UiModeStore {
  mode: UiMode;
  setMode(mode: UiMode): void;
  __resetForTest(): void;
}

export const useUiModeStore = create<UiModeStore>((set) => ({
  mode: loadMode(),
  setMode(mode) {
    set({ mode });
    persistMode(mode);
  },
  __resetForTest() {
    if (typeof window !== "undefined") {
      try {
        window.localStorage.removeItem(STORAGE_KEY);
      } catch {
        /* ignore */
      }
    }
    set({ mode: DEFAULT_MODE });
  },
}));
```

### - [ ] Step 1.4: vitest PASS

```bash
pnpm vitest run src/components/sql-ide/store/__tests__/useUiModeStore.test.ts
```

**Expected:** 5/5 PASS.

### - [ ] Step 1.5: ModeSwitcher component

`src/components/sql-ide/ModeSwitcher.tsx`:

```tsx
import { Segmented } from "antd";
import { type FC } from "react";
import { useUiModeStore } from "./store/useUiModeStore";

export const ModeSwitcher: FC = () => {
  const mode = useUiModeStore((s) => s.mode);
  const setMode = useUiModeStore((s) => s.setMode);

  return (
    <Segmented
      size="small"
      value={mode}
      onChange={(v) => setMode(v as "simple" | "advanced")}
      options={[
        { label: "简洁", value: "simple" },
        { label: "高级", value: "advanced" },
      ]}
      aria-label="SQL IDE 显示模式"
    />
  );
};
```

### - [ ] Step 1.6: Mount ModeSwitcher in SqlIde.tsx + plumb mode down

Read `src/components/sql-ide/SqlIde.tsx`. At the top of the main column (just above `<TabBar />`), add a small toolbar row containing `<ModeSwitcher />` flush-right:

```tsx
import { ModeSwitcher } from "./ModeSwitcher";
import { useUiModeStore } from "./store/useUiModeStore";

// Inside SqlIde component:
const uiMode = useUiModeStore((s) => s.mode);

// At the top of the center column, before TabBar, add:
<div style={{
  display: "flex",
  justifyContent: "flex-end",
  alignItems: "center",
  padding: "4px 8px",
  borderBottom: "1px solid var(--ant-color-border-secondary)",
  gap: 8,
}}>
  <ModeSwitcher />
</div>
```

Pass `mode={uiMode}` into `<SqlEditor>` (already accepts `mode?: "simple" | "advanced"`). Do NOT alter SqlEditor's signature.

### - [ ] Step 1.7: ActivityBar simple-mode hides Search + Copilot

Modify `src/components/sql-ide/layout/ActivityBar.tsx`:

Read mode from store and filter activities:

```tsx
import { useUiModeStore } from "../store/useUiModeStore";

// inside component, after existing hooks:
const mode = useUiModeStore((s) => s.mode);

const visibleActivities = ACTIVITIES.filter((a) => {
  if (mode === "simple" && (a.id === "search" || a.id === "copilot")) return false;
  return true;
});

// replace `ACTIVITIES.map(...)` with `visibleActivities.map(...)`
```

### - [ ] Step 1.8: SchemaContextMenu simple-mode drops INSERT

Modify `src/components/sql-ide/schema/SchemaContextMenu.tsx`:

```tsx
import { useUiModeStore } from "../store/useUiModeStore";

// inside TableContextMenu component:
const mode = useUiModeStore((s) => s.mode);

const items: MenuProps["items"] = mode === "simple"
  ? [
      { key: "select", label: "Generate SELECT" },
      { key: "copy-name", label: "Copy Name" },
    ]
  : [
      { key: "select", label: "Generate SELECT" },
      { key: "insert", label: "Generate INSERT" },
      { key: "copy-name", label: "Copy Name" },
    ];
```

The `onClick` switch stays the same — simple mode just won't fire "insert" since item is absent.

### - [ ] Step 1.9: LogPanel reads mode from store (drop showAdvanced prop)

Modify `src/components/sql-ide/result/LogPanel.tsx`:

```tsx
import { useUiModeStore } from "../store/useUiModeStore";

// Drop the showAdvanced prop from LogPanelProps:
export interface LogPanelProps {
  executionId: string;
}

export const LogPanel: FC<LogPanelProps> = ({ executionId }) => {
  const mode = useUiModeStore((s) => s.mode);
  const showAdvanced = mode === "advanced";
  // ... rest of component logic unchanged; the internal advancedOpen state/toggle stays
```

Keep the internal `advancedOpen` local state + Switch — it starts from `showAdvanced` but allows per-panel toggle while mode is "advanced".

Update `useEffect` sync:
```tsx
useEffect(() => {
  setAdvancedOpen(showAdvanced);
}, [showAdvanced]);
```

### - [ ] Step 1.10: BottomTabs drops showAdvanced pass

Modify `src/components/sql-ide/result/BottomTabs.tsx`:

LogPanel doesn't take `showAdvanced` anymore — remove `showAdvanced={false}` from the `<LogPanel ...>` call:

```tsx
{ key: "log", label: "日志", children: <LogPanel executionId={executionId} /> },
```

### - [ ] Step 1.11: Verification

```bash
pnpm tsc --noEmit
pnpm vitest run src/components/sql-ide
```

**Expected:** tsc clean, 71 + 5 = **76 PASS**.

### - [ ] Step 1.12: Commit

```bash
git add source/dts-platform-webapp/src/components/sql-ide/store \
        source/dts-platform-webapp/src/components/sql-ide/ModeSwitcher.tsx \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx \
        source/dts-platform-webapp/src/components/sql-ide/layout/ActivityBar.tsx \
        source/dts-platform-webapp/src/components/sql-ide/schema/SchemaContextMenu.tsx \
        source/dts-platform-webapp/src/components/sql-ide/result/LogPanel.tsx \
        source/dts-platform-webapp/src/components/sql-ide/result/BottomTabs.tsx
git commit -m "$(cat <<'EOF'
feat(F6/T26): simple/advanced mode switcher + persist to localStorage

Sprint-11 F6 T26: useUiModeStore (TDD: 5 tests) holds mode state
and persists to localStorage key "sqlide.ui-mode.v1". ModeSwitcher
is a small AntD Segmented toggle mounted at the top of SqlIde.

Mode wiring:
- Simple: ActivityBar hides Search + Copilot icons; SchemaTree
  context menu drops "Generate INSERT"; LogPanel hides rewrittenSql
- Advanced: all 5 ActivityBar icons shown; full 3-item menu;
  rewrittenSql toggle available in Log tab

Mode reads happen inside the consuming components via useUiModeStore
selector — no prop drilling beyond SqlEditor (already had mode prop).
EOF
)"
```

---

## Task 2 — T27: Copilot 接入契约（接口 + 文档）

**Files:**
- Create: `src/components/sql-ide/copilot/types.ts`
- Create: `src/components/sql-ide/copilot/README.md`
- Modify: `src/components/sql-ide/copilot/CopilotSlot.tsx` (reference README + simple-mode hide via mode prop OR store read)

### - [ ] Step 2.1: Create Copilot contract types

`src/components/sql-ide/copilot/types.ts`:

```typescript
/**
 * Copilot contract — F6/T27.
 *
 * Providers implement this interface to plug AI into the SQL IDE.
 * Current in-tree implementations: none (placeholder only).
 * Future candidates: ClaudeMessagesProvider, ManagedAgentProvider.
 *
 * See copilot/README.md for the integration guide.
 */

export type CopilotMode =
  | "nl2sql"       // Natural-language question → SQL
  | "explain"     // Explain what this SQL does in plain language
  | "optimize"   // Suggest optimizations for this SQL
  | "fix";       // Given an error message + SQL, suggest a fix

export interface CopilotRequest {
  mode: CopilotMode;
  sql?: string;           // current SQL (for explain/optimize/fix)
  naturalQuery?: string;  // user question (for nl2sql)
  schemaContext?: string; // compact schema description: "schema.table(col1 type, col2 type, ...)"
  errorMessage?: string;  // for fix mode
  datasourceId: string | null;
  engine: string;
}

export interface CopilotResponse {
  sql?: string;            // generated/optimized SQL (for nl2sql/optimize/fix)
  explanation?: string;    // prose explanation (for explain/optimize/fix)
  suggestions?: string[];  // additional hints
  confidence: number;      // 0..1
  modelVersion: string;    // e.g. "claude-sonnet-4-6"
}

export interface CopilotProvider {
  readonly name: string;
  readonly supportedModes: CopilotMode[];

  chat(req: CopilotRequest): Promise<CopilotResponse>;

  /** Optional streaming for providers that support it. */
  stream?(req: CopilotRequest): AsyncIterable<Partial<CopilotResponse>>;
}

/**
 * The CopilotProvider registry for dependency injection.
 * When AI lands, a concrete provider instance registers here and
 * CopilotSlot reads from it.
 */
export interface CopilotProviderRegistry {
  register(provider: CopilotProvider): void;
  unregister(name: string): void;
  default(): CopilotProvider | null;
}
```

### - [ ] Step 2.2: Write README

`src/components/sql-ide/copilot/README.md`:

```markdown
# SQL IDE Copilot 接入指南

本目录是 SQL IDE 的 AI Copilot 插槽（placeholder）。本 Sprint (F6/T27) 只落地契约，不实现。

## 契约

实现 `CopilotProvider` 接口（见 `types.ts`）即可接入：

- `chat(req: CopilotRequest): Promise<CopilotResponse>` —— 单次请求
- `stream(req: CopilotRequest)` —— 可选，流式返回

### 四种模式

| mode | 输入 | 输出 |
|---|---|---|
| `nl2sql` | `naturalQuery`（用户自然语言）+ `schemaContext` | `sql` + `confidence` |
| `explain` | `sql` | `explanation` 散文 |
| `optimize` | `sql` + `schemaContext` | `sql` 优化版 + `explanation` + `suggestions[]` |
| `fix` | `sql` + `errorMessage` | `sql` 修正版 + `explanation` |

## 实现 Provider

### 选项 A：Claude Messages API

```typescript
import type { CopilotProvider, CopilotRequest, CopilotResponse } from "./types";

export class ClaudeMessagesProvider implements CopilotProvider {
  readonly name = "claude-messages";
  readonly supportedModes = ["nl2sql", "explain", "optimize", "fix"] as const;

  async chat(req: CopilotRequest): Promise<CopilotResponse> {
    const systemPrompt = buildSystemPrompt(req.mode, req.schemaContext);
    const userMessage = buildUserMessage(req);
    const resp = await fetch("/api/copilot/messages", {
      method: "POST",
      body: JSON.stringify({ system: systemPrompt, messages: [userMessage] }),
    });
    const json = await resp.json();
    return parseResponse(json);
  }
}
```

后端需要新增 `/api/copilot/*` 端点代理 Anthropic Messages API，并注入项目的 audit + rate-limit + security-rewriter。

### 选项 B：Anthropic Managed Agents

使用 `@anthropic-ai/sdk` 的 `client.agents.create/sessions.create/events.send`。Managed Agents 内置工具（bash、file、search），适合多轮工作流。

### 前端挂载

`CopilotSlot.tsx` 读取默认 provider。引入时：

1. 定义 `CopilotProviderRegistry` 单例（可用 Zustand 或普通模块级 Map）
2. 应用启动时 `registry.register(new ClaudeMessagesProvider(...))`
3. CopilotSlot 内部 `registry.default()?.chat(...)`

## 安全与合规

- 所有 Copilot 调用必须走后端代理（不直接从浏览器调 Anthropic API，避免 key 泄露）
- 生成的 SQL 在执行前仍需要过 `SecuritySqlRewriter`（不能绕过密级/部门过滤）
- Copilot 调用和结果应该走 `SqlIdeAuditActions` 审计（待新增常量）

## 延后事项

- 纯占位 UI（见 `CopilotSlot.tsx`）
- 简洁模式下 Copilot icon 隐藏（F6/T26 已经做）
- 真正的 provider 实现放到未来 Sprint
```

### - [ ] Step 2.3: Update CopilotSlot to reference README + respect mode

Modify `src/components/sql-ide/copilot/CopilotSlot.tsx`:

- Add small link/text pointing to the README path
- Component still renders "敬请期待" placeholder
- Simple mode hides Copilot via ActivityBar filter already (F6/T26); CopilotSlot doesn't need its own hide logic

Keep the existing 3-feature bullet list, add a small note:

```tsx
<div style={{ fontSize: 10, color: "var(--ant-color-text-quaternary)", marginTop: 16 }}>
  接入指南：<code>src/components/sql-ide/copilot/README.md</code>
</div>
```

### - [ ] Step 2.4: Verification

```bash
pnpm tsc --noEmit
pnpm vitest run src/components/sql-ide
```

**Expected:** tsc clean, 76/76 still PASS (no new tests).

### - [ ] Step 2.5: Commit

```bash
git add source/dts-platform-webapp/src/components/sql-ide/copilot
git commit -m "$(cat <<'EOF'
feat(F6/T27): Copilot contract types + integration README

Sprint-11 F6 T27: defines CopilotRequest/CopilotResponse/
CopilotProvider/CopilotProviderRegistry interfaces. No runtime
implementation — future NL2SQL/explain/optimize/fix providers
implement CopilotProvider and register via the registry.

README covers two likely implementation paths (Claude Messages
API vs Managed Agents), example code, security considerations,
and mounting instructions. CopilotSlot keeps its placeholder but
now points to the README for contributors.
EOF
)"
```

---

## Task 3 — T28: Sprint 收尾文档（IT + 用户手册）

No code — documentation only. Create 8 files.

### - [ ] Step 3.1: E2E report (placeholder with plan)

Create `worklog/v2.2.3/sprint-11-202604/it/e2e-report.md`:

```markdown
# Sprint-11 E2E Test Report

**Status**: Planned (implementation pending)
**Sprint**: v2.2.3 Sprint-11 — SQL IDE

## Scope

17+ user-journey tests covering:

- [ ] Open SqlIdePage (flag enabled), verify blank state renders
- [ ] Monaco loads, syntax highlighting on `SELECT * FROM foo`
- [ ] Ctrl+Enter executes; result appears in Results tab
- [ ] Multi-tab: open 3 tabs, switch between them, state persists
- [ ] Cross-device recovery: open Tab A on device 1, sign in on device 2, Tab A appears
- [ ] Schema tree: expand datasource → schema → tables; double-click inserts `schema.table` at cursor
- [ ] Right-click table → Generate SELECT → SQL inserted
- [ ] Run query → switch to Chart tab → auto-recommend shows relevant chart
- [ ] Run query → Pivot tab → drag fields → aggregated result
- [ ] Run query → Plan tab → click EXPLAIN → tree renders
- [ ] Run query → Log tab → SQL + metadata visible
- [ ] Export CSV → browser downloads file
- [ ] Export Excel → .xlsx opens in Excel
- [ ] Simple/Advanced toggle → ActivityBar icon count changes
- [ ] Rate limit: export 6 times → 6th rejected (429)
- [ ] Long SQL text (1MB) in tab persists + recovers
- [ ] Feature flag OFF → `/explore/workbench` routes to old QueryWorkbenchPage

## Execution

Not yet run. Playwright setup deferred to dedicated test-infra task.
```

### - [ ] Step 3.2: Perf report skeleton

Create `worklog/v2.2.3/sprint-11-202604/it/perf-report.md`:

```markdown
# Sprint-11 Performance Report

**Status**: Baseline targets defined; measurements pending.

## Targets

| Metric | Target | Measurement Method |
|---|---|---|
| SqlIdePage 首屏渲染 | ≤ 800ms | Lighthouse LCP |
| Tab 切换延迟 | ≤ 100ms | Performance API, marks around setActive |
| Schema 树单节点展开 | ≤ 300ms（缓存命中 ≤ 50ms） | React DevTools profiler |
| 10k 行 Grid 滚动 | 60fps | Chrome DevTools Performance |
| 100k 行 CSV 导出 | ≤ 30s | 服务端日志 |
| 后端 /page P99 | ≤ 200ms | JMH / k6 |

## Measurements

待实施后补充。
```

### - [ ] Step 3.3: Regression matrix

Create `worklog/v2.2.3/sprint-11-202604/it/regression-matrix.md`:

```markdown
# Sprint-11 Regression Matrix

Old `SqlWorkbenchExperimental` → new SQL IDE mapping. Every feature must be verified in both legacy (flag off) and new (flag on) paths.

| 老功能 | 新版位置 | 验证方式 | 状态 |
|---|---|---|---|
| 写 SQL | F1 SqlEditor (Monaco) | E2E | 待测 |
| 数据源选择 | F3 Schema Select 下拉 | E2E | 待测 |
| Schema 树展开 | F3 SchemaTree | E2E | 待测 |
| 搜索表 | F3 SchemaTree 搜索框 | E2E | 待测 |
| 保存查询列表 | F3 SavedPanel | E2E | 待测 |
| 运行 SQL | F1 Ctrl+Enter / 工具栏 Run | E2E | 待测 |
| 取消执行 | F4 useSqlExecution.cancel | E2E | 待测 |
| 格式化 | F1 Ctrl+Alt+F | E2E | 待测 |
| 保存为查询 | F3 Ctrl+S SaveQueryDialog | E2E | 待测 |
| 沉淀为数据集 | 复用 QueryDatasetManager | E2E | 待测 |
| 行数限制 | F4 100k 上限硬编码 | — | 实现即满足 |
| 结果表格（基础） | F4 ResultGrid | E2E | 待测 |
| 结果分页 | F4 ResultGrid 底部分页 | E2E | 待测 |
| 复制结果 | F4 "复制全部 (TSV)" 按钮 | E2E | 待测 |
| 耗时/行数统计 | F5 LogPanel + BottomPanel 头 | E2E | 待测 |
| 日志 Tab | F5 LogPanel | E2E | 待测 |
| 历史 Tab | F3 HistoryPanel | E2E | 待测 |
| 导出（CSV/Excel/JSON） | F4 ExportMenu + 流式导出 | E2E | 待测 |
| 多 Tab | F2 TabBar + 持久化 | E2E | 待测 |
| Chart 可视化 | F5 ResultChart | E2E | 待测 |
| Pivot 透视 | F5 ResultPivot | E2E | 待测 |
| EXPLAIN 查询计划 | F5 QueryPlanView | E2E | 待测 |
| 二次查询 | F5 SubQueryButton | E2E | 待测 |

## 未覆盖（接受清单）

- 鼠标拖拽调整列宽 — `columnState` reducer 就绪但 UI handle 待 T13 followup 接入
- 图表保存到看板 — 本 Sprint 占位，未实现
```

### - [ ] Step 3.4: Audit coverage

Create `worklog/v2.2.3/sprint-11-202604/it/audit-coverage.md`:

```markdown
# Sprint-11 审计覆盖清单

13 类审计动作（`SqlIdeAuditActions`）在代码中的落点。

| # | 常量 | 值 | 触发点 | 载荷字段 | 状态 |
|---|---|---|---|---|---|
| 1 | SQL_EXECUTE_SUBMIT | `sql.ide.execute.submit` | `SqlExecutionService.executeQueued`（post-rewrite） | engine, sqlHash, rewrittenHash | ✅ 已落 |
| 2 | SQL_EXECUTE_CANCEL | `sql.ide.execute.cancel` | `SqlExecutionService.cancel` | reason | ✅ |
| 3 | SQL_EXECUTE_COMPLETE | `sql.ide.execute.complete` | `SqlExecutionService` 终态分支 | status, rows, elapsedMs | ✅ |
| 4 | SQL_RESULT_VIEW | `sql.ide.result.view` | `SqlIdeExecutionController.page` | page, size | 待迁移到常量 |
| 5 | SQL_RESULT_EXPORT | `sql.ide.result.export` | `SqlIdeExecutionController.export` | format | 待迁移到常量 |
| 6 | SQL_RESULT_COPY | `sql.ide.result.copy` | `SqlIdeAuditController.copy` | cells | ✅ |
| 7 | SQL_TEMP_VIEW_CREATE | `sql.ide.temp_view.create` | `SqlIdeSubqueryController.create` | viewName, rowCount | ✅ |
| 8 | SQL_TEMP_VIEW_DROP | `sql.ide.temp_view.drop` | `SqlIdeSubqueryController.delete` | viewName | ✅ |
| 9 | SQL_SUBQUERY_EXECUTE | `sql.ide.subquery.execute` | `SqlIdeSubqueryController.query` | viewName, sqlHash, error? | ✅ |
| 10 | SQL_PLAN_VIEW | `sql.ide.plan.view` | `SqlIdePlanController.explain` | engine, sqlHash, errorSnippet? | ✅ |
| 11 | SQL_IDE_TAB_SAVE | `sql.ide.tab.save` | `SqlIdeTabResource.patch/batch` | tabId, sqlTextHash | 待迁移到常量 |
| 12 | SAVED_QUERY_LOAD | `sql.workbench.saved-query.load` | 旧路径沿用 | queryId | ✅ 兼容 |
| 13 | SQL_CATALOG_BROWSE | `sql.ide.catalog.browse` | `SqlIdeResource.catalog*` (F3 多端点) | dsId, schema, table | 待迁移到常量 |

## Followup

4 个动作点当前仍使用直接字符串字面量（#4/#5/#11/#13），应迁移到 `SqlIdeAuditActions` 常量使用保证一致性。
```

### - [ ] Step 3.5: Grayscale runbook

Create `worklog/v2.2.3/sprint-11-202604/it/grayscale-runbook.md`:

```markdown
# Sprint-11 灰度上线 SOP

## Feature Flag

- 环境变量：`WEBAPP_ENABLE_SQL_IDE_V2`（webapp 容器）和 `DTS_SQL_IDE_V2_ENABLED`（平台容器，保留未来 gate 用）
- 默认：`false`
- 影响：`/explore/workbench` 路由在启用时指向 `SqlIdePage`，关闭时回到 `QueryWorkbenchPage`

## 阶段

### 阶段 0 — 内部（<10 人，第 1-7 天）
1. 在 dev/stage 环境 `WEBAPP_ENABLE_SQL_IDE_V2=true`
2. 研发 + SA 账户试用全部 6 个 Feature
3. 观察 audit log 无异常、前端 console 无报错
4. 运维 dashboard：
   - Hazelcast `sqlIdeSchemas/Tables/Columns` 缓存命中率 > 50%
   - `query_execution_chunk` 表行数 / 占用增长正常
   - `sqlide_view_*` UNLOGGED 表 30min TTL 清理生效
5. 若 3 天无 P0/P1：进入阶段 1

### 阶段 1 — 部门（~50 人，第 8-21 天）
1. 挑选 1-2 个活跃数据分析团队，灰度账户开启
2. 每日站会收集反馈
3. 观察审计日志体量（export / page 增长 ≤ 预估 10x）
4. 性能指标达标（见 perf-report.md）
5. 连续 5 天无 P0/P1 缺陷进入阶段 2

### 阶段 2 — 全量（第 22 天起）
1. 全部环境 `WEBAPP_ENABLE_SQL_IDE_V2=true`
2. 老 `QueryWorkbenchPage` 保留至少 3 个版本（v2.2.3 / v2.3.0 / v2.4.0）作为兜底

## 观测指标

- 前端错误率（埋点 /分钟 / 用户）
- 后端 `/api/sql/v2/*` P99 延迟
- audit_log 行增速
- `query_execution_chunk` 表大小
- Feature flag 状态分布

## 升级路径

从阶段 0 → 1：修改 webapp 容器的环境变量，重建前端 bundle / hot-reload runtime-config.js 配置即可（见 vite.config.ts + docker-entrypoint.sh）。
```

### - [ ] Step 3.6: Rollback runbook

Create `worklog/v2.2.3/sprint-11-202604/it/rollback-runbook.md`:

```markdown
# Sprint-11 回滚 SOP

## 触发条件

- P0 bug：无法执行 SQL / 用户数据丢失 / 安全漏洞
- P1 bug：核心路径阻断（> 10% 失败率）
- 审计日志丢失 > 1%
- 后端 5xx > 1%
- 数据库连接池打爆

## 快速回滚（预期 ≤ 5 分钟）

1. 运维把 webapp 容器环境变量 `WEBAPP_ENABLE_SQL_IDE_V2=false`
2. 重启 webapp 容器（或 reload runtime-config.js）
3. 前端下次刷新路由到 `QueryWorkbenchPage`
4. 观察 30 分钟确认恢复

## 现场保留

- **不要**删 `sql_ide_tab` 表（用户 Tab 数据）
- **不要**删 `query_execution_chunk`（结果缓存）
- **不要**drop `sqlide_view_*` UNLOGGED 表（二次查询临时视图）
- 保留 app log + audit log 2 周

## 根因分析

- 分析现场日志
- 打 bug ticket → 跟踪到 Sprint-11 某 Task 的 followup
- 修复后重走阶段 0 → 1 → 2

## 后端数据层回滚（如果需要）

所有新增表 + 列可用 Liquibase rollback 移除：
- `sql_ide_tab`（F2/T07）
- `saved_query.folder`（F3/T15）
- `query_execution_chunk`（F4/T16）
- `rows_json` jsonb 迁移（F4/T16 fix）

**仅在业务方同意丢失用户 Tab 状态 + 保存查询的 folder 标签 + 查询结果缓存时执行。**
```

### - [ ] Step 3.7: User guide + architecture doc

Create `worklog/v2.2.3/sprint-11-202604/assets/user-guide.md`:

```markdown
# SQL IDE 用户手册

## 快速入门

1. 左侧 Activity Bar 选择 `🗂 Schema`，选数据源，展开数据库
2. 中间编辑区输入 SQL，Ctrl+Enter 运行
3. 底部 5 tab 查看 Results / Chart / Pivot / Query Plan / Log
4. Ctrl+S 保存为查询
5. 查询历史在 `📋 History` icon

## 快捷键

| 快捷键 | 动作 |
|---|---|
| Ctrl+Enter | 执行当前 SQL |
| Ctrl+Shift+Enter | 执行并在新 Tab 显示结果 |
| Ctrl+Alt+F | 格式化 |
| Ctrl+/ | 行注释 |
| Ctrl+D | 多选下一个同词 |
| Alt+↑/↓ | 行上移/下移 |
| Ctrl+S | 保存为查询 |
| F8 | 跳到下一个错误 |
| Ctrl+\` | 切换底部面板 |

## 简洁 vs 高级模式

右上角切换。

| 能力 | 简洁 | 高级 |
|---|---|---|
| Monaco minimap | ✗ | ✓ |
| ActivityBar icons | Schema/History/Saved | + Search + Copilot |
| 右键菜单 | SELECT / Copy | + INSERT |
| Log 重写后 SQL | ✗ | ✓ toggle |

## 多 Tab

- 顶部 `+` 新建，上限 30
- 双击标题重命名
- Tab 状态跨设备同步（登录后自动恢复）
- Dirty 标记 `●` 表示尚未同步

## 导出

结果区右上角 `导出 ▾`：CSV / Excel / JSON。频控 5 次/10 分钟。

## 二次查询

结果区 "Query This Result" 按钮创建临时视图，新开 Tab 继续对结果写 SQL。30 分钟 TTL。
```

Create `worklog/v2.2.3/sprint-11-202604/assets/architecture.md`:

```markdown
# SQL IDE 架构

## 组件树

```
SqlIde
├─ ModeSwitcher (F6 右上角)
├─ ActivityBar (F3/T12, 44px icon 栏)
├─ SidePanel (F3/T12, 可拖拽)
│  └─ { Schema | History | Saved | Search | Copilot }
├─ 中央列
│  ├─ TabBar (F2/T10)
│  ├─ SqlEditor (F1: Monaco + completion + shortcuts + formatter)
│  └─ BottomPanel
│     ├─ Header: status + cancel + ExportMenu + SubQueryButton
│     └─ BottomTabs (F5/T25)
│        └─ { Results | Chart | Pivot | Query Plan | Log }
└─ SaveQueryDialog (F3/T15, Ctrl+S 弹)
```

## Store

- `useTabStore` (F2) — Tab 状态 + 持久化
- `useLayoutStore` (F3) — ActivityBar + SidePanel 尺寸
- `useUiModeStore` (F6) — 简洁/高级模式

## 后端

- `/api/sql/v2/*` 全新
- `/api/sql/*` legacy 保留
- 审计 → `SqlIdeAuditActions` + `auditService.record()`
- 结果分块 → `query_execution_chunk` JSONB
- 二次查询 → PG UNLOGGED + ownership check + read-only

## Feature Flag

`WEBAPP_ENABLE_SQL_IDE_V2` (webapp) — 默认 false；前端路由条件分派。
```

### - [ ] Step 3.8: Update sprint README + IT README

Modify `worklog/v2.2.3/sprint-11-202604/README.md`:
- Update all F1–F6 statuses to DONE in the Feature table
- Update 统计 to READY=0, DONE=28 (or actual breakdown)

Modify `worklog/v2.2.3/sprint-11-202604/it/README.md`:
- Cross-link to the 4 new files (e2e/perf/regression/audit/grayscale/rollback)

Modify `worklog/v2.2.3/sprint-queue.md`:
- Update Sprint-11 statistics row

### - [ ] Step 3.9: Commit

```bash
git add worklog/v2.2.3/sprint-11-202604/it/ \
        worklog/v2.2.3/sprint-11-202604/assets/ \
        worklog/v2.2.3/sprint-11-202604/README.md \
        worklog/v2.2.3/sprint-queue.md
git commit -m "$(cat <<'EOF'
docs(F6/T28): Sprint-11 closeout — regression matrix, audit coverage, SOPs

Sprint-11 F6 T28: 6 documents landed under worklog/v2.2.3/sprint-11-202604:
- it/e2e-report.md — 17+ planned E2E cases
- it/perf-report.md — 6 perf targets + measurement methods
- it/regression-matrix.md — 23 legacy features mapped to new components
- it/audit-coverage.md — 13 audit actions, callsite map
- it/grayscale-runbook.md — 3-stage rollout with observability gates
- it/rollback-runbook.md — 5-min flag flip + DB preservation rules
- assets/user-guide.md — shortcut chart + simple/advanced matrix
- assets/architecture.md — component tree + stores + feature flag

Sprint tracker + feature READMEs updated to DONE state across F1–F6.
EOF
)"
```

---

## Final Verification

```bash
cd /opt/prod/s10/s10-stack
cd source/dts-platform-webapp
pnpm tsc --noEmit && pnpm vitest run src/components/sql-ide
cd ../dts-platform
./mvnw test -Dtest='SqlIde*' 2>&1 | grep "Tests run:" | tail
```

**Expected:**
- Frontend **76 PASS** (71 prior + 5 useUiModeStore TDD)
- Backend **26 PASS** (no new ITs in F6)

---

## Notes for the Executing Engineer

1. **No backend changes** in F6 — all pure frontend + docs
2. **Follow the same subagent model**: sonnet implementer + sonnet spec reviewer + opus code quality reviewer
3. **T28 is documentation only** — execute inline or via a single implementer call; no review required since there's no code
4. **F6 F-follow-ups** (track for future):
   - Integrate the 4 literal-string audit callsites into `SqlIdeAuditActions` constants
   - Wire real Copilot provider (beyond interface + README)
   - Run the planned E2E suite + fill in perf measurements
   - Execute 3-stage grayscale per SOP

5. **Self-review before commit**: ensure LogPanel's `showAdvanced` prop is removed (not just unused) and BottomTabs no longer passes it — otherwise Typescript won't complain but it's dead code.
