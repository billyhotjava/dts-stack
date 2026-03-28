# Screen Import/Export Enhancement — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance screen editor and template gallery import/export with Base64 resource inlining, import preview modal, and consolidated UI menu.

**Architecture:** Two utility modules (resourceInliner, resourceRestorer) handle Base64 conversion. A shared ImportPreviewModal provides preview + action selection. ScreenHeader gets a new "导入导出" HeaderMenu consolidating all import/export. TemplateGallery import/export enhanced with resource inlining and preview modal. No backend changes — Base64 data URLs render natively in browsers; no upload API needed for MVP.

**Tech Stack:** React 18, TypeScript, existing Modal UI component, existing analyticsApi client.

**Spec:** `worklog/v2.2.2/sprint-18-202603/features/F1-导入导出增强/README.md`

---

## File Map

### New Files

| File | Responsibility |
|------|---------------|
| `pages/screens/utils/resourceInliner.ts` | Export: scan spec for image URLs, fetch & convert to Base64 |
| `pages/screens/utils/resourceRestorer.ts` | Import: count Base64 data URLs (MVP: no upload, keep as-is) |
| `pages/screens/components/ImportPreviewModal.tsx` | Shared import preview modal with action selection |

### Modified Files

| File | Change |
|------|--------|
| `pages/screens/components/ScreenHeader.tsx` | Add "导入导出" HeaderMenu, refactor import flow, use resourceInliner for JSON export |
| `pages/screens/components/TemplateGallery.tsx` | Enhance import with ImportPreviewModal, enhance export with resourceInliner |

All paths relative to `source/dts-analytics-webapp/modern/src/`.

---

## Task 1: resourceInliner — Export Resource Inlining

**Files:**
- Create: `pages/screens/utils/resourceInliner.ts`

- [ ] **Step 1: Create the utils directory and resourceInliner.ts**

```typescript
// pages/screens/utils/resourceInliner.ts

/**
 * Scans a screen spec object for internal image URLs and converts them to
 * inline Base64 data URIs. External URLs and already-inlined data: URIs are skipped.
 */

const IMAGE_URL_PATTERN = /\.(png|jpe?g|gif|svg|webp)(\?.*)?$/i;
const INTERNAL_URL_PATTERN = /^(\/analytics\/|\/api\/|\/?uploads\/)/;

function isInternalImageUrl(value: unknown): value is string {
	if (typeof value !== 'string' || !value) return false;
	if (value.startsWith('data:')) return false;
	if (!INTERNAL_URL_PATTERN.test(value) && !IMAGE_URL_PATTERN.test(value)) return false;
	if (value.startsWith('http://') || value.startsWith('https://')) return false;
	return true;
}

async function fetchAsDataUrl(url: string): Promise<string> {
	const response = await fetch(url);
	if (!response.ok) throw new Error(`Failed to fetch ${url}: ${response.status}`);
	const blob = await response.blob();
	return new Promise<string>((resolve, reject) => {
		const reader = new FileReader();
		reader.onloadend = () => resolve(reader.result as string);
		reader.onerror = () => reject(new Error(`Failed to read blob for ${url}`));
		reader.readAsDataURL(blob);
	});
}

export async function inlineResources(spec: Record<string, unknown>): Promise<{
	spec: Record<string, unknown>;
	inlinedCount: number;
	errors: string[];
}> {
	const result = JSON.parse(JSON.stringify(spec)) as Record<string, unknown>;
	const errors: string[] = [];
	let inlinedCount = 0;

	// Collect all image URL paths to inline
	const urlEntries: Array<{ obj: Record<string, unknown>; key: string; url: string }> = [];

	function scan(obj: unknown): void {
		if (!obj || typeof obj !== 'object') return;
		if (Array.isArray(obj)) {
			for (const item of obj) scan(item);
			return;
		}
		const record = obj as Record<string, unknown>;
		for (const key of Object.keys(record)) {
			const value = record[key];
			if (isInternalImageUrl(value)) {
				urlEntries.push({ obj: record, key, url: value });
			} else if (value && typeof value === 'object') {
				scan(value);
			}
		}
	}

	scan(result);

	// Fetch and inline each URL
	for (const entry of urlEntries) {
		try {
			const dataUrl = await fetchAsDataUrl(entry.url);
			entry.obj[entry.key] = dataUrl;
			inlinedCount++;
		} catch (e) {
			const msg = e instanceof Error ? e.message : String(e);
			errors.push(`${entry.url}: ${msg}`);
			console.warn(`[resourceInliner] Failed to inline ${entry.url}:`, e);
		}
	}

	return { spec: result, inlinedCount, errors };
}
```

