# Sprint-9: GPMC 大屏跳转修复 + 甘特图弹层下钻 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 GPMC 大屏点击事件"无目标却跳首页"的体验缺陷；把执行层甘特图升级为"父项目汇总 + 弹层下钻子项目精致甘特"。

**Architecture:** 见 `worklog/v2.2.3/sprint-9-202604/README.md`。核心：（1）`InteractionLayer.tsx` 引入空 sentinel 取消跳转 + `useResolvableJumpStatus` hook 视觉禁用；（2）`PropertyPanel.tsx` 修 ScreenJumpPicker UI bug；（3）批量清理 5 个 v2 大屏 JSON 死链；（4）新建 `ProjectDetailGanttModal.tsx` 弹层组件 + `majorProjectAggregator.ts` SQL 行 → 嵌套结构 transformer；（5）`gpmc-execution-board-v2.json` 把硬编码 tasks 替换为 SQL 数据源。

**Tech Stack:** React 18 / TypeScript / Tailwind / ECharts / PostgreSQL / dbt

**Spec:** `worklog/v2.2.3/sprint-9-202604/README.md`

**Chrome 兼容下限:** Chrome 95（禁用 `:has()`、`structuredClone`、`findLast`、`text-wrap: balance`、container queries）

---

## File Structure

```
# 新增
source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/
  ProjectDetailGanttModal.tsx
  ProjectDetailGanttModal.css   (或集成到上文 .tsx 内 inline-style)

source/dts-platform-webapp/src/analytics/pages/project-cockpit/utils/
  majorProjectAggregator.ts
  majorProjectAggregator.test.ts

# 修改 (TypeScript)
source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx
source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx
source/dts-platform-webapp/src/analytics/pages/screens/renderers/EChartsRenderer.tsx
source/dts-platform-webapp/src/analytics/pages/screens/renderers/TableRenderer.tsx
source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/ProjectGanttBoard.tsx
source/dts-platform-webapp/src/analytics/pages/screens/types.ts

# 修改 (JSON)
worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-strategic-overview-v2.json
worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-execution-board-v2.json
worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-quality-board-v2.json
worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-risk-board-v2.json
worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-tech-state-board-v2.json

# 修改 (索引)
worklog/v2.2.3/sprint-queue.md
```

---

## F1: 跳转引擎"无目标=不跳"修复（运行时）

### Task F1-T01: parseScreenReferenceUrl 移除默认 fallback

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx`

- [ ] **Step 1: 修改 parseScreenReferenceUrl 函数签名**

定位 `InteractionLayer.tsx:28-40`。返回类型从 `{ screenName: string; fallbackUrl: string } | null` 改为 `{ screenName: string; fallbackUrl: string | null } | null`。

```ts
function parseScreenReferenceUrl(targetUrl: string): { screenName: string; fallbackUrl: string | null } | null {
    if (!targetUrl.startsWith(SCREEN_REF_PREFIX)) {
        return null;
    }
    const raw = targetUrl.slice(SCREEN_REF_PREFIX.length);
    const [screenNamePart, fallbackPart = ''] = raw.split('|', 2);
    const screenName = decodeURIComponent(screenNamePart || '').trim();
    const fallbackRaw = decodeURIComponent(fallbackPart || '').trim();
    const fallbackUrl = fallbackRaw || null;  // ★ 不再默认 '/bi/screens'
    if (!screenName) {
        return { screenName: '', fallbackUrl };
    }
    return { screenName, fallbackUrl };
}
```

- [ ] **Step 2: 修改 resolveScreenReferenceUrl 失败分支**

定位 `InteractionLayer.tsx:42-67`。把 `return parsed.fallbackUrl;` 改为 `return parsed.fallbackUrl ?? '';`：

```ts
export async function resolveScreenReferenceUrl(targetUrl: string): Promise<string> {
    const parsed = parseScreenReferenceUrl(targetUrl);
    if (!parsed) {
        return targetUrl;
    }
    if (!parsed.screenName) {
        return parsed.fallbackUrl ?? '';  // ★
    }
    try {
        // ... 现有 listScreens + 匹配逻辑保持不变
        if (exact?.id != null) {
            return resolveRouteForOpen(`/bi/screens/${encodeURIComponent(String(exact.id))}/preview`);
        }
    } catch (error) {
        console.error('Failed to resolve screen reference jump target:', error);
    }
    return parsed.fallbackUrl ?? '';  // ★ 找不到目标且无显式 fallback → 空字符串
}
```

- [ ] **Step 3: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```
Expected: 无新增 error。

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx
git commit -m "feat(S9/F1-T01): parseScreenReferenceUrl 移除默认 fallback,引入空 sentinel"
```

---

### Task F1-T02: navigateToResolvedUrl 检测空 sentinel 取消跳转

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx`

- [ ] **Step 1: 修改 navigateToResolvedUrl callback**

定位 `InteractionLayer.tsx:144-166`。在调用 `normalizeRuntimeJumpUrl` 之前增加 sentinel 检测：

```ts
const navigateToResolvedUrl = useCallback(async (targetUrl: string, openMode: 'self' | 'new-tab', source: string) => {
    const resolved = await resolveScreenReferenceUrl(targetUrl);
    if (!resolved) {
        // ★ 取消跳转,记录事件
        runtime.trackEvent({
            kind: 'jump',
            key: 'jumpUrl',
            value: '',
            source,
            meta: `cancelled;raw=${targetUrl}`,
        });
        return;
    }
    const resolvedTargetUrl = normalizeRuntimeJumpUrl(
        resolved,
        {
            currentOrigin: window.location.origin,
            resolveAppRoute: resolveRouteForOpen,
        },
    );
    runtime.trackEvent({
        kind: 'jump',
        key: 'jumpUrl',
        value: resolvedTargetUrl,
        source,
        meta: `openMode=${openMode};raw=${targetUrl}`,
    });
    if (openMode === 'self') {
        if (resolvedTargetUrl.startsWith('/') || (() => { try { return new URL(resolvedTargetUrl).origin === window.location.origin; } catch { return false; } })()) {
            window.location.assign(resolvedTargetUrl);
        }
        return;
    }
    window.open(resolvedTargetUrl, '_blank', 'noopener,noreferrer');
}, [runtime]);
```

- [ ] **Step 2: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```
Expected: 无新增 error。

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx
git commit -m "feat(S9/F1-T02): navigateToResolvedUrl 检测空 sentinel 取消跳转"
```

---

### Task F1-T03: 单测 — InteractionLayer sentinel 行为

**Files:**
- Add: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.cancelOnEmpty.test.tsx`

- [ ] **Step 1: 写单测**

```ts
import { resolveScreenReferenceUrl } from './InteractionLayer';
import { analyticsApi } from '../../../api/analyticsApi';

vi.mock('../../../api/analyticsApi', () => ({
    analyticsApi: {
        listScreens: vi.fn(),
    },
}));

describe('resolveScreenReferenceUrl - sentinel behavior', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('returns empty string when screen not found and no fallback', async () => {
        (analyticsApi.listScreens as any).mockResolvedValue([]);
        const result = await resolveScreenReferenceUrl('screen-ref:NonExistent|');
        expect(result).toBe('');
    });

    it('returns explicit fallback when screen not found but fallback set', async () => {
        (analyticsApi.listScreens as any).mockResolvedValue([]);
        const result = await resolveScreenReferenceUrl('screen-ref:NonExistent|/explicit/fallback');
        expect(result).toBe('/explicit/fallback');
    });

    it('returns resolved URL when screen found', async () => {
        (analyticsApi.listScreens as any).mockResolvedValue([
            { id: 42, name: 'Target', updatedAt: '2026-01-01' },
        ]);
        const result = await resolveScreenReferenceUrl('screen-ref:Target|');
        expect(result).toContain('/42/preview');
    });

    it('returns empty string when listScreens fails and no fallback', async () => {
        (analyticsApi.listScreens as any).mockRejectedValue(new Error('network'));
        const result = await resolveScreenReferenceUrl('screen-ref:Target|');
        expect(result).toBe('');
    });

    it('returns input as-is for non screen-ref URL', async () => {
        const result = await resolveScreenReferenceUrl('https://example.com/path');
        expect(result).toBe('https://example.com/path');
    });
});
```

- [ ] **Step 2: 运行单测**

```bash
cd source/dts-platform-webapp && pnpm vitest run InteractionLayer.cancelOnEmpty 2>&1 | tail -20
```
Expected: 5 passed。

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.cancelOnEmpty.test.tsx
git commit -m "test(S9/F1-T03): InteractionLayer sentinel 行为单测"
```

