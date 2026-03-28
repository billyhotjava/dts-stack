# Sprint-23: 核心页面体验优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize analytics-webapp core pages by removing redundant AnalyzePage, adding delete/restore capabilities to card/dashboard lists and trash, and renaming "集合" to "工作空间".

**Architecture:** Pure frontend changes across ~10 files. Backend DELETE/PUT archived APIs already exist. Add `deleteCard`/`deleteDashboard`/`updateDashboard` to the API client, then build UI features on top. Each feature (F1-F4) is independent and can be done in any order.

**Tech Stack:** React 19, TypeScript, antd v5, React Router v7

---

## File Structure

### Deleted Files
| File | Reason |
|------|--------|
| `src/pages/AnalyzePage.tsx` | F1: removed (redundant with HomePage) |

### Modified Files
| File | Changes |
|------|---------|
| `src/api/analyticsApi.ts` | Add deleteCard, deleteDashboard, updateDashboard methods |
| `src/layouts/AppLayout.tsx` | Remove /analyze + /collections/root menu items, rename 集合→工作空间 |
| `src/routes.tsx` | Remove /analyze route |
| `src/i18n.ts` | Update nav keys: remove analyze/myCollection, rename collections |
| `src/pages/CardsPage.tsx` | Add inline delete + batch select/delete |
| `src/pages/DashboardsPage.tsx` | Add inline delete + batch select/delete |
| `src/pages/TrashPage.tsx` | Add restore button + batch restore |
| `src/pages/CollectionsPage.tsx` | Title → "工作空间", root collection tag |
| `src/pages/PublicDashboardPage.tsx` | Fix /analyze breadcrumb |
| `src/pages/PublicCardPage.tsx` | Fix /analyze breadcrumb |

**Note:** All paths below are relative to `source/dts-analytics-webapp/modern/`. The working directory for commands is `/opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern`.

---

## Feature 1: 移除分析中心

### Task 1: Add API methods + Remove AnalyzePage + Update navigation

**Files:**
- Modify: `src/api/analyticsApi.ts`
- Delete: `src/pages/AnalyzePage.tsx`
- Modify: `src/routes.tsx`
- Modify: `src/layouts/AppLayout.tsx`
- Modify: `src/i18n.ts`
- Modify: `src/pages/PublicDashboardPage.tsx`
- Modify: `src/pages/PublicCardPage.tsx`

- [ ] **Step 1: Add deleteCard, deleteDashboard, updateDashboard to analyticsApi.ts**

Find the `updateCard` method (around line 1701) and add after it:

```typescript
deleteCard: (id: string | number) =>
  requestJson<void>(`/analytics/api/card/${encodeURIComponent(String(id))}`, "DELETE"),

deleteDashboard: (id: string | number) =>
  requestJson<void>(`/analytics/api/dashboard/${encodeURIComponent(String(id))}`, "DELETE"),

updateDashboard: (id: string | number, body: unknown) =>
  requestJson<DashboardDetail>(`/analytics/api/dashboard/${encodeURIComponent(String(id))}`, "PUT", body),
```

Follow the exact same pattern as the existing `updateCard` method (same URL format, same `requestJson` usage).

- [ ] **Step 2: Remove /analyze route from routes.tsx**

In `src/routes.tsx`, find and remove the line (around line 32):
```tsx
{ path: "/analyze", lazy: lazyComponent(() => import("./pages/AnalyzePage")) },
```

- [ ] **Step 3: Update AppLayout.tsx sidebar menu**

In `src/layouts/AppLayout.tsx`, find the `menuItems` array (around lines 180-217). Make these changes:

1. **Remove** the `/analyze` menu item:
```tsx
// Remove this line:
{ key: "/analyze", icon: <LineChartOutlined />, label: t(locale, "nav.analyze") },
```

2. **Remove** the `/collections/root` menu item:
```tsx
// Remove this line:
{ key: "/collections/root", icon: <FolderOutlined />, label: t(locale, "nav.myCollection") },
```

3. **Keep** the `/collections` menu item — only the i18n value changes (Step 4 handles this), no code change needed here:
```tsx
// This stays the same (the i18n key "nav.collections" value changes from "集合" to "工作空间"):
{ key: "/collections", icon: <FolderOutlined />, label: t(locale, "nav.collections") },
```

4. Remove `/analyze` and `/collections/root` from the `MENU_PATHS` array (used for route matching) and from `ROUTE_NAV_MAP` (used for breadcrumbs). The `/collections` entry in ROUTE_NAV_MAP stays — its label comes from `nav.collections` which we're updating to "工作空间".

- [ ] **Step 4: Update i18n.ts**

