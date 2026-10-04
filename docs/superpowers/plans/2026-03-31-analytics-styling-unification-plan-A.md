# Analytics Styling Unification — Plan A: Page Layout & Component Unification

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace analytics PageContainer/PageHeader with platform equivalents, unify Table wrappers to antd Card, and standardize action button styles across ~24 analytics pages.

**Architecture:** Each analytics page currently imports `PageContainer`/`PageHeader` from `@/analytics/components/PageContainer/PageContainer`. We replace these with: (1) `<div className="space-y-4">` as outer wrapper, (2) platform `PageHeader` from `@/components/page-header`, (3) antd `Breadcrumb` for pages that need breadcrumbs, (4) antd `Card` for table wrappers, (5) `type="link"` for action buttons.

**Tech Stack:** React 19, antd 5, Tailwind CSS, TypeScript

**Spec:** `docs/superpowers/specs/2026-03-31-analytics-styling-unification-design.md`

---

## Key Transformation Patterns

Before reading the tasks, understand these patterns that recur across all pages:

### Pattern 1: Import Swap

**Before:**
```tsx
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
// or with Breadcrumb/EmptyState:
import { PageContainer, PageHeader, Breadcrumb, EmptyState } from "../components/PageContainer/PageContainer";
```

**After:**
```tsx
import { PageHeader } from "@/components/page-header";
// If page uses breadcrumbs, add:
import { Breadcrumb } from "antd";
// If page uses EmptyState, add:
import { EmptyState } from "@/components/empty-state";
```

### Pattern 2: PageContainer → div

**Before:**
```tsx
<PageContainer>
  <PageHeader title="..." actions={...} />
  {/* content */}
</PageContainer>
```

**After:**
```tsx
<div className="space-y-4">
  <PageHeader title="..." actions={...} />
  {/* content */}
</div>
```

For `<PageContainer maxWidth="full">`, use `<div className="space-y-4">` (the dashboard layout already constrains width).

### Pattern 3: Analytics Breadcrumb → antd Breadcrumb

**Before (analytics Breadcrumb component):**
```tsx
<PageHeader
  title="..."
  breadcrumbs={
    <Breadcrumb items={[
      { label: "数据", href: "/bi/data" },
      { label: "数据库 #1" },
    ]} />
  }
/>
```

**After (antd Breadcrumb + platform PageHeader):**
```tsx
<Breadcrumb items={[
  { title: <Link to="/bi/data">数据</Link> },
  { title: "数据库 #1" },
]} />
<PageHeader title="..." actions={...} />
```

Note: analytics Breadcrumb used `{ label, href }`. antd Breadcrumb uses `{ title }` where title can be a `<Link>` element. Convert `href` values to `<Link to={href}>label</Link>`.

### Pattern 4: Custom Table Wrapper → Card

**Before:**
```tsx
<div className="bg-surface-card border border-border-default rounded-lg overflow-hidden">
  <div className="flex items-center justify-between px-4 py-3 border-b border-border-default">
    <span className="text-sm text-text-secondary">N 条</span>
    <Input placeholder="搜索" ... />
  </div>
  <Table ... />
</div>
```

**After:**
```tsx
<Card>
  <Space className="mb-4">
    <Input.Search placeholder="搜索..." style={{ width: 300 }} allowClear ... />
  </Space>
  <Table ... />
</Card>
```

### Pattern 5: Button Type

**Before:** `<Button type="text" size="small" icon={...}>编辑</Button>`
**After:** `<Button type="link" size="small" icon={...}>编辑</Button>`

### Pattern 6: Analytics EmptyState → Platform EmptyState

**Before (analytics):**
```tsx
import { EmptyState } from "../components/PageContainer/PageContainer";
<EmptyState icon={<SomeIcon />} title="..." description="..." action={<Button>新增</Button>} />
```

**After (platform):**
```tsx
import { EmptyState } from "@/components/empty-state";
<EmptyState title="..." description="..." actions={<Button>新增</Button>} />
```