---

### Task F1-T04: useResolvableJumpStatus hook 实现

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx`

- [ ] **Step 1: 在 InteractionLayer.tsx 末尾新增 useResolvableJumpStatus**

```ts
// 放在 useComponentInteractions 之后,文件末尾之前
export function useResolvableJumpStatus(component: ScreenComponent, mode: string): {
    hasResolvableJump: boolean;
    isResolving: boolean;
} {
    const [status, setStatus] = useState<{ hasResolvableJump: boolean; isResolving: boolean }>({
        hasResolvableJump: true,
        isResolving: true,
    });
    useEffect(() => {
        if (mode !== 'preview') {
            setStatus({ hasResolvableJump: true, isResolving: false });
            return;
        }
        const candidates: string[] = [];
        for (const a of component.actions ?? []) {
            if (a?.type === 'jump-url' && String(a.jumpUrlTemplate || '').trim()) {
                candidates.push(String(a.jumpUrlTemplate));
            }
        }
        if (component.interaction?.enabled === true
            && component.interaction?.jumpEnabled === true
            && String(component.interaction?.jumpUrlTemplate || '').trim()) {
            candidates.push(String(component.interaction.jumpUrlTemplate));
        }
        if (candidates.length === 0) {
            setStatus({ hasResolvableJump: false, isResolving: false });
            return;
        }
        let cancelled = false;
        (async () => {
            for (const tmpl of candidates) {
                const resolved = await resolveScreenReferenceUrl(tmpl).catch(() => '');
                if (cancelled) return;
                if (resolved) {
                    setStatus({ hasResolvableJump: true, isResolving: false });
                    return;
                }
            }
            setStatus({ hasResolvableJump: false, isResolving: false });
        })();
        return () => { cancelled = true; };
    }, [component.actions, component.interaction, mode]);
    return status;
}

export function hasNonJumpInteractivity(component: ScreenComponent): boolean {
    const actions = component.actions ?? [];
    const hasOtherAction = actions.some((a) => {
        const t = a?.type;
        return t === 'drill-down' || t === 'drill-up' || t === 'open-panel' || t === 'set-variable' || t === 'emit-intent';
    });
    if (hasOtherAction) return true;
    const interaction = component.interaction;
    if (interaction?.enabled === true) {
        if (Array.isArray(interaction.mappings) && interaction.mappings.length > 0) return true;
    }
    return false;
}
```

- [ ] **Step 2: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx
git commit -m "feat(S9/F1-T04): 新增 useResolvableJumpStatus + hasNonJumpInteractivity"
```

---

### Task F1-T05: EChartsRenderer / TableRenderer 接入视觉禁用

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/EChartsRenderer.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/TableRenderer.tsx`

- [ ] **Step 1: EChartsRenderer 引入 hook**

在文件顶部 import：
```ts
import { useResolvableJumpStatus, hasNonJumpInteractivity } from './InteractionLayer';
```

在主渲染函数内（与现有 `useComponentInteractions` 同级）调用：
```ts
const { hasResolvableJump, isResolving } = useResolvableJumpStatus(component, mode);
const isFullyDisabled = !isResolving && !hasResolvableJump && !hasNonJumpInteractivity(component);
```

把 `echartsClickHandler` 的赋值改为：
```ts
const effectiveClickHandler = isFullyDisabled ? undefined : echartsClickHandler;
// 后续 renderEChartWithHandles(...) 调用都用 effectiveClickHandler
```

容器 div 加 `style={{ cursor: isFullyDisabled ? 'default' : undefined }}`。

- [ ] **Step 2: TableRenderer 引入 hook**

同样 import + 调用 `useResolvableJumpStatus`。在表格行渲染处，用 `isFullyDisabled` 决定是否绑定 onClick 与 cursor 类。

- [ ] **Step 3: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/renderers/EChartsRenderer.tsx
git add source/dts-platform-webapp/src/analytics/pages/screens/renderers/TableRenderer.tsx
git commit -m "feat(S9/F1-T05): EChartsRenderer/TableRenderer 接入 useResolvableJumpStatus 视觉禁用"
```

---

## F2: 编辑器 ScreenJumpPicker UI 修复

### Task F2-T01: 下拉面板暗色主题适配

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx`

- [ ] **Step 1: 替换下拉面板 inline style 为 Tailwind 类**

定位 `PropertyPanel.tsx:885-942`。把 `<div style={{ position: 'absolute', ... background: 'var(--color-surface-card, #fff)', ... }}>` 改为：

```tsx
<div className="absolute top-full left-0 right-0 z-[999] max-h-60 overflow-y-auto
                border border-border-default rounded-md bg-surface-card
                shadow-lg mt-1">
```

- [ ] **Step 2: 替换搜索框分隔与提示色**

把 `<div style={{ padding: '6px 8px', borderBottom: '1px solid rgba(148,163,184,0.18)' }}>` 改为 `<div className="px-2 py-1.5 border-b border-border-default">`。

把 `<div style={{ padding: '12px 14px', fontSize: 12, color: '#94a3b8' }}>` 改为 `<div className="px-3.5 py-3 text-xs text-text-tertiary">`。

- [ ] **Step 3: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx
git commit -m "fix(S9/F2-T01): ScreenJumpPicker 下拉面板暗色主题适配"
```

---

### Task F2-T02: 列表项 name flex 塌陷修复

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx`

- [ ] **Step 1: 替换列表项渲染**

定位 `PropertyPanel.tsx:912-939`。把 inline style + filter map 改为：

```tsx
filtered.map(s => {
    const isSelected = selectedName === String(s.name || '').trim();
    const isPublished = s.publishedVersionNo != null && s.publishedVersionNo > 0;
    return (
        <div
            key={String(s.id)}
            onClick={() => { onChange(buildScreenRefUrl(s)); setOpen(false); setSearch(''); }}
            className={`flex items-center justify-between gap-2 px-3.5 py-2 cursor-pointer
                        text-xs hover:bg-brand/[0.06] ${isSelected ? 'bg-brand/[0.08]' : ''}`}
        >
            <span className={`flex-1 min-w-0 truncate ${isSelected ? 'font-semibold' : ''}`}>
                {isSelected ? '✓ ' : ''}{s.name || `大屏 #${s.id}`}
            </span>
            <span className={`flex-shrink-0 text-[10px] px-1.5 py-0.5 rounded
                              ${isPublished ? 'bg-emerald-500/10 text-emerald-500' : 'bg-slate-400/10 text-text-tertiary'}`}>
                {isPublished ? '已发布' : '草稿'}
            </span>
        </div>
    );
})
```

关键改动：`flex-1 min-w-0 truncate` 让 name 占据剩余空间但不溢出；`flex-shrink-0` 让标签不被压缩。

- [ ] **Step 2: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx
git commit -m "fix(S9/F2-T02): ScreenJumpPicker 列表项 flex 塌陷修复"
```

---