- [ ] **Step 2: Verify compiles**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp && npx tsc -p modern/tsconfig.json --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/utils/resourceInliner.ts
git commit -m "feat(F1/T01): add resourceInliner for Base64 image inlining on export

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: resourceRestorer — Import Resource Counter

**Files:**
- Create: `pages/screens/utils/resourceRestorer.ts`

Since no image upload API exists, this MVP implementation only counts and identifies Base64 resources. The data URLs are kept as-is — browsers render them natively. A future upload-based restoration can be added later.

- [ ] **Step 1: Create resourceRestorer.ts**

```typescript
// pages/screens/utils/resourceRestorer.ts

/**
 * Scans a spec for inline Base64 data URLs.
 * MVP: does NOT upload/restore — just counts them for the preview modal.
 * Browsers render data: URLs natively, so the spec works as-is.
 */

export function countInlinedResources(spec: unknown): number {
	let count = 0;
	function scan(obj: unknown): void {
		if (!obj || typeof obj !== 'object') return;
		if (Array.isArray(obj)) {
			for (const item of obj) scan(item);
			return;
		}
		const record = obj as Record<string, unknown>;
		for (const key of Object.keys(record)) {
			const value = record[key];
			if (typeof value === 'string' && value.startsWith('data:image/')) {
				count++;
			} else if (value && typeof value === 'object') {
				scan(value);
			}
		}
	}
	scan(spec);
	return count;
}
```

- [ ] **Step 2: Verify compiles**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp && npx tsc -p modern/tsconfig.json --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/utils/resourceRestorer.ts
git commit -m "feat(F1/T02): add countInlinedResources for import preview

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: ImportPreviewModal

**Files:**
- Create: `pages/screens/components/ImportPreviewModal.tsx`

- [ ] **Step 1: Create ImportPreviewModal.tsx**

The component uses the existing `Modal` from `../../../ui/Modal/Modal`. Read `ScreenHeader.tsx` line 29 for the import pattern: `import { Modal } from '../../../ui/Modal/Modal';`.

Read `types.ts` for the `ScreenConfig` type (line 263+): has `name`, `width`, `height`, `components[]`, `theme`, `backgroundImage`.