Note: platform EmptyState uses `actions` (plural) not `action`. Its `icon` prop accepts a string (icon name like `"lucide:inbox"`), not ReactNode. For pages where the analytics EmptyState passes a ReactNode icon, drop the `icon` prop (platform default is `"lucide:inbox"`).

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | 24 analytics page files | Layout & component swap |
| No change | `src/components/page-header.tsx` | Already correct |
| No change | `src/components/empty-state.tsx` | Already correct |
| No change | `src/analytics/components/PageContainer/PageContainer.tsx` | Keep for now (screen pages still use it) |

---

### Task 1: Fix CardsPage and DashboardsPage (Table + Custom Wrapper)

These two pages have identical structure: custom div table wrapper, type="text" action buttons, analytics PageContainer/PageHeader.

**Files:**
- Modify: `src/analytics/pages/CardsPage.tsx`
- Modify: `src/analytics/pages/DashboardsPage.tsx`

- [ ] **Step 1: Fix CardsPage.tsx**

Read the file. Apply these changes:

1. **Replace import** (line ~4-5):
   - Remove: `import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";`
   - Remove: `import { EmptyState } from "../components/EmptyState";` (if present, not used — verify)
   - Add: `import { PageHeader } from "@/components/page-header";`
   - Ensure `Card` is in the antd import

2. **Replace PageContainer wrapper**: Change `<PageContainer>` → `<div className="space-y-4">` and `</PageContainer>` → `</div>`

3. **Replace custom table wrapper div**: The `<div className="bg-surface-card border border-border-default rounded-lg overflow-hidden">` with its inner header bar → `<Card>` with search inside:
   ```tsx
   <Card>
     <Space className="mb-4">
       <Input.Search
         placeholder={t(locale, "common.search")}
         value={searchQuery}
         onChange={(e) => setSearchQuery(e.target.value)}
         allowClear
         style={{ width: 300 }}
       />
     </Space>
     <Table ... />
   </Card>
   ```
   Ensure `Space` is in the antd import.

4. **Change action buttons**: In the columns definition, change all `type="text"` to `type="link"` in the action column buttons.

- [ ] **Step 2: Fix DashboardsPage.tsx**

Same transformation as CardsPage. Read the file and apply identical changes:
1. Replace import
2. Replace PageContainer → div
3. Replace custom table wrapper → Card with search
4. Change action button types

- [ ] **Step 3: Verify both pages compile**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit 2>&1 | grep -E "CardsPage|DashboardsPage" || echo "CLEAN"`
Expected: CLEAN

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/CardsPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/DashboardsPage.tsx
git commit -m "refactor(analytics): unify CardsPage & DashboardsPage with platform layout"
```

---

### Task 2: Fix CollectionsPage (Two Table Sections)

**Files:**
- Modify: `src/analytics/pages/CollectionsPage.tsx`

- [ ] **Step 1: Apply transformations**

1. Replace import (Pattern 1)
2. Replace `<PageContainer>` → `<div className="space-y-4">`
3. This page has TWO table sections (cards table + dashboards table), each wrapped in a custom div. Convert each to `<Card>`:

   ```tsx
   <Card title={<span className="text-base font-semibold">{t(locale, "questions.title")}</span>}
         extra={
           <Space>
             <Input.Search value={cardSearch} onChange={...} allowClear size="small" style={{ width: 200 }} />
             <Link to="/bi/questions/new">
               <Button type="primary" size="small" icon={<PlusOutlined />}>{t(locale, "questions.new")}</Button>
             </Link>
           </Space>
         }>
     <Table ... />
   </Card>
   ```

   Repeat for the dashboards table section.

4. Change any `type="text"` buttons to `type="link"` (if present in columns).

- [ ] **Step 2: Verify and commit**

Run: `npx tsc --noEmit 2>&1 | grep "CollectionsPage" || echo "CLEAN"`

```bash
git add source/dts-platform-webapp/src/analytics/pages/CollectionsPage.tsx
git commit -m "refactor(analytics): unify CollectionsPage with platform layout"
```

---

### Task 3: Fix Simple PageContainer-Only Pages (9 pages)

These pages only need import swap and PageContainer → div. No table wrapper or button changes needed.

