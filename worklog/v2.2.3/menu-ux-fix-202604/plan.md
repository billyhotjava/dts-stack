# 菜单管理 UX 优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复菜单管理页面 5 个 UX 问题：去掉名称 id 前缀、统计移入页头、删除路径列、操作按钮横排 + 自定义菜单折入下拉、警告降级。

**Architecture:** 全部改动集中在 `portal-menus.tsx` 一个文件。`MenuRow` 组件新增 `displayLabel` 变量与 `name`（路径 key）解耦；操作列引入 antd `Dropdown`；主组件页头新增 antd `Tag` 统计徽章，删除 3 个 Card 统计块。

**Tech Stack:** React, antd（Button/Dropdown/Tag 已在项目中），TypeScript

---

## 文件变更

| 文件 | 操作 |
|------|------|
| `source/dts-admin-webapp/src/admin/views/portal-menus.tsx` | 修改 |

---

### Task 1: 新增 Dropdown/Tag import，添加 displayLabel，去掉名称 id 前缀

**Files:**
- Modify: `source/dts-admin-webapp/src/admin/views/portal-menus.tsx`

- [ ] **Step 1: 更新 antd import（第 9 行）**

将：
```tsx
import { Button } from "antd";
```
改为：
```tsx
import { Button, Dropdown, Tag } from "antd";
import { MoreOutlined } from "@ant-design/icons";
```

- [ ] **Step 2: 在 MenuRow 函数体内添加 displayLabel，名称显示改用 displayLabel**

找到 `MenuRow` 函数体（约第 686 行）中：
```tsx
const baseName = item.displayName ?? item.name ?? String(id);
const name = id != null ? `id${id}-${baseName}` : baseName;
```
改为：
```tsx
const baseName = item.displayName ?? item.name ?? String(id);
const displayLabel = baseName;                                  // 纯净显示名，不含 id 前缀
const name = id != null ? `id${id}-${baseName}` : baseName;    // 仅用于 fullPath 路径 key
```

- [ ] **Step 3: 菜单名称单元格改用 displayLabel 并附 title tooltip**

找到：
```tsx
<div className="min-w-0 flex-1 truncate font-semibold">{highlightKeyword(name, keyword)}</div>
```
改为：
```tsx
<div className="min-w-0 flex-1 truncate font-semibold" title={fullPathLabel}>{highlightKeyword(displayLabel, keyword)}</div>
```

- [ ] **Step 4: 确认编译无报错**

```bash
cd /opt/prod/s10/s10-stack/source/dts-admin-webapp && npx tsc --noEmit 2>&1 | head -20
```
Expected: 无 TS 错误（或仅有无关旧错误）

- [ ] **Step 5: Commit**

```bash
cd /opt/prod/s10/s10-stack/source/dts-admin-webapp
git add src/admin/views/portal-menus.tsx
git commit -m "fix(admin/menus): remove id prefix from menu display name"
```

---

### Task 2: 删除统计卡片，改为页头行内 Tag 徽章

**Files:**
- Modify: `source/dts-admin-webapp/src/admin/views/portal-menus.tsx`

- [ ] **Step 1: 页头区域加入 Tag 统计**

找到（约第 418 行）：
```tsx
<div className="flex flex-wrap items-center justify-between gap-3">
    <Text variant="body1" className="text-lg font-semibold">
        菜单管理
    </Text>
    <div className="flex items-center gap-2">
```
改为：
```tsx
<div className="flex flex-wrap items-center justify-between gap-3">
    <div className="flex items-center gap-3">
        <Text variant="body1" className="text-lg font-semibold">
            菜单管理
        </Text>
        <div className="flex items-center gap-1.5">
            <Tag>共 {menuStats.total} 项</Tag>
            <Tag color="success">启用 {menuStats.active}</Tag>
            {menuStats.disabled > 0 && <Tag color="error">禁用 {menuStats.disabled}</Tag>}
        </div>
    </div>
    <div className="flex items-center gap-2">
```

- [ ] **Step 2: 删除 3 个统计 Card 块**

删除以下整个 `<div>` 块（约第 432–457 行）：
```tsx
<div className="grid gap-3 sm:grid-cols-3">
    <Card>
        <CardContent className="flex flex-col gap-1 px-4 py-3">
            <Text variant="body3" className="text-muted-foreground">
                菜单总数
            </Text>
            <span className="text-2xl font-semibold">{menuStats.total}</span>
        </CardContent>
    </Card>
    <Card>
        <CardContent className="flex flex-col gap-1 px-4 py-3">
            <Text variant="body3" className="text-muted-foreground">
                已启用
            </Text>
            <span className="text-2xl font-semibold text-emerald-600">{menuStats.active}</span>
        </CardContent>
    </Card>
    <Card>
        <CardContent className="flex flex-col gap-1 px-4 py-3">
            <Text variant="body3" className="text-muted-foreground">
                已禁用
            </Text>
            <span className="text-2xl font-semibold text-red-500">{menuStats.disabled}</span>
        </CardContent>
    </Card>
</div>
```

- [ ] **Step 3: 确认编译无报错**

```bash
cd /opt/prod/s10/s10-stack/source/dts-admin-webapp && npx tsc --noEmit 2>&1 | head -20
```

- [ ] **Step 4: Commit**

```bash
git add src/admin/views/portal-menus.tsx
git commit -m "fix(admin/menus): replace stat cards with inline header tags"
```

---

### Task 3: 删除完整路径列，行对齐改为 middle

**Files:**
- Modify: `source/dts-admin-webapp/src/admin/views/portal-menus.tsx`