```tsx
// pages/screens/components/ImportPreviewModal.tsx
import { useState } from 'react';
import { Modal } from '../../../ui/Modal/Modal';
import type { ScreenConfig } from '../types';

type ImportAction = 'replace' | 'create-screen' | 'register-template';

type TemplateMeta = {
	name: string;
	description?: string;
	category?: string;
	tags?: string[];
};

export type ImportPreviewModalProps = {
	isOpen: boolean;
	onClose: () => void;
	fileName: string;
	parsedSpec: ScreenConfig;
	templateMeta?: TemplateMeta;
	validation: { errors: string[]; warnings: string[] };
	resourcesInlined: boolean;
	inlinedResourceCount: number;
	mode: 'editor' | 'marketplace';
	onConfirm: (action: ImportAction) => void;
};

export function ImportPreviewModal({
	isOpen,
	onClose,
	fileName,
	parsedSpec,
	templateMeta,
	validation,
	resourcesInlined,
	inlinedResourceCount,
	mode,
	onConfirm,
}: ImportPreviewModalProps) {
	const [selectedAction, setSelectedAction] = useState<ImportAction>(
		mode === 'editor' ? 'replace' : 'create-screen'
	);
	const hasErrors = validation.errors.length > 0;

	const actions: Array<{ value: ImportAction; label: string; description: string }> =
		mode === 'editor'
			? [
				{ value: 'replace', label: '替换当前大屏', description: '用导入内容替换当前编辑中的大屏配置' },
				{ value: 'create-screen', label: '创建为新大屏', description: '保留当前大屏，将导入内容创建为新大屏' },
			]
			: [
				{ value: 'register-template', label: '注册为模板', description: '将导入内容添加到模板库中' },
				{ value: 'create-screen', label: '创建为大屏', description: '直接用导入内容创建一个新大屏' },
			];

	const componentCount = parsedSpec.components?.length ?? 0;
	const pageCount = parsedSpec.pages?.length ?? 0;

	return (
		<Modal
			isOpen={isOpen}
			onClose={onClose}
			title="导入预览"
			size="md"
			footer={
				<div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
					<button type="button" className="header-btn" onClick={onClose}>取消</button>
					<button
						type="button"
						className="header-btn save-btn"
						disabled={hasErrors}
						onClick={() => onConfirm(selectedAction)}
					>
						确认导入
					</button>
				</div>
			}
		>
			<div style={{ display: 'grid', gap: 16 }}>
				{/* File name */}
				<div style={{ fontSize: 13, color: 'var(--color-text-secondary, #6b7280)' }}>
					文件: {fileName}
				</div>

				{/* Basic info */}
				<div style={{ padding: '12px 16px', background: 'var(--color-bg-secondary, #f8f9fa)', borderRadius: 8 }}>
					<div style={{ fontWeight: 600, marginBottom: 8 }}>基本信息</div>
					<div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px 24px', fontSize: 13 }}>
						<div>名称: <strong>{templateMeta?.name || parsedSpec.name || '未命名'}</strong></div>
						<div>尺寸: <strong>{parsedSpec.width ?? '?'} × {parsedSpec.height ?? '?'}</strong></div>
						<div>组件数: <strong>{componentCount}</strong>{pageCount > 1 ? ` (${pageCount} 页)` : ''}</div>
						<div>主题: <strong>{parsedSpec.theme || '默认'}</strong></div>
						{resourcesInlined && (
							<div>资源内联: <strong>是 ({inlinedResourceCount} 张图片)</strong></div>
						)}
						{templateMeta?.category && (
							<div>分类: <strong>{templateMeta.category}</strong></div>
						)}
					</div>
					{templateMeta?.tags && templateMeta.tags.length > 0 && (
						<div style={{ marginTop: 8, fontSize: 12 }}>
							标签: {templateMeta.tags.map((tag) => (
								<span key={tag} style={{ display: 'inline-block', padding: '1px 8px', marginRight: 4, background: 'var(--color-bg-tertiary, #e5e7eb)', borderRadius: 4 }}>{tag}</span>
							))}
						</div>
					)}
				</div>

				{/* Validation */}
				<div style={{ padding: '12px 16px', background: hasErrors ? 'rgba(239,68,68,0.06)' : 'rgba(16,185,129,0.06)', borderRadius: 8, border: hasErrors ? '1px solid rgba(239,68,68,0.2)' : '1px solid rgba(16,185,129,0.2)' }}>
					<div style={{ fontWeight: 600, marginBottom: 4 }}>校验结果</div>
					{hasErrors ? (
						<div style={{ color: '#b91c1c', fontSize: 13 }}>
							{validation.errors.map((e, i) => <div key={i}>✗ {e}</div>)}
						</div>
					) : (
						<div style={{ color: '#047857', fontSize: 13 }}>✓ 配置格式合法</div>
					)}
					{validation.warnings.length > 0 && (
						<div style={{ color: '#92400e', fontSize: 13, marginTop: 4 }}>
							{validation.warnings.map((w, i) => <div key={i}>⚠ {w}</div>)}
						</div>
					)}
				</div>

				{/* Action selection */}
				{!hasErrors && (
					<div>
						<div style={{ fontWeight: 600, marginBottom: 8 }}>导入方式</div>
						<div style={{ display: 'grid', gap: 8 }}>
							{actions.map((action) => (
								<label
									key={action.value}
									style={{
										display: 'flex',
										alignItems: 'flex-start',
										gap: 8,
										padding: '10px 14px',
										borderRadius: 8,
										border: selectedAction === action.value
											? '2px solid var(--color-primary, #3B82F6)'
											: '1px solid var(--color-border, #e0e0e0)',
										cursor: 'pointer',
										background: selectedAction === action.value ? 'rgba(59,130,246,0.04)' : undefined,
									}}
								>
									<input
										type="radio"
										name="import-action"
										value={action.value}
										checked={selectedAction === action.value}
										onChange={() => setSelectedAction(action.value)}
										style={{ marginTop: 2 }}
									/>
									<div>
										<div style={{ fontWeight: 500 }}>{action.label}</div>
										<div style={{ fontSize: 12, color: 'var(--color-text-secondary, #6b7280)' }}>{action.description}</div>
									</div>
								</label>
							))}
						</div>
					</div>
				)}
			</div>
		</Modal>
	);
}
```