### Task F2-T03: 默认模式调整为 'screen'

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx`

- [ ] **Step 1: 修改 useState 初始值**

定位 `PropertyPanel.tsx:832`：

```tsx
// 修改前
const [mode, setMode] = useState<'screen' | 'custom'>(isScreenRef ? 'screen' : 'custom');
// 修改后
const [mode, setMode] = useState<'screen' | 'custom'>(
    !value || isScreenRef ? 'screen' : 'custom'
);
```

- [ ] **Step 2: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx
git commit -m "fix(S9/F2-T03): ScreenJumpPicker 空值默认为'选择大屏'模式"
```

---

### Task F2-T04: 动作配置区段视觉一致性（卡片背景）

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx`

- [ ] **Step 1: 替换 action 卡片背景**

定位 `PropertyPanel.tsx:6086-6094`。把：

```tsx
<div
    key={`action-${index}`}
    style={{
        border: '1px solid rgba(148,163,184,0.18)',
        borderRadius: 10,
        padding: 10,
        marginBottom: 10,
        background: 'rgba(248,250,252,0.72)',
    }}
>
```

改为：

```tsx
<div
    key={`action-${index}`}
    className="border border-border-default rounded-[10px] p-2.5 mb-2.5 bg-surface-muted/40"
>
```

- [ ] **Step 2: 替换内层 mapping 卡片**

定位 `PropertyPanel.tsx:6125-6133`。把：

```tsx
<div
    key={`action-${index}-mapping-${mappingIndex}`}
    style={{
        border: '1px dashed rgba(148,163,184,0.22)',
        borderRadius: 8,
        padding: 8,
        marginBottom: 8,
    }}
>
```

改为：

```tsx
<div
    key={`action-${index}-mapping-${mappingIndex}`}
    className="border border-dashed border-border-default rounded-lg p-2 mb-2"
>
```

- [ ] **Step 3: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx
git commit -m "fix(S9/F2-T04): 动作配置区段卡片背景使用主题变量,修复暗色主题留白"
```

---

## F3: JSON 实例死链清理

### Task F3-T01: 清理 5 个 GPMC v2 大屏 JSON

**Files:**
- Modify: `worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-strategic-overview-v2.json`
- Modify: `worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-execution-board-v2.json`
- Modify: `worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-quality-board-v2.json`
- Modify: `worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-risk-board-v2.json`
- Modify: `worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-tech-state-board-v2.json`

- [ ] **Step 1: 备份与确认起始状态**

```bash
cd /opt/prod/s10/s10-stack
git status worklog/v2.2.3/s10/pjm/v2/screen-instances/
# 确认无未提交修改
```

- [ ] **Step 2: 清空所有 screen-ref 的 fallback 部分（5 个文件）**

对每个文件用 `Edit replace_all`，把所有 `|%2F` URL 编码后缀清空。需要识别的 7 个 URL 后缀模式（按 v2 实际出现）：

- `|%2Fbi%2Fgpmc` → `|`
- `|%2Fbi%2Fgpmc%2Fexecution` → `|`
- `|%2Fbi%2Fgpmc%2Fquality` → `|`
- `|%2Fbi%2Fgpmc%2Ftech-state` → `|`
- `|%2Fbi%2Fgpmc%2Fcost` → `|`
- `|%2Fbi%2Fgpmc%2Frisk` → `|`

每次替换后立即用 `python -m json.tool` 验证 JSON 合法。

- [ ] **Step 3: 删除直链 `/bi/gpmc/drill/...` 类的整条 action**

直链模式：
- `/bi/gpmc/drill/execution`
- `/bi/gpmc/drill/quality`
- `/bi/gpmc/drill/risk`
- `/bi/gpmc/drill/tech-state`

每条 action 形如：

```json
{
  "type": "jump-url",
  "jumpUrlTemplate": "/bi/gpmc/drill/execution",
  "jumpOpenMode": "new-tab"
}
```

需要从 `actions` 数组中精确删除该对象。**不能用简单字符串替换**（会破坏数组语法）。改用脚本：

```python
import json
from pathlib import Path

DEAD_PATHS = {
    '/bi/gpmc/drill/execution',
    '/bi/gpmc/drill/quality',
    '/bi/gpmc/drill/risk',
    '/bi/gpmc/drill/tech-state',
}

def clean_actions(node):
    if isinstance(node, dict):
        if 'actions' in node and isinstance(node['actions'], list):
            node['actions'] = [
                a for a in node['actions']
                if not (isinstance(a, dict) and a.get('type') == 'jump-url'
                        and a.get('jumpUrlTemplate') in DEAD_PATHS)
            ]
        for v in node.values():
            clean_actions(v)
    elif isinstance(node, list):
        for item in node:
            clean_actions(item)

for f in Path('worklog/v2.2.3/s10/pjm/v2/screen-instances').glob('gpmc-*-board-v2.json'):
    data = json.loads(f.read_text())
    clean_actions(data)
    f.write_text(json.dumps(data, indent=2, ensure_ascii=False))
    print(f'cleaned {f.name}')

# 同时处理 gpmc-strategic-overview-v2.json
f = Path('worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-strategic-overview-v2.json')
data = json.loads(f.read_text())
clean_actions(data)
f.write_text(json.dumps(data, indent=2, ensure_ascii=False))
print(f'cleaned {f.name}')
```

注意 Python 脚本会**重新格式化整个 JSON**（缩进 2 空格 + ensure_ascii=False）。如果原文件格式与此不一致，可能产生无关 diff，需要验证缩进风格。先 cat 一个 JSON 头部确认。

- [ ] **Step 4: 验证 JSON 合法 + 死链清零**

```bash
for f in worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-*.json; do
    python -m json.tool "$f" > /dev/null && echo "OK: $f" || echo "INVALID: $f"
done

# 死链残留检查（应该为 0）
grep -c '/bi/gpmc/drill/' worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-*-v2.json

# screen-ref fallback 残留检查（应该为 0）
grep -c '|%2Fbi%2Fgpmc' worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-*-v2.json
```

注意：v2 目录下还有 `gpmc-drill-*-v2.json` 4 个独立 drill 文件，**保留不动**，过滤时只匹配 `*-board-v2.json` 和 `gpmc-strategic-overview-v2.json`。

- [ ] **Step 5: Commit**

```bash
git add worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-strategic-overview-v2.json
git add worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-execution-board-v2.json
git add worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-quality-board-v2.json
git add worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-risk-board-v2.json
git add worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-tech-state-board-v2.json
git commit -m "chore(S9/F3-T01): 清理 5 个 GPMC v2 大屏 JSON 死链 fallback 与失效直链"
```

---

## F4: 父项目汇总甘特组件改造

### Task F4-T01: types.ts 类型扩展

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/types.ts`

- [ ] **Step 1: 新增 MajorProject 相关类型**

在 types.ts 末尾追加：

```ts
export interface MajorProjectKPI {
    completionRate?: number;       // 0-100
    milestoneRate?: number;        // 0-1, null 表示无里程碑
    highRiskCount?: number;
    delayDays?: number;
}

export interface MajorProjectRisk {
    level: 'high' | 'warn' | 'normal';
    label: string;
    taskRef?: string;
}

export interface SubProject {
    name: string;
    tasks: ProjectGanttTask[];
}

export interface MajorProject {
    name: string;
    responsibleDept?: string;
    manager?: string;
    instituteLeader?: string;
    startDate?: string;
    plannedDeliveryDate?: string;
    stage?: string;
    kpi?: MajorProjectKPI;
    risks?: MajorProjectRisk[];
    subprojects: SubProject[];
}
```

- [ ] **Step 2: 扩展 gantt-chart config 类型**

定位 types.ts 中 `ScreenComponent.config` 的定义（或 gantt 专用配置）。如果是松散 Record，则无需改动；如果是严格类型，添加：

```ts
renderMode?: 'echarts' | 'board' | 'board-hierarchical';
drillMode?: 'none' | 'modal';
```

- [ ] **Step 3: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/types.ts
git commit -m "feat(S9/F4-T01): 新增 MajorProject 相关类型 + gantt-chart config 扩展"
```

