# Analytics Styling Unification Design

## Goal

Unify the visual appearance of analytics module pages (CRUD, list, detail) with the platform-webapp standard. After this work, analytics pages should be visually indistinguishable from platform pages in terms of layout structure, table styling, button patterns, and notification feedback.

## Scope

**In scope:** ~24 analytics page files under `src/analytics/pages/` (excluding `screens/` subdirectory and `Public*` pages).

**Out of scope:**
- Screen designer pages (`screens/ScreenDesignerPage`, `ScreenPreviewPage`, `ScreenExportPage`, `PropertyPanel`, `ScreenHeader`, renderers, etc.) — highly customized full-screen editor with distinct styling needs
- Public share pages (`PublicCardPage`, `PublicDashboardPage`, `PublicScreenPage`) — standalone layouts without sidebar
- Analytics `styles.css` Tailwind layer and chart CSS — unrelated to page-level consistency

## Design Decisions

### 1. PageContainer/PageHeader Replacement

**Decision:** Replace analytics `PageContainer`/`PageHeader` with platform equivalents.

**Current state:**
- Analytics uses `PageContainer` (from `@/analytics/components/PageContainer/PageContainer.tsx`) which provides `maxWidth`, `padding`, `PageHeader` (with breadcrumb support), `PageSection`, `EmptyState`
- Platform uses `PageHeader` (from `@/components/page-header.tsx`) — simpler, Tailwind-based

**Target state:**
- Page top-level wrapper: `<div className="space-y-4">` (matches platform pattern)
- Page header: `<PageHeader>` from `@/components/page-header.tsx`
- `EmptyState` from `@/components/empty-state.tsx` replaces analytics `EmptyState`
- Analytics `Breadcrumb` component can remain where needed (platform pages also use antd `Breadcrumb` directly)

**Impact:** All ~24 in-scope analytics pages import `PageContainer`/`PageHeader`. Each needs its import and JSX updated.

### 2. Table Wrapper Pattern

**Decision:** All antd Tables wrapped in `<Card>`, search/filter inside Card above Table.

**Current analytics pattern:**
```tsx
<div className="bg-surface-card border border-border-default rounded-lg overflow-hidden">
  <div className="flex items-center justify-between px-4 py-3 border-b border-border-default">
    <span>N 条</span>
    <Input ... />
  </div>
  <Table ... />
</div>
```

**Target platform pattern:**
```tsx
<Card>
  <Space className="mb-4">
    <Input.Search placeholder="搜索..." style={{ width: 300 }} allowClear />
    <Button>重置</Button>
  </Space>
  <Table ... />
</Card>
```

**Pages with custom table wrappers to convert:**
- CardsPage, DashboardsPage, CollectionsPage (custom div wrapper → Card)
- MetricsPage (raw HTML `<table>` → antd `<Table>` in Card)
- ModelsPage list view (raw HTML `<table>` → antd `<Table>` in Card)

### 3. Action Button Style

**Decision:** Table action columns use `type="link"` (matching platform).

**Current:** `<Button type="text" size="small">` (gray text, hover to reveal)
**Target:** `<Button type="link" size="small">` (blue link text, more discoverable)

**Affected pages:** CardsPage, DashboardsPage, CollectionsPage, and any other page with table action columns.

### 4. Notification Feedback

**Decision:** Three-tier notification strategy, eliminate all `alert()`.

| Scenario | Method | Import |
|----------|--------|--------|
| Operation feedback (CRUD success/failure) | `toast.success()` / `toast.error()` | `import { toast } from "sonner"` |
| Light tips (copy success, etc.) | `message.success()` / `message.info()` | `import { message } from "antd"` |
| Never | `alert()` | — |

**Current `alert()` locations (~60+ calls):**
- `ScreensPage.tsx` (~15 calls) — AI generation, copy, share, template, delete
- `ScreenHeader.tsx` (~30 calls) — publish, rollback, export, import, version compare
- `PropertyPanel.tsx` (~20 calls) — copy style, paste config, tab rules
- `DataPage.tsx` (1 call)
- `ScreenExportPage.tsx` (2 calls)
- `ScreenDesignerPage.tsx` (1 call)
- `ScreenSnapshotPanel.tsx` (2 calls)
- `ScreenSharePolicyPanel.tsx` (1 call)
- `ScreenVersionComparePanel.tsx` (1 call)