- [ ] **Step 2: Verify compiles**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp && npx tsc -p modern/tsconfig.json --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/ImportPreviewModal.tsx
git commit -m "feat(F1/T03): add ImportPreviewModal for import preview and action selection

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: ScreenHeader — Add "导入导出" Menu & Refactor Export

**Files:**
- Modify: `pages/screens/components/ScreenHeader.tsx`

This task adds a new HeaderMenu and moves export functionality. Task 5 handles the import flow.

- [ ] **Step 1: Add resourceInliner import**

At the top of ScreenHeader.tsx, after existing imports (around line 30), add:

```typescript
import { inlineResources } from '../utils/resourceInliner';
```

- [ ] **Step 2: Add the "导入导出" HeaderMenu**

Find the comment `{/* --- 4. 版本导出 (kept as-is) --- */}` (around line 2083). Insert a NEW HeaderMenu BEFORE it:

```tsx
                        {/* --- 导入导出 --- */}
                        <HeaderMenu
                            label="导入导出"
                            open={activeMenu === 'tools-io'}
                            onToggle={() => setActiveMenu((prev) => (prev === 'tools-io' ? null : 'tools-io'))}
                        >
                            <div className="header-menu-section">
                                <div className="header-menu-section-title">导入</div>
                                <button type="button" className="header-btn" onClick={() => executeMenuAction(handleOpenImport)} title="从 JSON 文件导入大屏配置">选择 JSON 文件...</button>
                            </div>
                            <div className="header-menu-section">
                                <div className="header-menu-section-title">导出</div>
                                <label className="header-menu-inline-label" htmlFor="screen-io-device-mode">预览设备</label>
                                <select id="screen-io-device-mode" className="header-device-select" value={previewDeviceMode} onChange={(e) => { const next = e.target.value; if (next === 'pc' || next === 'tablet' || next === 'mobile') { setPreviewDeviceMode(next); return; } setPreviewDeviceMode('auto'); }} title="预览设备模式">
                                    <option value="auto">自动</option>
                                    <option value="pc">PC</option>
                                    <option value="tablet">平板</option>
                                    <option value="mobile">手机</option>
                                </select>
                                <button type="button" className="header-btn" onClick={() => executeMenuAction(handleExportJson)} title="导出 JSON（含内联资源）">导出 JSON</button>
                                <button type="button" className="header-btn" onClick={() => executeMenuAction(handleExportPng)} disabled={!id} title="导出 PNG 图片">导出 PNG</button>
                                <button type="button" className="header-btn" onClick={() => executeMenuAction(handleExportPdf)} disabled={!id} title="导出 PDF 文档">导出 PDF</button>
                            </div>
                        </HeaderMenu>
```

- [ ] **Step 3: Strip export items from "版本导出" menu**

In the "版本导出" HeaderMenu (around line 2084-2118):
- Change label from `"版本导出"` to `"版本"`
- Change toggle key from `'tools-release'` to `'tools-version'`
- Remove the export section (lines with `导出动作` label, export format select, and execute export button — approximately lines 2110-2116)
- Keep only the version history/rollback section and the preview device select

- [ ] **Step 4: Remove "导入JSON" from "编辑" menu**

In the "编辑" HeaderMenu "更多" section (around line 2057), remove this line:
```tsx
<button type="button" className="header-btn" onClick={() => executeMenuAction(handleOpenImport)} title="导入JSON配置">导入JSON</button>
```

- [ ] **Step 5: Enhance handleExportJson to inline resources**

Find `handleExportJson` (around line 1238). Modify the payload building to use `inlineResources`:

Replace the existing payload construction:
```typescript
const payload = {
    schema: 'dts.screen.spec',
    exportedAt: new Date().toISOString(),
    screenSpec: buildScreenPayload(persistedConfig),
};
```