---

### Task F4-T02: majorProjectAggregator transformer 实现

**Files:**
- Add: `source/dts-platform-webapp/src/analytics/pages/project-cockpit/utils/majorProjectAggregator.ts`

- [ ] **Step 1: 创建 utils 目录（如不存在）**

```bash
mkdir -p source/dts-platform-webapp/src/analytics/pages/project-cockpit/utils
```

- [ ] **Step 2: 实现 transformer**

```ts
import type { MajorProject, MajorProjectKPI, MajorProjectRisk, SubProject } from '../../screens/types';
import type { ProjectGanttTask } from '../components/ProjectGanttBoard';

export interface FlatProjectNodeRow {
    重大项目?: string | null;
    子项目?: string | null;
    任务?: string | null;
    类型?: string | null;
    计划日期?: string | null;
    实际日期?: string | null;
    基线日期?: string | null;
    是否完成?: boolean | null;
    是否超期完成?: boolean | null;
    是否未完成?: boolean | null;
    延期天数?: number | null;
    风险等级?: string | null;
    完成情况?: string | null;
    责任科室?: string | null;
    责任人?: string | null;
    项目经理?: string | null;
    所领导?: string | null;
    风险内容?: string | null;
    延期影响?: string | null;
}

function rowToTask(r: FlatProjectNodeRow): ProjectGanttTask {
    const isCompleted = r.是否完成 === true;
    const isOverdue = r.是否超期完成 === true || (r.延期天数 != null && r.延期天数 > 0);
    return {
        name: String(r.任务 ?? '').trim(),
        type: String(r.类型 ?? '一般任务'),
        planDate: r.计划日期 ?? undefined,
        planEndDate: r.计划日期 ?? undefined,
        baselineStartDate: r.基线日期 ?? undefined,
        baselineEndDate: r.基线日期 ?? undefined,
        actualDate: r.实际日期 ?? undefined,
        delayDays: r.延期天数 ?? 0,
        riskLevel: String(r.风险等级 ?? ''),
        owner: String(r.责任人 ?? r.责任科室 ?? ''),
        majorProjectName: String(r.重大项目 ?? ''),
        subprojectName: String(r.子项目 ?? ''),
        status: String(r.完成情况 ?? ''),
        isCompleted,
        isOverdue,
        isIncomplete: r.是否未完成 === true,
    } as ProjectGanttTask;
}

function pickFirstNonEmpty(rows: FlatProjectNodeRow[], key: keyof FlatProjectNodeRow): string | undefined {
    for (const r of rows) {
        const v = r[key];
        if (v != null && String(v).trim() !== '') return String(v);
    }
    return undefined;
}

function aggregateMeta(rows: FlatProjectNodeRow[]): Pick<MajorProject, 'responsibleDept' | 'manager' | 'instituteLeader' | 'startDate' | 'plannedDeliveryDate' | 'stage'> {
    const planDates = rows.map(r => r.计划日期).filter((v): v is string => !!v).sort();
    const stages = rows.map(r => r.完成情况).filter((v): v is string => !!v);
    const allCompleted = rows.length > 0 && rows.every(r => r.是否完成 === true);
    const anyDelayed = rows.some(r => (r.延期天数 ?? 0) > 0);
    let derivedStage: string;
    if (allCompleted) derivedStage = '已完成';
    else if (anyDelayed) derivedStage = '存在延期';
    else derivedStage = '进行中';
    return {
        responsibleDept: pickFirstNonEmpty(rows, '责任科室'),
        manager: pickFirstNonEmpty(rows, '项目经理'),
        instituteLeader: pickFirstNonEmpty(rows, '所领导'),
        startDate: planDates[0],
        plannedDeliveryDate: planDates[planDates.length - 1],
        stage: stages[0] ?? derivedStage,
    };
}

function aggregateKpi(rows: FlatProjectNodeRow[]): MajorProjectKPI {
    const total = rows.length;
    const completed = rows.filter(r => r.是否完成 === true).length;
    const milestones = rows.filter(r => String(r.类型 ?? '').includes('里程碑'));
    const milestoneCompleted = milestones.filter(r => r.是否完成 === true).length;
    const highRiskOpen = rows.filter(r => String(r.风险等级 ?? '') === '高' && r.是否完成 !== true).length;
    const totalDelay = rows.reduce((sum, r) => sum + Math.max(0, r.延期天数 ?? 0), 0);
    return {
        completionRate: total === 0 ? 0 : Math.round((completed / total) * 1000) / 10,
        milestoneRate: milestones.length === 0 ? undefined : Math.round((milestoneCompleted / milestones.length) * 1000) / 1000,
        highRiskCount: highRiskOpen,
        delayDays: totalDelay,
    };
}

function aggregateRisks(rows: FlatProjectNodeRow[]): MajorProjectRisk[] {
    const candidates = rows
        .filter(r => String(r.风险等级 ?? '') === '高' || (r.延期天数 ?? 0) > 0)
        .sort((a, b) => (b.延期天数 ?? 0) - (a.延期天数 ?? 0))
        .slice(0, 8);
    return candidates.map(r => {
        const level: MajorProjectRisk['level'] = String(r.风险等级 ?? '') === '高' ? 'high' : 'warn';
        const days = r.延期天数 ?? 0;
        const desc = String(r.风险内容 ?? r.延期影响 ?? r.任务 ?? '').slice(0, 24);
        const suffix = days > 0 ? ` (${days}d)` : '';
        return {
            level,
            label: `${desc}${suffix}`,
            taskRef: String(r.任务 ?? ''),
        };
    });
}

export function aggregateMajorProjects(rows: FlatProjectNodeRow[]): MajorProject[] {
    const byProject = new Map<string, FlatProjectNodeRow[]>();
    for (const r of rows) {
        const key = String(r.重大项目 ?? '').trim();
        if (!key) continue;
        const list = byProject.get(key);
        if (list) list.push(r);
        else byProject.set(key, [r]);
    }
    const result: MajorProject[] = [];
    for (const [projectName, projectRows] of byProject.entries()) {
        const bySub = new Map<string, FlatProjectNodeRow[]>();
        for (const r of projectRows) {
            const subKey = String(r.子项目 ?? '(无子项目)').trim() || '(无子项目)';
            const list = bySub.get(subKey);
            if (list) list.push(r);
            else bySub.set(subKey, [r]);
        }
        const subprojects: SubProject[] = [];
        for (const [subName, subRows] of bySub.entries()) {
            subprojects.push({ name: subName, tasks: subRows.map(rowToTask) });
        }
        result.push({
            name: projectName,
            ...aggregateMeta(projectRows),
            kpi: aggregateKpi(projectRows),
            risks: aggregateRisks(projectRows),
            subprojects,
        });
    }
    return result;
}
```

- [ ] **Step 3: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/project-cockpit/utils/majorProjectAggregator.ts
git commit -m "feat(S9/F4-T02): 新增 majorProjectAggregator transformer"
```

---

### Task F4-T03: majorProjectAggregator 单测

**Files:**
- Add: `source/dts-platform-webapp/src/analytics/pages/project-cockpit/utils/majorProjectAggregator.test.ts`

- [ ] **Step 1: 写单测**

```ts
import { describe, it, expect } from 'vitest';
import { aggregateMajorProjects, type FlatProjectNodeRow } from './majorProjectAggregator';