**Files:**
- Modify: `src/analytics/pages/DatabaseEditPage.tsx`
- Modify: `src/analytics/pages/DashboardDetailPage.tsx`
- Modify: `src/analytics/pages/DashboardEditorPage.tsx`
- Modify: `src/analytics/pages/TableDetailPage.tsx`
- Modify: `src/analytics/pages/TrashPage.tsx`
- Modify: `src/analytics/pages/SearchPage.tsx`
- Modify: `src/analytics/pages/MetricLensPage.tsx`
- Modify: `src/analytics/pages/Nl2SqlEvalPage.tsx`
- Modify: `src/analytics/pages/ReportFactoryPage.tsx`

- [ ] **Step 1: Fix each file**

For each file:
1. Replace import: `PageContainer, PageHeader` from analytics → `PageHeader` from `@/components/page-header`
2. If import includes `Breadcrumb`: add `import { Breadcrumb } from "antd"` and convert breadcrumb items from `{ label, href }` to `{ title: <Link to={href}>{label}</Link> }` (use Pattern 3)
3. If import includes `EmptyState`: add `import { EmptyState } from "@/components/empty-state"` and change `action=` to `actions=`, drop ReactNode `icon` prop
4. Replace `<PageContainer>` / `<PageContainer maxWidth="full">` → `<div className="space-y-4">`
5. If breadcrumbs are used, extract them from PageHeader's `breadcrumbs` prop into a standalone `<Breadcrumb>` component above `<PageHeader>`

**Important notes per file:**
- `DatabaseEditPage.tsx`: Has Breadcrumb import, uses `breadcrumbs` prop on PageHeader
- `DashboardDetailPage.tsx`: Uses `maxWidth="full"` on PageContainer — just use `<div className="space-y-4">`
- `DashboardEditorPage.tsx`: Only imports PageContainer (no PageHeader). Replace wrapper only.
- `TableDetailPage.tsx`: Has Breadcrumb, EmptyState
- `TrashPage.tsx`: Simple, no breadcrumbs
- `SearchPage.tsx`: Has EmptyState
- `MetricLensPage.tsx`, `Nl2SqlEvalPage.tsx`, `ReportFactoryPage.tsx`: Simple, no breadcrumbs

- [ ] **Step 2: Verify all compile**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/DatabaseEditPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/DashboardDetailPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/DashboardEditorPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/TableDetailPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/TrashPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/SearchPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/MetricLensPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/Nl2SqlEvalPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/ReportFactoryPage.tsx
git commit -m "refactor(analytics): unify 9 simple pages with platform layout"
```

---

### Task 4: Fix Breadcrumb Pages (Detail/Editor Pages)

These pages use the analytics `Breadcrumb` component within `PageHeader`'s `breadcrumbs` prop. Need to convert to antd `Breadcrumb` as a standalone element.

**Files:**
- Modify: `src/analytics/pages/CardDetailPage.tsx`
- Modify: `src/analytics/pages/CardEditorPage.tsx`
- Modify: `src/analytics/pages/DatabaseDetailPage.tsx`
- Modify: `src/analytics/pages/DatabaseNewPage.tsx`
- Modify: `src/analytics/pages/FieldDetailPage.tsx`
- Modify: `src/analytics/pages/CollectionItemsPage.tsx`

- [ ] **Step 1: Fix each file**

For each file:
1. Replace imports (Pattern 1). Add `Breadcrumb` from antd and `Link` from react-router if not already imported.
2. Replace `<PageContainer>` → `<div className="space-y-4">`
3. Convert analytics `<Breadcrumb items={[...]} />` inside PageHeader's `breadcrumbs` prop to standalone antd `<Breadcrumb>` above `<PageHeader>`:

   **Before:**
   ```tsx
   <PageHeader
     title="字段详情"
     breadcrumbs={
       <Breadcrumb items={[
         { label: t(locale, "data.title"), href: "/bi/data" },
         { label: `数据库 #${dbId}`, href: `/bi/data/${dbId}` },
         { label: "字段" },
       ]} />
     }
   />
   ```

   **After:**
   ```tsx
   <Breadcrumb items={[
     { title: <Link to="/bi/data">{t(locale, "data.title")}</Link> },
     { title: <Link to={`/bi/data/${dbId}`}>{`数据库 #${dbId}`}</Link> },
     { title: "字段" },
   ]} />
   <PageHeader title="字段详情" />
   ```

4. If page has EmptyState from analytics, convert to platform EmptyState (Pattern 6).
5. If page has inline `<style>` tags (CollectionItemsPage, FieldDetailPage, CardEditorPage), leave them for Plan C (CSS consolidation). Only change layout components in this task.

**Specific notes:**
- `CollectionItemsPage.tsx`: Has inline `<style>` for `.collection-items` — leave for Plan C
- `FieldDetailPage.tsx`: Has inline `<style>` for `.field-detail-*` — leave for Plan C
- `CardEditorPage.tsx`: Has inline `<style>` for `.form-grid` media query — leave for Plan C
- `DatabaseNewPage.tsx`: Complex page with tabs, heavy inline styles — only change PageContainer/PageHeader/Breadcrumb

- [ ] **Step 2: Verify all compile**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/CardDetailPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/CardEditorPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/DatabaseDetailPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/DatabaseNewPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/FieldDetailPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/CollectionItemsPage.tsx
git commit -m "refactor(analytics): unify breadcrumb pages with platform layout"
```