With:
```typescript
const rawSpec = buildScreenPayload(persistedConfig) as Record<string, unknown>;
const { spec: inlinedSpec, inlinedCount, errors: inlineErrors } = await inlineResources(rawSpec);
if (inlineErrors.length > 0) {
    console.warn('[export] Resource inlining warnings:', inlineErrors);
}
const payload = {
    schema: 'dts.screen.spec',
    exportedAt: new Date().toISOString(),
    resourcesInlined: inlinedCount > 0,
    screenSpec: inlinedSpec,
};
```

- [ ] **Step 6: Verify compiles**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp && npx tsc -p modern/tsconfig.json --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 7: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHeader.tsx
git commit -m "feat(F1/T04): add consolidated import/export menu, inline resources on JSON export

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: ScreenHeader — Import Flow Enhancement

**Files:**
- Modify: `pages/screens/components/ScreenHeader.tsx`

- [ ] **Step 1: Add imports for ImportPreviewModal and countInlinedResources**

At the top of ScreenHeader.tsx, add:

```typescript
import { ImportPreviewModal, type ImportPreviewModalProps } from './ImportPreviewModal';
import { countInlinedResources } from '../utils/resourceRestorer';
```

- [ ] **Step 2: Add import modal state**

After the existing state declarations (around line 280), add:

```typescript
const [importPreview, setImportPreview] = useState<{
    fileName: string;
    parsedSpec: ScreenConfig;
    templateMeta?: { name: string; description?: string; category?: string; tags?: string[] };
    validation: { errors: string[]; warnings: string[] };
    resourcesInlined: boolean;
    inlinedResourceCount: number;
} | null>(null);
```

- [ ] **Step 3: Rewrite handleImportJson**

Replace the existing `handleImportJson` (around line 1513-1541) with:

```typescript
const handleImportJson = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;

    try {
        const content = await file.text();
        const parsed = JSON.parse(content) as Record<string, unknown>;
        const source = (parsed.screenSpec || parsed) as Record<string, unknown>;
        const templateMeta = parsed.templateMeta as { name: string; description?: string; category?: string; tags?: string[] } | undefined;
        const normalized = normalizeScreenConfig(source, { id: id || '' });
        if (normalized.warnings.length > 0) {
            console.warn('[screen-import] normalized warnings:', normalized.warnings);
        }
        const validation = validateScreenPayload(buildScreenPayload(normalized.config));
        const resourcesInlined = parsed.resourcesInlined === true;
        const inlinedResourceCount = resourcesInlined ? countInlinedResources(source) : 0;

        setImportPreview({
            fileName: file.name,
            parsedSpec: normalized.config,
            templateMeta: templateMeta || undefined,
            validation,
            resourcesInlined,
            inlinedResourceCount,
        });
    } catch (error) {
        console.error('Failed to parse import file:', error);
        alert('JSON 导入失败，请检查文件格式');
    }
};
```

- [ ] **Step 4: Add import confirm handler**

After the rewritten `handleImportJson`, add:

```typescript
const handleImportConfirm = async (action: 'replace' | 'create-screen' | 'register-template') => {
    if (!importPreview) return;
    const { parsedSpec } = importPreview;

    try {
        if (action === 'replace') {
            loadConfig(materializeScreenPage(parsedSpec, currentPageIndex));
            setImportPreview(null);
        } else if (action === 'create-screen') {
            const spec = buildScreenPayload(parsedSpec);
            const created = await analyticsApi.createScreen(spec);
            setImportPreview(null);
            navigate(`/screens/${String(created.id)}/edit`);
        }
    } catch (error) {
        console.error('Import action failed:', error);
        alert(error instanceof Error ? error.message : '导入操作失败');
    }
};
```

- [ ] **Step 5: Render ImportPreviewModal**

Find the hidden file input for import (around line 2175-2181):
```tsx
<input ref={importInputRef} type="file" accept="application/json,.json" style={{ display: 'none' }} onChange={handleImportJson} />
```

After it, add:

```tsx
{importPreview && (
    <ImportPreviewModal
        isOpen={!!importPreview}
        onClose={() => setImportPreview(null)}
        fileName={importPreview.fileName}
        parsedSpec={importPreview.parsedSpec}
        templateMeta={importPreview.templateMeta}
        validation={importPreview.validation}
        resourcesInlined={importPreview.resourcesInlined}
        inlinedResourceCount={importPreview.inlinedResourceCount}
        mode="editor"
        onConfirm={handleImportConfirm}
    />
)}
```