const sampleRows: FlatProjectNodeRow[] = [
    { 重大项目: '制造协同平台', 子项目: '平台基础', 任务: '需求分析', 类型: '一般任务',
      计划日期: '2026-01-06', 实际日期: '2026-02-15', 是否完成: true, 是否超期完成: false,
      延期天数: 0, 风险等级: '低', 责任科室: '研发部', 项目经理: '张工' },
    { 重大项目: '制造协同平台', 子项目: '平台基础', 任务: '系统设计', 类型: '一般任务',
      计划日期: '2026-02-10', 实际日期: '2026-03-20', 是否完成: true, 延期天数: 0,
      风险等级: '低', 责任科室: '研发部', 项目经理: '张工' },
    { 重大项目: '制造协同平台', 子项目: '平台基础', 任务: '里程碑 M1', 类型: '里程碑节点',
      计划日期: '2026-03-20', 实际日期: '2026-03-20', 是否完成: true, 延期天数: 0,
      风险等级: '低', 责任科室: '研发部', 项目经理: '张工' },
    { 重大项目: '制造协同平台', 子项目: '核心模块', 任务: '核心开发', 类型: '一般任务',
      计划日期: '2026-03-01', 是否完成: false, 延期天数: 0, 风险等级: '中', 责任科室: '研发部' },
    { 重大项目: '新能源工厂', 子项目: '厂房建设', 任务: '基建施工', 类型: '一般任务',
      计划日期: '2026-01-15', 是否完成: false, 是否未完成: true, 延期天数: 28,
      风险等级: '高', 责任科室: '工程建设', 项目经理: '刘工' },
];

describe('aggregateMajorProjects', () => {
    it('groups by 重大项目', () => {
        const result = aggregateMajorProjects(sampleRows);
        expect(result).toHaveLength(2);
        expect(result.map(p => p.name).sort()).toEqual(['制造协同平台', '新能源工厂']);
    });

    it('groups subprojects within each major project', () => {
        const result = aggregateMajorProjects(sampleRows);
        const platform = result.find(p => p.name === '制造协同平台')!;
        expect(platform.subprojects).toHaveLength(2);
        const subNames = platform.subprojects.map(s => s.name).sort();
        expect(subNames).toEqual(['平台基础', '核心模块']);
    });

    it('computes completionRate correctly', () => {
        const result = aggregateMajorProjects(sampleRows);
        const platform = result.find(p => p.name === '制造协同平台')!;
        // 4 rows, 3 completed → 75%
        expect(platform.kpi?.completionRate).toBe(75);
    });

    it('computes milestoneRate using only milestone rows', () => {
        const result = aggregateMajorProjects(sampleRows);
        const platform = result.find(p => p.name === '制造协同平台')!;
        // 1 milestone, 1 completed → 1.000
        expect(platform.kpi?.milestoneRate).toBe(1);
    });

    it('counts highRiskCount as open high-risk only', () => {
        const result = aggregateMajorProjects(sampleRows);
        const factory = result.find(p => p.name === '新能源工厂')!;
        expect(factory.kpi?.highRiskCount).toBe(1);
    });

    it('sums delayDays', () => {
        const result = aggregateMajorProjects(sampleRows);
        const factory = result.find(p => p.name === '新能源工厂')!;
        expect(factory.kpi?.delayDays).toBe(28);
    });

    it('produces risks list sorted by delayDays desc', () => {
        const result = aggregateMajorProjects(sampleRows);
        const factory = result.find(p => p.name === '新能源工厂')!;
        expect(factory.risks).toHaveLength(1);
        expect(factory.risks?.[0].level).toBe('high');
    });

    it('uses pickFirstNonEmpty for manager', () => {
        const result = aggregateMajorProjects(sampleRows);
        expect(result.find(p => p.name === '制造协同平台')?.manager).toBe('张工');
        expect(result.find(p => p.name === '新能源工厂')?.manager).toBe('刘工');
    });

    it('derives startDate as earliest planDate', () => {
        const result = aggregateMajorProjects(sampleRows);
        expect(result.find(p => p.name === '制造协同平台')?.startDate).toBe('2026-01-06');
    });

    it('handles rows with empty 重大项目 by skipping', () => {
        const rows: FlatProjectNodeRow[] = [
            ...sampleRows,
            { 重大项目: '', 子项目: 'X', 任务: 'noise' },
            { 重大项目: undefined, 子项目: 'X', 任务: 'noise' },
        ];
        const result = aggregateMajorProjects(rows);
        expect(result).toHaveLength(2);
    });

    it('uses (无子项目) for null subsystem', () => {
        const rows: FlatProjectNodeRow[] = [
            { 重大项目: 'A', 子项目: null, 任务: 't1', 计划日期: '2026-01-01' },
        ];
        const result = aggregateMajorProjects(rows);
        expect(result[0].subprojects[0].name).toBe('(无子项目)');
    });
});
```

- [ ] **Step 2: 运行单测**

```bash
cd source/dts-platform-webapp && pnpm vitest run majorProjectAggregator 2>&1 | tail -25
```
Expected: 11 passed。

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/project-cockpit/utils/majorProjectAggregator.test.ts
git commit -m "test(S9/F4-T03): majorProjectAggregator transformer 单测"
```

---

### Task F4-T04: ProjectGanttBoard hierarchical 模式 + flat 模式增强

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/ProjectGanttBoard.tsx`

- [ ] **Step 1: 扩展 Props 类型**

```ts
import type { MajorProject } from '../../screens/types';

type Props = {
    tasks?: ProjectGanttTask[];
    majorProjects?: MajorProject[];
    renderMode?: 'flat' | 'hierarchical';
    onProjectClick?: (project: MajorProject) => void;
    onTaskClick?: (task: ProjectGanttTask) => void;
    highlightedTaskName?: string;
    maxHeight?: number;
    sideTextColor?: string;
    dark?: boolean;
};
```

- [ ] **Step 2: 在主组件函数顶部增加分支**

```tsx
export function ProjectGanttBoard(props: Props) {
    if (props.renderMode === 'hierarchical' && props.majorProjects) {
        return <HierarchicalGantt {...props} majorProjects={props.majorProjects} />;
    }
    return <FlatGantt {...props} tasks={props.tasks ?? []} />;
}
```

把现有渲染逻辑抽到 `FlatGantt` 内部。

- [ ] **Step 3: 实现 HierarchicalGantt**

```tsx
function HierarchicalGantt({ majorProjects, onProjectClick, maxHeight, dark, sideTextColor }: ...) {
    if (majorProjects.length === 0) {
        return <div className="...">当前筛选范围暂无重大项目</div>;
    }
    // 算 start/end 全局时间范围
    const allDates = majorProjects.flatMap(p =>
        p.subprojects.flatMap(sp => sp.tasks.flatMap(t => [t.planDate, t.actualDate, t.planEndDate]))
    ).filter(Boolean).map(s => new Date(s as string).getTime()).filter(Number.isFinite);
    if (allDates.length === 0) {
        return <div className="...">缺少计划日期</div>;
    }
    const start = Math.min(...allDates);
    const end = Math.max(...allDates, Date.now());
    const total = Math.max(end - start, 1);

    return (
        <div className="flex flex-col gap-3" style={{ maxHeight: maxHeight ?? 520, overflowY: 'auto' }}>
            {majorProjects.map(project => (
                <ProjectSummaryRow
                    key={project.name}
                    project={project}
                    start={start}
                    total={total}
                    onClick={onProjectClick}
                    dark={dark}
                />
            ))}
        </div>
    );
}