---

### Task 5: Fix ModelsPage (Raw HTML Table → antd Table)

ModelsPage has a grid view (keep as-is) and a list view that uses a raw HTML `<table>`. Convert the list view to antd `<Table>`.

**Files:**
- Modify: `src/analytics/pages/ModelsPage.tsx`

- [ ] **Step 1: Read the file and understand the list view structure**

The list view (around line 203-240) renders a raw `<table>` inside a `<Card>`:
```tsx
<Card styles={{ body: { padding: 0 } }}>
  <table>
    <thead><tr><th>Name</th><th>Type</th><th>ID</th></tr></thead>
    <tbody>
      {filteredModels.map(c => <tr>...</tr>)}
    </tbody>
  </table>
</Card>
```

- [ ] **Step 2: Replace with antd Table**

1. Replace import (Pattern 1). Add `Table` to antd imports. Add `import type { ColumnsType } from "antd/es/table";`.
2. Define columns:
   ```tsx
   const modelColumns: ColumnsType<CardListItem> = [
     {
       title: t(locale, "common.name"),
       dataIndex: "name",
       key: "name",
       render: (name: string, record) => (
         <Link to={`/bi/questions/${record.id}`} className="text-brand hover:underline font-medium">
           {name || t(locale, "common.untitled")}
           {record.description && (
             <span className="text-text-secondary ml-2 font-normal text-sm">{record.description}</span>
           )}
         </Link>
       ),
     },
     {
       title: t(locale, "common.type"),
       dataIndex: "display",
       key: "display",
       width: 100,
       render: (display: string) => display ? <Tag>{display}</Tag> : "-",
     },
     {
       title: t(locale, "common.id"),
       dataIndex: "id",
       key: "id",
       width: 80,
       render: (id: number) => <span className="text-text-muted">{id}</span>,
     },
   ];
   ```
3. Replace the raw `<table>` JSX with:
   ```tsx
   <Card>
     <Table
       rowKey={(record) => String(record.id)}
       columns={modelColumns}
       dataSource={filteredModels}
       pagination={false}
       size="small"
     />
   </Card>
   ```
4. Replace `<PageContainer>` → `<div className="space-y-4">`, convert PageHeader.
5. Convert analytics EmptyState → platform EmptyState (Pattern 6).

- [ ] **Step 3: Verify and commit**

Run: `npx tsc --noEmit 2>&1 | grep "ModelsPage" || echo "CLEAN"`

```bash
git add source/dts-platform-webapp/src/analytics/pages/ModelsPage.tsx
git commit -m "refactor(analytics): convert ModelsPage list view to antd Table"
```

---

### Task 6: Fix MetricsPage (Raw HTML Table → antd Table)

MetricsPage uses raw HTML `<table>` tags inside antd Cards. Convert to antd Table.

**Files:**
- Modify: `src/analytics/pages/MetricsPage.tsx`

- [ ] **Step 1: Read the full file**

Understand the current table structure. MetricsPage typically has a metrics summary section and a details table.

- [ ] **Step 2: Apply transformations**

1. Replace imports (Pattern 1).
2. Replace `<PageContainer>` → `<div className="space-y-4">`
3. For each raw `<table>` section:
   - Define `columns: ColumnsType<...>` matching the existing `<th>` headers
   - Replace `<table>...<tbody>{items.map(...)}</tbody></table>` with `<Table columns={...} dataSource={...} rowKey={...} pagination={false} />`