- [ ] **Step 6: Add navigate import**

Check if `useNavigate` is already imported. If not, add to the react-router import at the top of the file. The `navigate` function should already be available from the ScreenHeader component's props or hooks — read the file to confirm. If it's passed via props, use `props.navigate` or equivalent. If not available, the `onCreateNewScreen` action can use `window.location.href = '/analytics/screens/${id}/edit'` as a fallback.

- [ ] **Step 7: Verify compiles**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp && npx tsc -p modern/tsconfig.json --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 8: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHeader.tsx
git commit -m "feat(F1/T05): enhance import flow with preview modal and action selection

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: TemplateGallery — Export Enhancement

**Files:**
- Modify: `pages/screens/components/TemplateGallery.tsx`

- [ ] **Step 1: Add resourceInliner import**

At the top of TemplateGallery.tsx, after existing imports:

```typescript
import { inlineResources } from '../utils/resourceInliner';
```

- [ ] **Step 2: Make handleExportTemplate async and add resource inlining**

Find `handleExportTemplate` (around line 407). It currently calls `asTemplatePackage(selectedSelection)` synchronously and downloads. Make it async and add inlining:

Replace the function (lines 407-428 approximately) with:

```typescript
const handleExportTemplate = async () => {
    if (!selectedSelection) {
        setActionNotice({
            tone: 'error',
            title: '请先选择模板',
            message: '需要先选中一个内置模板或资产模板，才能执行导出。',
        });
        return;
    }
    try {
        const pack = asTemplatePackage(selectedSelection);
        const rawSpec = (pack.template as Record<string, unknown>)?.config as Record<string, unknown> | undefined;
        let resourcesInlined = false;
        if (rawSpec && typeof rawSpec === 'object') {
            const { spec: inlinedSpec, inlinedCount, errors: inlineErrors } = await inlineResources(rawSpec);
            if (inlineErrors.length > 0) {
                console.warn('[template-export] Resource inlining warnings:', inlineErrors);
            }
            if (inlinedCount > 0) {
                (pack.template as Record<string, unknown>).config = inlinedSpec;
                resourcesInlined = true;
            }
        }
        (pack as Record<string, unknown>).resourcesInlined = resourcesInlined;

        const name = selectedSelection.kind === 'builtin'
            ? selectedSelection.template.name
            : (selectedSelection.template.name || `template-${String(selectedSelection.template.id)}`);
        const blob = new Blob([JSON.stringify(pack, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${name}-template.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        setActionNotice({ tone: 'success', title: '模板包已导出', message: `${name}-template.json` });
    } catch (err) {
        console.error('Failed to export template:', err);
        setActionNotice({ tone: 'error', title: '导出失败', message: err instanceof Error ? err.message : '未知错误' });
    }
};
```

- [ ] **Step 3: Verify compiles**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp && npx tsc -p modern/tsconfig.json --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/TemplateGallery.tsx
git commit -m "feat(F1/T06): enhance template export with Base64 resource inlining

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: TemplateGallery — Import Enhancement

**Files:**
- Modify: `pages/screens/components/TemplateGallery.tsx`

- [ ] **Step 1: Add imports**

```typescript
import { ImportPreviewModal } from './ImportPreviewModal';
import { countInlinedResources } from '../utils/resourceRestorer';
import { normalizeScreenConfig, validateScreenPayload, buildScreenPayload } from '../specV2';
```

Check which of these are already imported and only add the missing ones.

- [ ] **Step 2: Add import preview state**

After existing state declarations (around line 175-185), add:

```typescript
const [importPreview, setImportPreview] = useState<{
    fileName: string;
    parsedSpec: ScreenConfig;
    rawPayload: Record<string, unknown>;
    templateMeta?: { name: string; description?: string; category?: string; tags?: string[] };
    validation: { errors: string[]; warnings: string[] };
    resourcesInlined: boolean;
    inlinedResourceCount: number;
} | null>(null);
```

- [ ] **Step 3: Modify handleImport for template mode to show preview**