function ProjectSummaryRow({ project, start, total, onClick, dark }: ...) {
    const allTasks = project.subprojects.flatMap(sp => sp.tasks);
    const dates = allTasks.map(t => new Date(t.planDate || '').getTime()).filter(Number.isFinite);
    const projectStart = Math.min(...dates);
    const projectEnd = Math.max(...dates);
    const left = ((projectStart - start) / total) * 100;
    const width = Math.max(((projectEnd - projectStart) / total) * 100, 2);
    const tone = (project.kpi?.highRiskCount ?? 0) > 0 ? 'high'
        : (project.kpi?.delayDays ?? 0) > 0 ? 'warn' : 'normal';
    const interactive = typeof onClick === 'function';
    return (
        <div
            className={`grid grid-cols-[240px_1fr_170px] gap-3 items-center rounded-xl py-2 px-3
                        ${interactive ? `cursor-pointer ${dark ? 'hover:bg-blue-500/[0.12]' : 'hover:bg-blue-600/[0.06]'}` : ''}`}
            role={interactive ? 'button' : undefined}
            tabIndex={interactive ? 0 : undefined}
            onClick={interactive ? () => onClick(project) : undefined}
            onKeyDown={interactive ? (e) => {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(project); }
            } : undefined}
        >
            <div className="flex flex-col gap-0.5">
                <strong className="text-[14px]">{project.name}</strong>
                <span className="text-xs text-text-secondary">
                    {project.responsibleDept} · {project.manager}
                </span>
            </div>
            <div className="relative h-10 rounded-full" style={{ background: TRACK_BG }}>
                <div
                    className="absolute top-2 h-6 rounded-full text-white text-xs flex items-center justify-center px-3"
                    style={{
                        left: `${left}%`,
                        width: `${width}%`,
                        background: TONE_BG[tone],
                    }}
                >
                    {project.subprojects.length} 子项目 · {allTasks.length} 任务
                </div>
            </div>
            <div className="flex flex-col gap-0.5 text-xs">
                <span className="text-[18px] font-bold">{project.kpi?.completionRate ?? 0}%</span>
                <span className="text-text-secondary">交付 {project.plannedDeliveryDate ?? '--'}</span>
            </div>
        </div>
    );
}
```

- [ ] **Step 4: flat 模式增强（里程碑菱形 + 今日红线 + 高亮）**

在 `FlatGantt` 内部 `renderRow` 函数末尾，task.type === '里程碑节点' 时改用菱形渲染（用 inline SVG 或 CSS rotate）。在 task 行的 actualBar div 上，根据 `props.highlightedTaskName === task.name` 加 `box-shadow: 0 0 0 3px rgba(239,68,68,0.5)`。

在外层容器渲染一条今日红线：在 track 的 absolute 容器内加：

```tsx
{showTodayLine && (
    <div
        className="absolute top-0 bottom-0 w-0.5 bg-red-500/60 pointer-events-none"
        style={{ left: `${((Date.now() - start) / total) * 100}%` }}
    />
)}
```

- [ ] **Step 5: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 6: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/ProjectGanttBoard.tsx
git commit -m "feat(S9/F4-T04): ProjectGanttBoard 新增 hierarchical 模式 + flat 增强(里程碑/今日线/高亮)"
```

---

## F5: ProjectDetailGanttModal 弹层组件

### Task F5-T01: 组件骨架 + Portal + ESC 关闭

**Files:**
- Add: `source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/ProjectDetailGanttModal.tsx`

- [ ] **Step 1: 创建组件文件骨架**

```tsx
import React, { useEffect, useState, useMemo } from 'react';
import ReactDOM from 'react-dom';
import type { MajorProject } from '../../screens/types';
import { ProjectGanttBoard } from './ProjectGanttBoard';

interface Props {
    project: MajorProject | null;
    onClose: () => void;
}

export function ProjectDetailGanttModal({ project, onClose }: Props) {
    const [highlightedTask, setHighlightedTask] = useState<string | undefined>();
    const [activeSub, setActiveSub] = useState<string>('全部');

    useEffect(() => {
        if (!project) return;
        setActiveSub('全部');
        setHighlightedTask(undefined);
        const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [project, onClose]);

    const allTasks = useMemo(() => {
        if (!project) return [];
        return project.subprojects.flatMap(sp =>
            sp.tasks.map(t => ({ ...t, subprojectName: sp.name }))
        );
    }, [project]);

    const visibleTasks = useMemo(() => {
        if (activeSub === '全部') return allTasks;
        return allTasks.filter(t => t.subprojectName === activeSub);
    }, [allTasks, activeSub]);

    if (!project) return null;

    return ReactDOM.createPortal(
        <div className="pgm-modal-overlay" onClick={onClose}>
            <div className="pgm-modal-window" onClick={(e) => e.stopPropagation()}>
                <ModalHeader project={project} onClose={onClose} />
                <div className="pgm-modal-body">
                    <ModalSidebar
                        project={project}
                        onRiskClick={(taskRef) => {
                            setHighlightedTask(taskRef);
                            setTimeout(() => setHighlightedTask(undefined), 1200);
                        }}
                    />
                    <ModalGanttArea
                        project={project}
                        visibleTasks={visibleTasks}
                        activeSub={activeSub}
                        onSubChange={setActiveSub}
                        highlightedTaskName={highlightedTask}
                    />
                </div>
                <ModalFooter />
            </div>
        </div>,
        document.body
    );
}

function ModalHeader({ project, onClose }: { project: MajorProject; onClose: () => void }) {
    return (
        <div className="flex items-center justify-between px-6 h-14 border-b border-slate-200 flex-shrink-0">
            <div className="flex items-baseline gap-3">
                <h2 className="text-xl font-bold text-slate-900">{project.name}</h2>
                <span className="text-sm text-slate-500">
                    {project.responsibleDept ? `${project.responsibleDept}` : ''}
                    {project.manager ? ` · ${project.manager}` : ''}
                </span>
            </div>
            <button
                type="button"
                onClick={onClose}
                className="w-8 h-8 rounded-full flex items-center justify-center text-slate-500 hover:bg-slate-100"
                aria-label="关闭"
            >
                ✕
            </button>
        </div>
    );
}

function ModalSidebar({ project, onRiskClick }: { project: MajorProject; onRiskClick: (taskRef: string | undefined) => void }) {
    return (
        <div className="w-80 flex-shrink-0 border-r border-slate-200 overflow-y-auto p-5 flex flex-col gap-5">
            {/* 元信息 */}
            <section>
                <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">项目信息</h3>
                <dl className="grid grid-cols-[80px_1fr] gap-y-1.5 text-xs">
                    <dt className="text-slate-500">责任部门</dt><dd className="font-semibold text-slate-700">{project.responsibleDept ?? '--'}</dd>
                    <dt className="text-slate-500">项目经理</dt><dd className="font-semibold text-slate-700">{project.manager ?? '--'}</dd>
                    <dt className="text-slate-500">所领导</dt><dd className="font-semibold text-slate-700">{project.instituteLeader ?? '--'}</dd>
                    <dt className="text-slate-500">启动</dt><dd className="font-semibold text-slate-700">{project.startDate ?? '--'}</dd>
                    <dt className="text-slate-500">计划交付</dt><dd className="font-semibold text-slate-700">{project.plannedDeliveryDate ?? '--'}</dd>
                    <dt className="text-slate-500">阶段</dt><dd className="font-semibold text-slate-700">{project.stage ?? '--'}</dd>
                </dl>
            </section>
            {/* KPI */}
            <section>
                <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">关键指标</h3>
                <div className="grid grid-cols-2 gap-2">
                    <KpiMini label="完成率" value={project.kpi?.completionRate ?? 0} suffix="%" />
                    <KpiMini label="里程碑" value={project.kpi?.milestoneRate != null ? Math.round(project.kpi.milestoneRate * 100) : '--'} suffix={project.kpi?.milestoneRate != null ? '%' : ''} />
                    <KpiMini label="高风险" value={project.kpi?.highRiskCount ?? 0} suffix="项" />
                    <KpiMini label="累计延期" value={project.kpi?.delayDays ?? 0} suffix="d" />
                </div>
            </section>
            {/* 风险列表 */}
            <section>
                <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">风险与延期</h3>
                <ul className="flex flex-col gap-1.5">
                    {(project.risks ?? []).length === 0 ? (
                        <li className="text-xs text-slate-400">暂无风险项</li>
                    ) : (
                        (project.risks ?? []).map((risk, i) => (
                            <li
                                key={i}
                                onClick={() => onRiskClick(risk.taskRef)}
                                className="flex items-center gap-1.5 px-2 py-1.5 rounded text-xs cursor-pointer hover:bg-slate-100"
                            >
                                <span>{risk.level === 'high' ? '🔴' : '🟡'}</span>
                                <span className="flex-1 min-w-0 truncate text-slate-700">{risk.label}</span>
                            </li>
                        ))
                    )}
                </ul>
            </section>
        </div>
    );
}

function KpiMini({ label, value, suffix }: { label: string; value: number | string; suffix?: string }) {
    return (
        <div className="bg-slate-50 rounded-lg p-2.5">
            <div className="text-[11px] text-slate-500">{label}</div>
            <div className="text-xl font-bold text-slate-900 leading-tight">
                {value}<span className="text-xs font-normal text-slate-500 ml-0.5">{suffix}</span>
            </div>
        </div>
    );
}

function ModalGanttArea({ project, visibleTasks, activeSub, onSubChange, highlightedTaskName }: {
    project: MajorProject;
    visibleTasks: any[];
    activeSub: string;
    onSubChange: (sub: string) => void;
    highlightedTaskName?: string;
}) {
    const subNames = ['全部', ...project.subprojects.map(sp => sp.name)];
    return (
        <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
            {/* Sub chips */}
            <div className="flex items-center gap-1.5 px-5 py-2 border-b border-slate-100 flex-shrink-0">
                {subNames.map(name => (
                    <button
                        key={name}
                        type="button"
                        onClick={() => onSubChange(name)}
                        className={`px-2.5 py-1 rounded-full text-xs ${activeSub === name ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                    >
                        {name}
                    </button>
                ))}
            </div>
            {/* Gantt 主体 */}
            <div className="flex-1 overflow-y-auto px-5 py-3 min-h-0">
                <ProjectGanttBoard
                    tasks={visibleTasks}
                    renderMode="flat"
                    highlightedTaskName={highlightedTaskName}
                    dark={false}
                />
            </div>
        </div>
    );
}