**Note:** Screen designer files (ScreenHeader, PropertyPanel, ScreenDesignerPage, etc.) are out of scope for layout changes but their `alert()` calls SHOULD be replaced as part of notification unification since it's a cross-cutting concern.

### 5. CSS Token Consolidation

**Decision:** Merge analytics tokens into global.css references; deprecate redundant utilities.

**Phase A (this work):**
- In `analytics/styles/tokens.css`: identify variables that duplicate `global.css` variables and make analytics reference the global ones (e.g., `--surface-card` already exists in both)
- In `analytics/styles/utilities.css`: add deprecation comments to classes that Tailwind already provides (e.g., `.flex`, `.gap-md`, `.text-sm`)
- In page files: replace inline `<style>` tags with Tailwind classes
- In page files: replace inline `style={{...}}` objects with Tailwind classes where straightforward

**Phase B (Sprint-21 Tailwind full unification):**
- Remove deprecated utility classes
- Complete Tailwind migration for remaining edge cases

**Pages with inline `<style>` tags to eliminate:**
- CardEditorPage.tsx (form-grid media query)
- CollectionItemsPage.tsx (collection-item styles)
- FieldDetailPage.tsx (field-detail styles)
- HomePage.tsx (quick-action-card, welcome-banner)
- ModelsPage.tsx (model-card styles)

## Affected Files Summary

### Analytics pages (layout + component changes)

| File | Changes |
|------|---------|
| CardsPage.tsx | PageHeader, Card wrapper, button type="link", search in Card |
| DashboardsPage.tsx | PageHeader, Card wrapper, button type="link", search in Card |
| CollectionsPage.tsx | PageHeader, Card wrapper, button type="link", search in Card |
| ModelsPage.tsx | PageHeader, raw table → antd Table, Card wrapper |
| DataPage.tsx | PageHeader, inline style → Tailwind |
| MetricsPage.tsx | PageHeader, raw table → antd Table, Card wrapper |
| HomePage.tsx | PageHeader, inline `<style>` → Tailwind |
| CardDetailPage.tsx | PageHeader, inline style → Tailwind |
| CardEditorPage.tsx | PageHeader, inline `<style>` → Tailwind |
| DashboardDetailPage.tsx | PageHeader |
| DashboardEditorPage.tsx | PageHeader |
| DatabaseDetailPage.tsx | PageHeader, inline style → Tailwind |
| DatabaseEditPage.tsx | PageHeader |
| DatabaseNewPage.tsx | PageHeader, inline style → Tailwind |
| CollectionItemsPage.tsx | PageHeader, inline `<style>` → Tailwind |
| FieldDetailPage.tsx | PageHeader, inline `<style>` → Tailwind |
| TableDetailPage.tsx | PageHeader |
| TrashPage.tsx | PageHeader |
| SearchPage.tsx | PageHeader |
| ExploreSessionsPage.tsx | PageHeader, alert() → toast |
| MetricLensPage.tsx | PageHeader |
| Nl2SqlEvalPage.tsx | PageHeader |
| ReportFactoryPage.tsx | PageHeader |
| NotFoundPage.tsx | PageHeader |

### Screen designer pages (alert() replacement only)

| File | Changes |
|------|---------|
| ScreensPage.tsx | alert() → toast/message |
| ScreenHeader.tsx | alert() → toast/message |
| PropertyPanel.tsx | alert() → toast/message |
| ScreenDesignerPage.tsx | alert() → toast/message |
| ScreenExportPage.tsx | alert() → toast/message |
| ScreenSnapshotPanel.tsx | alert() → toast/message |
| ScreenSharePolicyPanel.tsx | alert() → message |
| ScreenVersionComparePanel.tsx | alert() → message |

### CSS files

| File | Changes |
|------|---------|
| analytics/styles/tokens.css | Deduplicate with global.css |
| analytics/styles/utilities.css | Mark Tailwind-covered classes as deprecated |

## Non-Goals

- Changing the analytics design token color values (they already align with platform)
- Refactoring the analytics API layer or data fetching patterns
- Changing routing or navigation behavior (addressed in separate hash-routing fix)
- Modifying screen designer UI/UX
- Modifying public share page layouts