In the Chinese (zh-CN) section:
```typescript
// Remove:
"nav.analyze": "分析中心",
"nav.myCollection": "你的个人集合",
// Change:
"nav.collections": "工作空间",  // was "集合"
// Add:
"nav.workspace": "工作空间",
```

In the English section:
```typescript
// Remove:
"nav.analyze": "Analyze",
"nav.myCollection": "My collection",
// Change:
"nav.collections": "Workspace",  // was "Collections"
// Add:
"nav.workspace": "Workspace",
```

Also update workspace-related keys:
```typescript
// Chinese:
"collections.title": "工作空间",  // was "集合"
// English:
"collections.title": "Workspace",  // was "Collections"
```

- [ ] **Step 5: Fix /analyze breadcrumb in PublicDashboardPage.tsx and PublicCardPage.tsx**

In `src/pages/PublicDashboardPage.tsx` (around line 89), find the breadcrumb referencing `/analyze` and change it to `/`:
```tsx
// Before:
<Link to="/analyze">...</Link>
// After:
<Link to="/">...</Link>
```

Do the same in `src/pages/PublicCardPage.tsx` (around line 65).

- [ ] **Step 6: Delete AnalyzePage.tsx**

```bash
rm src/pages/AnalyzePage.tsx
```

- [ ] **Step 7: Update CollectionsPage.tsx title**

In `src/pages/CollectionsPage.tsx`, the title (around line 47) should already use the i18n key `collections.title` which we updated. Verify it says "工作空间".

Also add a Tag to the root collection item to mark it as "个人":
```tsx
// In the collection card rendering (around line 80), after the name:
import { Tag } from "antd";

// Where root collection name is rendered:
{c.id === "root" ? (
  <>{t(locale, "collections.rootName")} <Tag color="blue">个人</Tag></>
) : (c.name ?? "-")}
```

- [ ] **Step 8: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 9: Commit**

```bash
cd /opt/prod/s10/s10-stack
git add -A source/dts-analytics-webapp/modern/src/pages/AnalyzePage.tsx
git add source/dts-analytics-webapp/modern/src/
git commit -m "feat(F1/T01): remove AnalyzePage, add API methods, rename collections to workspace"
```

---

## Feature 2: 查询/仪表盘删除功能

### Task 2: CardsPage — 行内删除 + 批量删除

**Files:**
- Modify: `src/pages/CardsPage.tsx`

- [ ] **Step 1: Read current CardsPage.tsx and add selection state + delete logic**

Add these imports and state variables:

```tsx
import { Button, Input, Card, Spin, Skeleton, Tag, Checkbox, Dropdown, Modal, message } from "antd";
import { PlusOutlined, AppstoreOutlined, BarsOutlined, EllipsisOutlined, DeleteOutlined } from "@ant-design/icons";
```

Add state for selection:
```tsx
const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
```

Add handler functions:
```tsx
const toggleSelect = (id: number) => {
  setSelectedIds(prev => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
};

const toggleSelectAll = () => {
  if (selectedIds.size === filtered.length) {
    setSelectedIds(new Set());
  } else {
    setSelectedIds(new Set(filtered.map(c => c.id)));
  }
};

const handleDelete = async (id: number, name: string) => {
  Modal.confirm({
    title: "移至回收站",
    content: `确定将「${name}」移至回收站？`,
    okText: "确定",
    cancelText: "取消",
    okButtonProps: { danger: true },
    onOk: async () => {
      await analyticsApi.deleteCard(id);
      message.success("已移至回收站");
      setSelectedIds(prev => { const n = new Set(prev); n.delete(id); return n; });
      // Trigger refresh by re-fetching
      loadCards();
    },
  });
};

const handleBatchDelete = () => {
  if (selectedIds.size === 0) return;
  Modal.confirm({
    title: "批量移至回收站",
    content: `确定将 ${selectedIds.size} 项移至回收站？`,
    okText: "确定",
    cancelText: "取消",
    okButtonProps: { danger: true },
    onOk: async () => {
      const ids = Array.from(selectedIds);
      const results = await Promise.allSettled(ids.map(id => analyticsApi.deleteCard(id)));
      const failed = results.filter(r => r.status === "rejected").length;
      if (failed > 0) {
        message.warning(`${ids.length - failed} 项已移至回收站，${failed} 项失败`);
      } else {
        message.success(`${ids.length} 项已移至回收站`);
      }
      setSelectedIds(new Set());
      loadCards();
    },
  });
};
```

Note: `loadCards()` should be extracted from the existing `useEffect` data loading logic into a callable function (e.g., wrap the fetch logic into a function and call it from both useEffect and after delete).

- [ ] **Step 2: Add batch action bar to the header area**

Between the page header and the filter bar, add a conditional batch action bar:

```tsx
{selectedIds.size > 0 && (
  <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 0", background: "#f0f5ff", borderRadius: 6, paddingLeft: 12, paddingRight: 12, marginBottom: 8 }}>
    <Checkbox
      checked={selectedIds.size === filtered.length}
      indeterminate={selectedIds.size > 0 && selectedIds.size < filtered.length}
      onChange={toggleSelectAll}
    />
    <span>已选 {selectedIds.size} 项</span>
    <Button size="small" danger icon={<DeleteOutlined />} onClick={handleBatchDelete}>
      移至回收站
    </Button>
    <Button size="small" type="text" onClick={() => setSelectedIds(new Set())}>
      取消选择
    </Button>
  </div>
)}
```

- [ ] **Step 3: Add checkboxes and action menu to grid view items**

For each card in grid view, wrap it with a checkbox:

```tsx
// In the grid view card rendering:
<div key={card.id} style={{ position: "relative" }}>
  <Checkbox
    checked={selectedIds.has(card.id)}
    onChange={() => toggleSelect(card.id)}
    style={{ position: "absolute", top: 8, left: 8, zIndex: 1 }}
  />
  <Dropdown
    menu={{
      items: [
        { key: "delete", icon: <DeleteOutlined />, label: "移至回收站", danger: true,
          onClick: () => handleDelete(card.id, card.name ?? "") },
      ],
    }}
    trigger={["click"]}
  >
    <Button
      type="text" size="small" icon={<EllipsisOutlined />}
      style={{ position: "absolute", top: 8, right: 8, zIndex: 1 }}
      onClick={e => e.preventDefault()}
    />
  </Dropdown>
  {/* existing card content (Link wrapping the card) */}
</div>
```

- [ ] **Step 4: Add checkboxes and action menu to list view items**

For the list/table view, add a checkbox column at the beginning and an actions column at the end:

```tsx
// In the list view table:
<thead>
  <tr>
    <th style={{ width: 40 }}>
      <Checkbox
        checked={filtered.length > 0 && selectedIds.size === filtered.length}
        indeterminate={selectedIds.size > 0 && selectedIds.size < filtered.length}
        onChange={toggleSelectAll}
      />
    </th>
    <th>{t(locale, "cards.name")}</th>
    <th>{t(locale, "cards.type")}</th>
    <th>ID</th>
    <th style={{ width: 60 }}></th>
  </tr>
</thead>
<tbody>
  {filtered.map(card => (
    <tr key={card.id}>
      <td><Checkbox checked={selectedIds.has(card.id)} onChange={() => toggleSelect(card.id)} /></td>
      <td><Link to={`/questions/${card.id}`}>{card.name}</Link></td>
      <td>...</td>
      <td>{card.id}</td>
      <td>
        <Dropdown menu={{ items: [{ key: "delete", icon: <DeleteOutlined />, label: "移至回收站", danger: true, onClick: () => handleDelete(card.id, card.name ?? "") }] }} trigger={["click"]}>
          <Button type="text" size="small" icon={<EllipsisOutlined />} />
        </Dropdown>
      </td>
    </tr>
  ))}
</tbody>
```

- [ ] **Step 5: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 6: Commit**

```bash
cd /opt/prod/s10/s10-stack
git add source/dts-analytics-webapp/modern/src/pages/CardsPage.tsx
git commit -m "feat(F2/T02): add inline delete and batch delete to CardsPage"
```

---

### Task 3: DashboardsPage — 行内删除 + 批量删除

**Files:**
- Modify: `src/pages/DashboardsPage.tsx`

- [ ] **Step 1: Apply the same pattern as CardsPage**

The changes are identical in structure to Task 2, but using dashboard-specific API calls:

- `analyticsApi.deleteDashboard(id)` instead of `analyticsApi.deleteCard(id)`
- Links go to `/dashboards/${id}` instead of `/questions/${id}`
- Use the dashboard's `name` and `description` fields

Follow the exact same pattern:
1. Add `selectedIds` state
2. Add `toggleSelect`, `toggleSelectAll`, `handleDelete`, `handleBatchDelete` functions (using `deleteDashboard`)
3. Add batch action bar
4. Add checkboxes + action menu to both grid and list views
5. Extract data loading into a callable `loadDashboards()` function

- [ ] **Step 2: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 3: Commit**

```bash
cd /opt/prod/s10/s10-stack
git add source/dts-analytics-webapp/modern/src/pages/DashboardsPage.tsx
git commit -m "feat(F2/T03): add inline delete and batch delete to DashboardsPage"
```

---

## Feature 3: 回收站恢复功能

### Task 4: TrashPage — 单个恢复 + 批量恢复

**Files:**
- Modify: `src/pages/TrashPage.tsx`