function ModalFooter() {
    return (
        <div className="flex items-center justify-center gap-5 h-11 border-t border-slate-200 bg-slate-50 flex-shrink-0 text-xs text-slate-500">
            <Legend color="#2563eb" label="计划" />
            <Legend color="#10b981" label="实际" />
            <Legend color="#cbd5e1" label="基线" />
            <Legend color="#f59e0b" label="里程碑" shape="diamond" />
            <Legend color="#ef4444" label="今日" shape="line" />
        </div>
    );
}

function Legend({ color, label, shape }: { color: string; label: string; shape?: 'diamond' | 'line' }) {
    return (
        <span className="flex items-center gap-1.5">
            {shape === 'diamond' ? (
                <span className="w-2 h-2 rotate-45" style={{ background: color }} />
            ) : shape === 'line' ? (
                <span className="w-3 h-0.5" style={{ background: color }} />
            ) : (
                <span className="w-3 h-2 rounded-sm" style={{ background: color }} />
            )}
            {label}
        </span>
    );
}
```

- [ ] **Step 2: 添加样式（直接 inline 或独立 CSS 文件）**

在 `ProjectDetailGanttModal.tsx` 同目录下创建 `ProjectDetailGanttModal.css`：

```css
.pgm-modal-overlay {
    position: fixed;
    inset: 0;
    z-index: 9999;
    background: rgba(2, 6, 23, 0.72);
    -webkit-backdrop-filter: blur(8px);
    backdrop-filter: blur(8px);
    display: flex;
    align-items: center;
    justify-content: center;
    animation: pgm-fade-in 200ms ease-out;
}

.pgm-modal-window {
    width: 1440px;
    height: 820px;
    max-width: calc(100vw - 80px);
    max-height: calc(100vh - 80px);
    background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
    border-radius: 20px;
    box-shadow: 0 32px 80px rgba(2, 6, 23, 0.5),
                0 0 0 1px rgba(148, 163, 184, 0.18);
    overflow: hidden;
    display: flex;
    flex-direction: column;
    animation: pgm-scale-in 300ms cubic-bezier(0.16, 1, 0.3, 1);
}

.pgm-modal-body {
    flex: 1;
    display: flex;
    min-height: 0;
}

@keyframes pgm-fade-in {
    from { opacity: 0; }
    to   { opacity: 1; }
}

@keyframes pgm-scale-in {
    from { opacity: 0; transform: scale(0.96); }
    to   { opacity: 1; transform: scale(1); }
}
```

在 `ProjectDetailGanttModal.tsx` 顶部 import：
```ts
import './ProjectDetailGanttModal.css';
```

- [ ] **Step 3: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/ProjectDetailGanttModal.tsx
git add source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/ProjectDetailGanttModal.css
git commit -m "feat(S9/F5-T01): 新建 ProjectDetailGanttModal 弹层组件(Portal+ESC+布局)"
```

---

### Task F5-T02: EChartsRenderer 接入 board-hierarchical 模式

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/EChartsRenderer.tsx`

- [ ] **Step 1: 定位 dataSource rows 的取法**

在 EChartsRenderer.tsx 中查找：现有 case 如何获取 `dataSource` 的 rows。常见命名是 `rows`、`dataSourceRows`、`data` 之类。这一步先 Read 该文件 100-300 行附近，记录变量名称。

- [ ] **Step 2: 在 gantt-chart case 添加 board-hierarchical 分支**

定位 `EChartsRenderer.tsx:323`。在现有 `if (ganttRenderMode === 'board')` 之前添加：

```tsx
case 'gantt-chart': {
    const tasks = Array.isArray(c.tasks) ? (c.tasks as Array<Record<string, any>>) : [];
    const ganttRenderMode = String(c.renderMode ?? '').trim().toLowerCase();
    
    // ★ 新增分支
    if (ganttRenderMode === 'board-hierarchical') {
        const drillMode = String(c.drillMode ?? 'none');
        // 假设 dataSource 行通过某变量提供（具体名称在 Step 1 确认）
        const sourceRows = (rows ?? dataSourceRows ?? []) as FlatProjectNodeRow[];
        const majorProjects = useMemo(() => aggregateMajorProjects(sourceRows), [sourceRows]);
        const [activeProject, setActiveProject] = useState<MajorProject | null>(null);
        return (
            <>
                <ProjectGanttBoard
                    renderMode="hierarchical"
                    majorProjects={majorProjects}
                    maxHeight={height}
                    onProjectClick={drillMode === 'modal' ? setActiveProject : undefined}
                    sideTextColor={typeof c.sideTextColor === 'string' ? c.sideTextColor : undefined}
                    dark={isLightColor(t.textPrimary)}
                />
                {drillMode === 'modal' && (
                    <ProjectDetailGanttModal
                        project={activeProject}
                        onClose={() => setActiveProject(null)}
                    />
                )}
            </>
        );
    }
    
    // 现有 board 分支（保持不变）
    if (ganttRenderMode === 'board') {
        // ...
    }
    // 现有 echarts 分支（保持不变）
}
```

需要在文件顶部 import（参考现有 `import { ProjectGanttBoard, type ProjectGanttTask } from '../../project-cockpit/components/ProjectGanttBoard'` 的相对路径）：
```ts
import { ProjectDetailGanttModal } from '../../project-cockpit/components/ProjectDetailGanttModal';
import { aggregateMajorProjects, type FlatProjectNodeRow } from '../../project-cockpit/utils/majorProjectAggregator';
import type { MajorProject } from '../types';
```

- [ ] **Step 3: TypeScript 编译验证**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit 2>&1 | tail -10
```