- [ ] **Step 1: 删除表头"完整路径"列**

找到：
```tsx
<th className="px-3 py-2 font-medium">菜单名称</th>
<th className="px-3 py-2 font-medium">完整路径</th>
<th className="px-3 py-2 font-medium">状态</th>
```
改为：
```tsx
<th className="px-3 py-2 font-medium">菜单名称</th>
<th className="px-3 py-2 font-medium">状态</th>
```

- [ ] **Step 2: 删除 MenuRow 中路径 `<td>` 单元格**

找到并删除：
```tsx
<td className="px-3 py-2 align-top text-xs text-muted-foreground break-all">{fullPathLabel}</td>
```

- [ ] **Step 3: 将行对齐从 align-top 改为 align-middle**

找到：
```tsx
<tr className="border-b last:border-none align-top hover:bg-accent/5">
```
改为：
```tsx
<tr className="border-b last:border-none hover:bg-accent/5">
```

同时将各 `<td>` 中的 `align-top` class 删除（共 4 处）：
```tsx
// 改前
<td className="px-3 py-2 align-top">
// 改后
<td className="px-3 py-2">
```

- [ ] **Step 4: 确认编译无报错**

```bash
cd /opt/prod/s10/s10-stack/source/dts-admin-webapp && npx tsc --noEmit 2>&1 | head -20
```

- [ ] **Step 5: Commit**

```bash
git add src/admin/views/portal-menus.tsx
git commit -m "fix(admin/menus): remove full-path column, fix row vertical alignment"
```

---

### Task 4: 操作按钮改横排，自定义菜单折入 Dropdown

**Files:**
- Modify: `source/dts-admin-webapp/src/admin/views/portal-menus.tsx`

- [ ] **Step 1: 替换操作列按钮区域**

找到 MenuRow 中操作列（约第 764 行）：
```tsx
<div className="flex flex-wrap justify-end gap-2">
    <Button size="small" type="default" onClick={() => onToggle(item)} disabled={busy}>
        {isDeleted ? "启用菜单" : "禁用菜单"}
    </Button>
    <Button size="small" type="default" onClick={() => onEditRoles(item)} disabled={rolesLoading || busy}>
        配置角色
    </Button>
    {isCustom ? (
        <>
            <Button size="small" type="default" onClick={() => onEditCustom(item)} disabled={busy}>
                编辑
            </Button>
            <Button size="small" danger type="primary" onClick={() => onDeleteCustom(item)} disabled={busy}>
                删除
            </Button>
        </>
    ) : null}
</div>
```

改为：
```tsx
<div className="flex items-center justify-end gap-1 flex-nowrap">
    <Button size="small" type="default" onClick={() => onToggle(item)} disabled={busy}>
        {isDeleted ? "启用" : "禁用"}
    </Button>
    <Button size="small" type="default" onClick={() => onEditRoles(item)} disabled={rolesLoading || busy}>
        配置角色
    </Button>
    {isCustom ? (
        <Dropdown
            trigger={["click"]}
            disabled={busy}
            menu={{
                items: [
                    {
                        key: "edit",
                        label: "编辑",
                        onClick: () => onEditCustom(item),
                    },
                    {
                        key: "delete",
                        label: "删除",
                        danger: true,
                        onClick: () => onDeleteCustom(item),
                    },
                ],
            }}
        >
            <Button size="small" type="text" icon={<MoreOutlined />} disabled={busy} />
        </Dropdown>
    ) : null}
</div>
```

- [ ] **Step 2: 确认编译无报错**

```bash
cd /opt/prod/s10/s10-stack/source/dts-admin-webapp && npx tsc --noEmit 2>&1 | head -20
```

- [ ] **Step 3: Commit**

```bash
git add src/admin/views/portal-menus.tsx
git commit -m "fix(admin/menus): horizontal action buttons, custom menu actions in dropdown"
```

---

### Task 5: 警告文字降级为灰色提示

**Files:**
- Modify: `source/dts-admin-webapp/src/admin/views/portal-menus.tsx`

- [ ] **Step 1: 合并并降级提示文字**

找到 CardHeader 内两行提示（约第 483 行）：
```tsx
<Text variant="body3" className="text-muted-foreground">
    说明：父节点仅用于分组，不提供启用/禁用按钮；叶子节点可切换状态
</Text>
<Text variant="body3" color="warning" className="mt-1">
    提示：禁用此项会触发菜单管理审批，请谨慎处理
</Text>
```
改为（合并为一行灰色提示）：
```tsx
<Text variant="body3" className="text-muted-foreground">
    父节点仅用于分组；禁用叶子节点会触发菜单管理审批
</Text>
```

- [ ] **Step 2: 确认编译无报错**

```bash
cd /opt/prod/s10/s10-stack/source/dts-admin-webapp && npx tsc --noEmit 2>&1 | head -20
```

- [ ] **Step 3: Commit**

```bash
git add src/admin/views/portal-menus.tsx
git commit -m "fix(admin/menus): downgrade warning hint to muted text"
```

---

## 完成检查

全部 5 个 task 完成后，在浏览器打开菜单管理页面验证：

1. 菜单名称不再显示 `id1302-` 前缀，hover 时 tooltip 显示完整路径
2. 页头显示 `共 N 项 · 启用 N · 禁用 N` 徽章，3 个大卡片消失
3. 表格没有"完整路径"列，行高明显缩小
4. 操作按钮 `[禁用] [配置角色]` 横排在一行；自定义菜单多一个 `…` 下拉按钮
5. 黄色警告文字消失，只剩灰色提示