- [ ] **Step 1: Read current TrashPage.tsx and add restore logic**

Add imports:
```tsx
import { Button, Spin, Tag, Checkbox, message } from "antd";
import { UndoOutlined } from "@ant-design/icons";
```

Add state:
```tsx
const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set()); // "card-1" or "dashboard-2" format
```

Add restore handlers:
```tsx
const restoreItem = async (model: string, id: number) => {
  if (model === "card") {
    await analyticsApi.updateCard(id, { archived: false });
  } else {
    await analyticsApi.updateDashboard(id, { archived: false });
  }
  message.success("已恢复");
  loadTrash(); // re-fetch trash
};

const handleBatchRestore = async () => {
  const items = Array.from(selectedIds).map(key => {
    const [model, idStr] = key.split("-");
    return { model, id: Number(idStr) };
  });
  const results = await Promise.allSettled(
    items.map(it =>
      it.model === "card"
        ? analyticsApi.updateCard(it.id, { archived: false })
        : analyticsApi.updateDashboard(it.id, { archived: false })
    )
  );
  const failed = results.filter(r => r.status === "rejected").length;
  if (failed > 0) {
    message.warning(`${items.length - failed} 项已恢复，${failed} 项失败`);
  } else {
    message.success(`${items.length} 项已恢复`);
  }
  setSelectedIds(new Set());
  loadTrash();
};
```

- [ ] **Step 2: Rewrite TrashPage UI with batch select + restore buttons**

```tsx
// Header area:
<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
  <h1>{t(locale, "nav.trash")}</h1>
  {selectedIds.size > 0 && (
    <Button icon={<UndoOutlined />} onClick={handleBatchRestore}>
      恢复选中项 ({selectedIds.size})
    </Button>
  )}
</div>

// Each trash item row:
{allItems.map(it => {
  const key = `${it.model}-${it.id}`;
  return (
    <div key={key} style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 12px", borderBottom: "1px solid #f0f0f0" }}>
      <Checkbox
        checked={selectedIds.has(key)}
        onChange={() => {
          setSelectedIds(prev => {
            const next = new Set(prev);
            if (next.has(key)) next.delete(key); else next.add(key);
            return next;
          });
        }}
      />
      <Tag color={it.model === "dashboard" ? "blue" : "green"}>
        {it.model === "dashboard" ? "仪表盘" : "查询"}
      </Tag>
      <span style={{ flex: 1 }}>{it.name ?? "-"}</span>
      {it.updated_at && (
        <span style={{ color: "#999", fontSize: 12 }}>
          {new Date(it.updated_at).toLocaleDateString()}
        </span>
      )}
      <Button
        type="link"
        size="small"
        icon={<UndoOutlined />}
        onClick={() => restoreItem(it.model, it.id)}
      >
        恢复
      </Button>
    </div>
  );
})}

// Empty state:
{allItems.length === 0 && !loading && (
  <div style={{ textAlign: "center", padding: 48, color: "#999" }}>
    回收站为空
  </div>
)}
```

- [ ] **Step 3: Add select-all checkbox**

Add a header row with select-all:
```tsx
{allItems.length > 0 && (
  <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 12px", borderBottom: "1px solid #f0f0f0", background: "#fafafa" }}>
    <Checkbox
      checked={allItems.length > 0 && selectedIds.size === allItems.length}
      indeterminate={selectedIds.size > 0 && selectedIds.size < allItems.length}
      onChange={() => {
        if (selectedIds.size === allItems.length) {
          setSelectedIds(new Set());
        } else {
          setSelectedIds(new Set(allItems.map(it => `${it.model}-${it.id}`)));
        }
      }}
    />
    <span style={{ fontWeight: 500, color: "#666" }}>全选</span>
  </div>
)}
```

- [ ] **Step 4: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 5: Commit**

```bash
cd /opt/prod/s10/s10-stack
git add source/dts-analytics-webapp/modern/src/pages/TrashPage.tsx
git commit -m "feat(F3/T04): add restore and batch restore to TrashPage"
```

---

## Final Verification

### Task 5: 全量构建验证

- [ ] **Step 1: TypeScript check**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 2: Production build**

```bash
npm run build
```

Expected: Build succeeds.

- [ ] **Step 3: Verify AnalyzePage is deleted**

```bash
ls src/pages/AnalyzePage.tsx 2>&1
```

Expected: "No such file or directory"

- [ ] **Step 4: Verify no /analyze references remain**

```bash
grep -r "/analyze" src/ --include="*.tsx" --include="*.ts" | grep -v node_modules
```

Expected: No matches (or only test/comment references).

- [ ] **Step 5: Commit if needed**

```bash
git commit -m "chore(sprint-23): final build verification passed"
```