如出现错误，按错误提示调整 dataSource rows 的取法。

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/renderers/EChartsRenderer.tsx
git commit -m "feat(S9/F5-T02): EChartsRenderer 接入 gantt-chart board-hierarchical + 弹层"
```

---

## F6: gpmc-execution-gantt JSON 实例改造

### Task F6-T01: 替换硬编码 tasks 为 SQL 数据源

**Files:**
- Modify: `worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-execution-board-v2.json`

- [ ] **Step 1: 定位 gpmc-execution-gantt 组件**

在 JSON 中找到 `"id": "gpmc-execution-gantt"` 的对象（约行 920-1108）。

- [ ] **Step 2: 用 Edit 整体替换 config / actions / dataSource 三个字段**

把现有 `config` 中的 `tasks` 数组 + 现有 `actions` 中的死链 action + 现有 `dataSource.sqlConfig.query` 全部替换为：

```json
"config": {
  "title": "重大项目执行甘特(点击下钻子项目)",
  "renderMode": "board-hierarchical",
  "drillMode": "modal",
  "sideTextColor": "#1c2833"
},
"actions": [],
"dataSource": {
  "type": "sql",
  "sqlConfig": {
    "databaseId": "{{DATABASE_ID}}",
    "query": "SELECT d.project_no AS \"重大项目\", COALESCE(d.subsystem, '(无子项目)') AS \"子项目\", d.node_task AS \"任务\", d.node_type AS \"类型\", to_char(d.plan_date, 'YYYY-MM-DD') AS \"计划日期\", to_char(d.actual_date, 'YYYY-MM-DD') AS \"实际日期\", to_char(d.original_plan_date, 'YYYY-MM-DD') AS \"基线日期\", d.is_completed AS \"是否完成\", d.is_overdue_completed AS \"是否超期完成\", d.is_incomplete AS \"是否未完成\", COALESCE(d.delay_days, 0) AS \"延期天数\", d.risk_level AS \"风险等级\", d.completion_status AS \"完成情况\", d.dept AS \"责任科室\", d.owner AS \"责任人\", d.project_manager AS \"项目经理\", d.institute_leader AS \"所领导\", d.risk_content AS \"风险内容\", d.delay_impact AS \"延期影响\" FROM biz_dwd_project_node_v2 d WHERE d.plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM') AND d.plan_month <= to_char({{dateTo}}::date, 'YYYY-MM') AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR d.project_no ILIKE '%' || {{projectNo}} || '%') AND ({{deptId}} IS NULL OR {{deptId}} = '' OR d.dept = {{deptId}}) AND ({{riskLevel}} IS NULL OR {{riskLevel}} = '' OR d.risk_level = {{riskLevel}}) ORDER BY d.project_no, d.subsystem NULLS LAST, d.plan_date",
    "queryTimeoutSeconds": 30,
    "maxRows": 5000
  }
}
```

注意：必须**完整删除**原有的 `tasks` 字段（19 个硬编码 task 对象）。

- [ ] **Step 3: 验证 JSON 仍合法**

```bash
python -m json.tool worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-execution-board-v2.json > /dev/null && echo OK
```

- [ ] **Step 4: 验证 SQL 语法（在测试环境如有可用的 PostgreSQL）**

```bash
# 可选：把 SQL 模板里的 {{...}} 占位符替换为示例值后，psql -c 验证语法
```

- [ ] **Step 5: Commit**

```bash
git add worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-execution-board-v2.json
git commit -m "feat(S9/F6-T01): gpmc-execution-gantt 改为 SQL 驱动 + board-hierarchical 模式"
```

---

## F7: 文档与索引

### Task F7-T01: 更新 sprint-queue.md

**Files:**
- Modify: `worklog/v2.2.3/sprint-queue.md`

- [ ] **Step 1: 在 sprint-queue.md 末尾追加 Sprint-9 条目**

```markdown

## Sprint-9: GPMC 大屏跳转修复 + 甘特图弹层下钻 (202604)
**状态**: READY
**类型**: Implementation（实施型）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-跳转引擎"无目标=不跳"修复 | 5 | READY |
| F2-编辑器 ScreenJumpPicker UI 修复 | 4 | READY |
| F3-JSON 实例死链清理 | 1 | READY |
| F4-父项目汇总甘特组件改造 | 4 | READY |
| F5-ProjectDetailGanttModal 弹层组件 | 2 | READY |
| F6-gpmc-execution-gantt JSON 改造 | 1 | READY |
| F7-文档与索引 | 1 | READY |

**统计**: READY=18, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-9-202604/README.md`
```

- [ ] **Step 2: Commit**

```bash
git add worklog/v2.2.3/sprint-queue.md
git commit -m "docs(S9/F7-T01): sprint-queue.md 追加 Sprint-9 条目"
```

---

## 实施顺序建议

为保证每次 commit 都可独立编译通过，按以下顺序：

1. **F3-T01**（JSON 死链清理）— 与代码无依赖，可独立提交
2. **F4-T01**（types.ts 类型扩展）— 后续多个 task 依赖
3. **F1-T01 / T02**（引擎 sentinel 改造）— 修核心引擎
4. **F1-T03**（引擎单测）— 验证 sentinel 行为
5. **F1-T04**（hook 实现）— 不影响现有逻辑
6. **F1-T05**（renderer 接入 hook）— 启用视觉禁用
7. **F2-T01 ~ T04**（编辑器 UI）— 修编辑器侧
8. **F4-T02 / T03**（aggregator + 单测）— 准备 transformer
9. **F4-T04**（ProjectGanttBoard hierarchical）— 准备渲染
10. **F5-T01**（弹层组件）— 准备 UI
11. **F5-T02**（EChartsRenderer 接入）— 串起来
12. **F6-T01**（JSON 改 SQL）— 切换数据源
13. **F7-T01**（文档索引）— 收尾

---

## 验证 Checklist（实施完成后逐项验证）

- [ ] `pnpm tsc --noEmit` 无新增 error
- [ ] `pnpm vitest run majorProjectAggregator` 通过
- [ ] `pnpm vitest run InteractionLayer.cancelOnEmpty` 通过
- [ ] `pnpm vitest run` 全量回归（无新失败）
- [ ] `pnpm lint` 无新增 warning/error
- [ ] `python -m json.tool` 验证 5 个 v2 大屏 JSON 仍合法
- [ ] `grep -c '/bi/gpmc/drill/' worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-{strategic-overview,execution-board,quality-board,risk-board,tech-state-board}-v2.json` 全为 0
- [ ] `grep -c '|%2Fbi%2Fgpmc' worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-{strategic-overview,execution-board,quality-board,risk-board,tech-state-board}-v2.json` 全为 0
- [ ] 启动 webapp 进入大屏编辑器，打开 ScreenJumpPicker 下拉，验证暗色主题与列表项渲染正确
- [ ] 在 preview 模式打开 5 个 GPMC v2 大屏，所有点击均不会跳到 `/bi/gpmc/...` 类首页
- [ ] 部署后，进入执行层大屏看到甘特图显示父项目汇总条
- [ ] 点击父项目 → 弹出居中模态，1440×820
- [ ] 模态左栏元信息 / KPI / 风险列表正确显示
- [ ] 模态右栏甘特正确显示该父项目所有子项目任务
- [ ] 点击模态左栏风险项 → 右栏对应任务条高亮
- [ ] ESC / 蒙层 / X 按钮三种关闭方式都生效
- [ ] 在 Chrome 95 浏览器（或 user-agent override）验证 backdrop-filter 与动效正常