Find the `handleImport` function (around line 327). In the template branch (the `else` branch after the `if (importMode === 'industry')` block, around line 363-404), replace the direct `createScreenTemplate` call with opening the preview modal:

Replace the template import logic (from `const rawTemplate =` to the catch block) with:

```typescript
// Template import — show preview modal
const rawTemplate = (payload.template || payload) as Record<string, unknown>;
const rawConfig = (rawTemplate.config || payload.config || payload) as Record<string, unknown>;
const templateMeta = {
    name: typeof rawTemplate.name === 'string' ? rawTemplate.name : file.name.replace(/\.[^.]+$/, ''),
    description: typeof rawTemplate.description === 'string' ? rawTemplate.description : undefined,
    category: typeof rawTemplate.category === 'string' ? rawTemplate.category : 'custom',
    tags: Array.isArray(rawTemplate.tags) ? rawTemplate.tags as string[] : undefined,
};

const normalized = normalizeScreenConfig(rawConfig, {});
const validation = validateScreenPayload(buildScreenPayload(normalized.config));
const resourcesInlined = payload.resourcesInlined === true;
const inlinedResourceCount = resourcesInlined ? countInlinedResources(rawConfig) : 0;

setImportPreview({
    fileName: file.name,
    parsedSpec: normalized.config,
    rawPayload: payload,
    templateMeta,
    validation,
    resourcesInlined,
    inlinedResourceCount,
});
```

- [ ] **Step 4: Add import confirm handler**

After the `handleExportTemplate` function, add:

```typescript
const handleImportConfirm = async (action: 'replace' | 'create-screen' | 'register-template') => {
    if (!importPreview) return;
    const { parsedSpec, rawPayload, templateMeta } = importPreview;

    try {
        if (action === 'register-template') {
            const rawTemplate = (rawPayload.template || rawPayload) as Record<string, unknown>;
            const body = {
                name: templateMeta?.name || '导入模板',
                description: templateMeta?.description || '',
                category: templateMeta?.category || 'custom',
                thumbnail: typeof rawTemplate.thumbnail === 'string' ? rawTemplate.thumbnail : '📦',
                tags: templateMeta?.tags || [],
                visibilityScope: typeof rawTemplate.visibilityScope === 'string' ? rawTemplate.visibilityScope : 'team',
                listed: typeof rawTemplate.listed === 'boolean' ? rawTemplate.listed : true,
                config: buildScreenPayload(parsedSpec),
            };
            const created = await analyticsApi.createScreenTemplate(body);
            await loadAssetTemplates();
            setSelectedKey(`asset:${String(created.id)}`);
            setActionNotice({
                tone: 'success',
                title: '模板导入成功',
                message: `模板已注册，ID=${String(created.id)}`,
            });
        } else if (action === 'create-screen') {
            const spec = buildScreenPayload(parsedSpec);
            const created = await analyticsApi.createScreen(spec);
            // Navigate to new screen edit page
            window.location.href = `/analytics/screens/${String(created.id)}/edit`;
            return;
        }
        setImportPreview(null);
    } catch (err) {
        console.error('Import action failed:', err);
        setActionNotice({
            tone: 'error',
            title: '导入操作失败',
            message: err instanceof Error ? err.message : '未知错误',
        });
        setImportPreview(null);
    }
};
```

- [ ] **Step 5: Render ImportPreviewModal**

Find the hidden file input in TemplateGallery (around line 882-888). After it, add:

```tsx
{importPreview && (
    <ImportPreviewModal
        isOpen={!!importPreview}
        onClose={() => setImportPreview(null)}
        fileName={importPreview.fileName}
        parsedSpec={importPreview.parsedSpec}
        templateMeta={importPreview.templateMeta}
        validation={importPreview.validation}
        resourcesInlined={importPreview.resourcesInlined}
        inlinedResourceCount={importPreview.inlinedResourceCount}
        mode="marketplace"
        onConfirm={handleImportConfirm}
    />
)}
```

- [ ] **Step 6: Verify compiles**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp && npx tsc -p modern/tsconfig.json --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 7: Build verification**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern && npm run build 2>&1 | tail -5`
Expected: build succeeds

- [ ] **Step 8: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/TemplateGallery.tsx
git commit -m "feat(F1/T07): enhance template import with preview modal and action selection

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```