4. Keep the antd `Card` wrappers (they're already correct).
5. Convert EmptyState if used (Pattern 6).

- [ ] **Step 3: Verify and commit**

Run: `npx tsc --noEmit 2>&1 | grep "MetricsPage" || echo "CLEAN"`

```bash
git add source/dts-platform-webapp/src/analytics/pages/MetricsPage.tsx
git commit -m "refactor(analytics): convert MetricsPage to antd Table"
```

---

### Task 7: Fix HomePage (Special Layout)

HomePage doesn't use PageHeader. It has a custom welcome section, quick actions, and recent items tables.

**Files:**
- Modify: `src/analytics/pages/HomePage.tsx`

- [ ] **Step 1: Read the full file**

HomePage uses `<PageContainer>` as wrapper and has:
- Welcome banner
- Quick action cards
- Recent screens/questions/dashboards tables (already in antd Card+Table)

- [ ] **Step 2: Apply transformations**

1. Replace import: Remove `PageContainer` import from analytics, no need for platform PageHeader (HomePage has no header).
2. Replace `<PageContainer>` → `<div className="space-y-4">`
3. Tables are already in `<Card>` wrappers with antd `<Table>` — verify and leave as-is.
4. Inline `<style>` tag — leave for Plan C.

- [ ] **Step 3: Verify and commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/HomePage.tsx
git commit -m "refactor(analytics): replace PageContainer in HomePage"
```

---

### Task 8: Fix DataPage and ExploreSessionsPage

**Files:**
- Modify: `src/analytics/pages/DataPage.tsx`
- Modify: `src/analytics/pages/ExploreSessionsPage.tsx`

- [ ] **Step 1: Fix DataPage.tsx**

DataPage uses a grid of Cards (no table). Only needs:
1. Replace import (Pattern 1). Convert EmptyState (Pattern 6).
2. Replace `<PageContainer>` → `<div className="space-y-4">`
3. Keep the grid layout as-is (inline styles will be addressed in Plan C).

- [ ] **Step 2: Fix ExploreSessionsPage.tsx**

1. Replace import (Pattern 1).
2. Replace `<PageContainer>` → `<div className="space-y-4">`
3. Keep existing Card wrappers (already correct).

- [ ] **Step 3: Verify and commit**

Run: `npx tsc --noEmit`

```bash
git add source/dts-platform-webapp/src/analytics/pages/DataPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/ExploreSessionsPage.tsx
git commit -m "refactor(analytics): unify DataPage & ExploreSessionsPage layout"
```

---

### Task 9: Fix NotFoundPage

**Files:**
- Modify: `src/analytics/pages/NotFoundPage.tsx`

- [ ] **Step 1: Apply transformations**

1. Replace import: Remove `PageContainer` from analytics.
2. Replace `<PageContainer>` → `<div className="space-y-4">`
3. Keep Card wrapper (already correct).

- [ ] **Step 2: Verify and commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/NotFoundPage.tsx
git commit -m "refactor(analytics): replace PageContainer in NotFoundPage"
```

---

### Task 10: Final Verification

- [ ] **Step 1: Full TypeScript check**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 2: Verify no remaining analytics PageContainer/PageHeader imports in modified pages**

Run:
```bash
grep -rn "from.*PageContainer/PageContainer" source/dts-platform-webapp/src/analytics/pages/ \
  --include="*.tsx" \
  | grep -v "screens/"
```

Expected: Only screen designer files and Public* files should remain. The ~24 in-scope pages should NOT appear.

- [ ] **Step 3: Verify button types in table columns**

Run:
```bash
grep -rn 'type="text"' source/dts-platform-webapp/src/analytics/pages/CardsPage.tsx \
  source/dts-platform-webapp/src/analytics/pages/DashboardsPage.tsx \
  source/dts-platform-webapp/src/analytics/pages/CollectionsPage.tsx || echo "CLEAN"
```

Expected: CLEAN

- [ ] **Step 4: Commit if any stragglers fixed**

If verification reveals issues, fix them and create a final cleanup commit.
