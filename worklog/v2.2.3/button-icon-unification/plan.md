# 按钮图标统一 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在两套 webapp 的所有编辑/删除/详情操作按钮上统一加上 antd 图标（`EditOutlined`/`DeleteOutlined`/`EyeOutlined`）。

**Architecture:** 纯 UI 修改，只改 `icon={}` prop 和 `@ant-design/icons` import，不触碰按钮逻辑。`user-detail.tsx` 还需移除 Iconify child icon 改为 antd prop 方式。分两个 task：admin-webapp（8 文件）和 platform-webapp（13 文件），互相独立。

**Tech Stack:** React, TypeScript, antd Button, `@ant-design/icons`

---

## 文件变更

| 文件 | 操作 |
|------|------|
| `source/dts-admin-webapp/src/admin/views/role-management.tsx` | 修改 |
| `source/dts-admin-webapp/src/admin/views/user-management.tsx` | 修改 |
| `source/dts-admin-webapp/src/admin/views/user-detail.tsx` | 修改 |
| `source/dts-admin-webapp/src/admin/views/role-detail.tsx` | 修改 |
| `source/dts-admin-webapp/src/admin/views/my-changes.tsx` | 修改 |
| `source/dts-admin-webapp/src/admin/views/org-management.tsx` | 修改 |
| `source/dts-admin-webapp/src/admin/views/workflow-config.tsx` | 修改 |
| `source/dts-admin-webapp/src/admin/views/data-lake-config.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/modeling/ModelTemplatesPage.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/workbench/index.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/governance/TemplatesPage.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/services/DataProductsPage.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/catalog/AssetDetailPage.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/explore/etl/components/ExecutionHistoryTable.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/workbench/WorkflowCenterPage.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/governance/IndicatorsPage.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/governance/GlossaryPage.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/governance/ReferenceCodesPage.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/governance/ElementsPage.tsx` | 修改 |
| `source/dts-platform-webapp/src/pages/governance/AssetOwnershipPage.tsx` | 修改 |

---

### Task 1: Admin-webapp — 编辑/删除/详情按钮加图标（8 个文件）

**Files:**
- Modify: `source/dts-admin-webapp/src/admin/views/role-management.tsx`
- Modify: `source/dts-admin-webapp/src/admin/views/user-management.tsx`
- Modify: `source/dts-admin-webapp/src/admin/views/user-detail.tsx`
- Modify: `source/dts-admin-webapp/src/admin/views/role-detail.tsx`
- Modify: `source/dts-admin-webapp/src/admin/views/my-changes.tsx`
- Modify: `source/dts-admin-webapp/src/admin/views/org-management.tsx`
- Modify: `source/dts-admin-webapp/src/admin/views/workflow-config.tsx`
- Modify: `source/dts-admin-webapp/src/admin/views/data-lake-config.tsx`

**规则：**
- 只加 `icon={}` prop，不改任何其他属性（`type`、`size`、`danger`、`onClick`、按钮文字）
- 每个文件加一行 `import { ... } from "@ant-design/icons";`，只导入该文件实际需要的图标
- `user-detail.tsx` 例外：要移除 Iconify JSX 子组件，改为 antd `icon={}` prop

- [ ] **Step 1: 修改 role-management.tsx**

当前 import（文件第 3 行）：
```tsx
import { Button, Table } from "antd";
```
改为（在第 3 行之后新增一行）：
```tsx
import { Button, Table } from "antd";
import { EyeOutlined, EditOutlined, DeleteOutlined } from "@ant-design/icons";
```

找到操作列渲染（含"详情"/"编辑"/"删除"三个按钮）：
```tsx
<Link to={`/admin/roles/${roleSlug}`}><Button size="small" type="default">详情</Button></Link>
<Link to={`/admin/roles/${roleSlug}/edit`}><Button size="small" type="default">编辑</Button></Link>
<Button
    size="small"
    danger
    type="primary"
    disabled={immutable}
    onClick={() => setDeleteTarget(record)}
>
    删除
</Button>
```
改为：
```tsx
<Link to={`/admin/roles/${roleSlug}`}><Button size="small" type="default" icon={<EyeOutlined />}>详情</Button></Link>
<Link to={`/admin/roles/${roleSlug}/edit`}><Button size="small" type="default" icon={<EditOutlined />}>编辑</Button></Link>
<Button
    size="small"
    danger
    type="primary"
    icon={<DeleteOutlined />}
    disabled={immutable}
    onClick={() => setDeleteTarget(record)}
>
    删除
</Button>
```

- [ ] **Step 2: 修改 user-management.tsx**

在文件第 1 行 `import { Button, Table } from "antd";` 之后新增：
```tsx
import { EditOutlined, EyeOutlined } from "@ant-design/icons";
```

找到操作列两个按钮（"编辑"和"详情"）：
```tsx
<Button size="small" type="default" onClick={() => setModalState({ open: true, mode: "edit", target: toKeycloakUser(record) })}>
  编辑
</Button>
<Button
  size="small"
  type="text"
  onClick={() => {
    const id = record.keycloakId || record.username;
    if (!id) return;
    push(`/admin/users/${id}`);
  }}
>
  详情
</Button>
```
改为：
```tsx
<Button size="small" type="default" icon={<EditOutlined />} onClick={() => setModalState({ open: true, mode: "edit", target: toKeycloakUser(record) })}>
  编辑
</Button>
<Button
  size="small"
  type="text"
  icon={<EyeOutlined />}
  onClick={() => {
    const id = record.keycloakId || record.username;
    if (!id) return;
    push(`/admin/users/${id}`);
  }}
>
  详情
</Button>
```

- [ ] **Step 3: 修改 user-detail.tsx（移除 Iconify，改为 antd icon prop）**

在文件第 2 行 `import { Button, Table } from "antd";` 之后新增：
```tsx
import { EditOutlined } from "@ant-design/icons";
```

找到编辑按钮（使用了 Iconify `<Icon>` 子组件）：
```tsx
<Button type="primary" onClick={() => setEditModal(true)}>
    <Icon icon="solar:pen-new-square-broken" className="mr-1" /> 编辑
</Button>
```
改为（移除 Iconify 子元素，使用 antd icon prop）：
```tsx
<Button type="primary" icon={<EditOutlined />} onClick={() => setEditModal(true)}>
    编辑
</Button>
```

- [ ] **Step 4: 修改 role-detail.tsx**

在文件第 4 行 `import { Button, TreeSelect, Select as AntSelect } from "antd";` 之后新增：
```tsx
import { EditOutlined } from "@ant-design/icons";
```

找到"编辑角色"按钮：
```tsx
<Button type="primary" onClick={() => navigate(`/admin/roles/${encodeURIComponent(roleKey)}/edit`)}>
    编辑角色
</Button>
```
改为：
```tsx
<Button type="primary" icon={<EditOutlined />} onClick={() => navigate(`/admin/roles/${encodeURIComponent(roleKey)}/edit`)}>
    编辑角色
</Button>
```

- [ ] **Step 5: 修改 my-changes.tsx**

在文件第 4 行 `import { Button, Table } from "antd";` 之后新增：
```tsx
import { EyeOutlined } from "@ant-design/icons";
```

找到"查看详情"按钮：
```tsx
<Button type="text" size="small" onClick={() => handleOpenChange(record)}>
    查看详情
</Button>
```
改为：
```tsx
<Button type="text" size="small" icon={<EyeOutlined />} onClick={() => handleOpenChange(record)}>
    查看详情
</Button>
```

- [ ] **Step 6: 修改 org-management.tsx**

找到文件中 `import { Button } from "antd";` 所在行（约第 13 行）之后新增：
```tsx
import { EditOutlined } from "@ant-design/icons";
```

找到"编辑"按钮：
```tsx
<Button type="default" size="small" onClick={openEdit} disabled={!selected}>
    编辑
</Button>
```
改为：
```tsx
<Button type="default" size="small" icon={<EditOutlined />} onClick={openEdit} disabled={!selected}>
    编辑
</Button>
```

- [ ] **Step 7: 修改 workflow-config.tsx**

找到文件中 `import { Button } from "antd";`（约第 10 行）之后新增：
```tsx
import { DeleteOutlined } from "@ant-design/icons";
```

找到"删除"按钮（在 FormList 节点循环中）：
```tsx
<Button size="small" type="text" onClick={() => remove(field.name)}>
    {"删除"}
</Button>
```
改为：
```tsx
<Button size="small" type="text" icon={<DeleteOutlined />} onClick={() => remove(field.name)}>
    {"删除"}
</Button>
```

- [ ] **Step 8: 修改 data-lake-config.tsx**

在文件第 4 行 `import { Alert, Button, Select, Space, Table, Tag } from "antd";` 之后新增：
```tsx
import { EyeOutlined, EditOutlined, DeleteOutlined } from "@ant-design/icons";
```

找到操作列中三个按钮（"详情"/"编辑"/"删除"），一一加上 icon prop：

"详情"按钮：
```tsx
<Button
    size="small"
    onClick={() => {
        if (!record.id) return;
        navigate(`/admin/data-lake/${record.id}?view=detail`);
    }}
>
    详情
</Button>
```
改为：
```tsx
<Button
    size="small"
    icon={<EyeOutlined />}
    onClick={() => {
        if (!record.id) return;
        navigate(`/admin/data-lake/${record.id}?view=detail`);
    }}
>
    详情
</Button>
```

"编辑"按钮：
```tsx
<Button
    size="small"
    onClick={() => {
        if (!record.id) return;
        navigate(`/admin/data-lake/${record.id}`);
    }}
>
    编辑
</Button>
```
改为：
```tsx
<Button
    size="small"
    icon={<EditOutlined />}
    onClick={() => {
        if (!record.id) return;
        navigate(`/admin/data-lake/${record.id}`);
    }}
>
    编辑
</Button>
```

"删除"按钮：
```tsx
<Button
    size="small"
    danger
    onClick={async () => {
        if (!record.id) return;
        try {
            await adminApi.deleteDataLake(record.id);
```
改为（只加 `icon={<DeleteOutlined />}`）：
```tsx
<Button
    size="small"
    danger
    icon={<DeleteOutlined />}
    onClick={async () => {
        if (!record.id) return;
        try {
            await adminApi.deleteDataLake(record.id);
```

- [ ] **Step 9: 编译检查**

```bash
cd /opt/prod/s10/s10-stack/source/dts-admin-webapp && npx tsc --noEmit 2>&1 | head -30
```
Expected: 无新增 TS 错误（原有旧错误可忽略）

- [ ] **Step 10: Commit**

```bash
cd /opt/prod/s10/s10-stack/source/dts-admin-webapp
git add src/admin/views/role-management.tsx src/admin/views/user-management.tsx src/admin/views/user-detail.tsx src/admin/views/role-detail.tsx src/admin/views/my-changes.tsx src/admin/views/org-management.tsx src/admin/views/workflow-config.tsx src/admin/views/data-lake-config.tsx
git commit -m "fix(admin): add EditOutlined/DeleteOutlined/EyeOutlined icons to action buttons"
```

---

### Task 2: Platform-webapp — 编辑/删除/详情按钮加图标（13 个文件）

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/ModelTemplatesPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/workbench/index.tsx`
- Modify: `source/dts-platform-webapp/src/pages/governance/TemplatesPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/services/DataProductsPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/catalog/AssetDetailPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/components/ExecutionHistoryTable.tsx`
- Modify: `source/dts-platform-webapp/src/pages/workbench/WorkflowCenterPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/governance/IndicatorsPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/governance/GlossaryPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/governance/ElementsPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/governance/ReferenceCodesPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/governance/AssetOwnershipPage.tsx`

**注意：** 部分文件已有部分图标 import，需要将新图标追加到现有 import 中，不要重复导入。
- `IndicatorsPage.tsx`：已有 `import { PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined } from "@ant-design/icons";`（无需改 import，直接加 icon prop）
- `DataProductsPage.tsx`：已有 `import { PlusOutlined, EditOutlined, DeleteOutlined, FileAddOutlined } from "@ant-design/icons";`（只需追加 `EyeOutlined`）
- `ExecutionHistoryTable.tsx`：已有 `import { PlayCircleOutlined, ReloadOutlined } from "@ant-design/icons";`（只需追加 `EyeOutlined`）
- `GlossaryPage.tsx`：已有 `import { EyeOutlined } from "@ant-design/icons";`（追加 `EditOutlined, DeleteOutlined`；且 详情 button 已有 `icon={<EyeOutlined />}`，跳过）
- `ElementsPage.tsx`：已有 `import { EyeOutlined } from "@ant-design/icons";`（追加 `EditOutlined, DeleteOutlined`；且 详情 button 已有 `icon={<EyeOutlined />}`，跳过）

- [ ] **Step 1: 修改 ModelTemplatesPage.tsx**

该文件无 `@ant-design/icons` import，在文件顶部 antd import 之后新增一行：
```tsx
import { EditOutlined, DeleteOutlined } from "@ant-design/icons";
```

找到两个按钮：
```tsx
<Button size="small" onClick={() => openEdit(row)}>
    编辑
</Button>
```
改为：
```tsx
<Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)}>
    编辑
</Button>
```

```tsx
<Button size="small" danger onClick={() => handleDelete(row)}>
    删除
</Button>
```
改为：
```tsx
<Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(row)}>
    删除
</Button>
```

- [ ] **Step 2: 修改 workbench/index.tsx**

该文件无 `@ant-design/icons` import，在文件顶部 antd import 之后新增一行：
```tsx
import { EditOutlined } from "@ant-design/icons";
```

找到"编辑"按钮（在收藏操作区域）：
```tsx
<Button size="small" onClick={() => openFavoriteEdit(favorite)}>
    编辑
</Button>
```
改为：
```tsx
<Button size="small" icon={<EditOutlined />} onClick={() => openFavoriteEdit(favorite)}>
    编辑
</Button>
```

- [ ] **Step 3: 修改 governance/TemplatesPage.tsx**

该文件无 `@ant-design/icons` import，在文件顶部 antd import 之后新增一行：
```tsx
import { EditOutlined, DeleteOutlined } from "@ant-design/icons";
```

找到模板详情 Card 的 extra 区域两个按钮：
```tsx
<Button size="small" onClick={() => openModal(activeTemplate)} disabled={!canManage}>
    编辑
</Button>
<Button size="small" danger onClick={() => removeTemplate(activeTemplate)} disabled={!canManage}>
    删除
</Button>
```
改为：
```tsx
<Button size="small" icon={<EditOutlined />} onClick={() => openModal(activeTemplate)} disabled={!canManage}>
    编辑
</Button>
<Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeTemplate(activeTemplate)} disabled={!canManage}>
    删除
</Button>
```

- [ ] **Step 4: 修改 governance/SubjectAreasPage.tsx**

该文件无 `@ant-design/icons` import，在文件顶部 antd import 之后新增一行：
```tsx
import { EditOutlined, DeleteOutlined } from "@ant-design/icons";
```

找到两个按钮：
```tsx
<Button onClick={() => openModal(activeDomain, activeDomain.parentId)} disabled={!canManage}>
    编辑域属性
</Button>
<Button danger onClick={() => confirmDelete(activeDomain)} disabled={!canManage}>
    删除域
</Button>
```
改为：
```tsx
<Button icon={<EditOutlined />} onClick={() => openModal(activeDomain, activeDomain.parentId)} disabled={!canManage}>
    编辑域属性
</Button>
<Button danger icon={<DeleteOutlined />} onClick={() => confirmDelete(activeDomain)} disabled={!canManage}>
    删除域
</Button>
```

- [ ] **Step 5: 修改 services/DataProductsPage.tsx**

该文件已有 import（第 16 行）：
```tsx
import { PlusOutlined, EditOutlined, DeleteOutlined, FileAddOutlined } from "@ant-design/icons";
```
改为（追加 `EyeOutlined`）：
```tsx
import { PlusOutlined, EditOutlined, DeleteOutlined, FileAddOutlined, EyeOutlined } from "@ant-design/icons";
```

找到"详情"按钮：
```tsx
<Button size="small" onClick={() => openDetail(record)}>详情</Button>
```
改为：
```tsx
<Button size="small" icon={<EyeOutlined />} onClick={() => openDetail(record)}>详情</Button>
```

- [ ] **Step 6: 修改 catalog/AssetDetailPage.tsx**

该文件无 `@ant-design/icons` import，在文件顶部 antd import 之后新增一行：
```tsx
import { EyeOutlined } from "@ant-design/icons";
```

找到"详情"按钮：
```tsx
<Button type="link" size="small" onClick={() => void openDetail(row)}>
    详情
</Button>
```
改为：
```tsx
<Button type="link" size="small" icon={<EyeOutlined />} onClick={() => void openDetail(row)}>
    详情
</Button>
```

- [ ] **Step 7: 修改 explore/etl/components/ExecutionHistoryTable.tsx**

该文件已有 import（第 21 行）：
```tsx
import { PlayCircleOutlined, ReloadOutlined } from "@ant-design/icons";
```
改为（追加 `EyeOutlined`）：
```tsx
import { PlayCircleOutlined, ReloadOutlined, EyeOutlined } from "@ant-design/icons";
```

找到"详情"按钮：
```tsx
<Button type="link" size="small" onClick={() => openAuditDetail(record)}>
    详情
</Button>
```
改为：
```tsx
<Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openAuditDetail(record)}>
    详情
</Button>
```

- [ ] **Step 8: 修改 workbench/WorkflowCenterPage.tsx**

该文件无 `@ant-design/icons` import，在文件顶部 antd import 之后新增一行：
```tsx
import { EyeOutlined } from "@ant-design/icons";
```

找到"查看详情"按钮：
```tsx
return <Button onClick={() => push("/dashboard/catalog/datasets")}>查看详情</Button>;
```
改为：
```tsx
return <Button icon={<EyeOutlined />} onClick={() => push("/dashboard/catalog/datasets")}>查看详情</Button>;
```

- [ ] **Step 9: 修改 governance/IndicatorsPage.tsx**

该文件已有 import（第 24 行）：
```tsx
import { PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined } from "@ant-design/icons";
```
无需修改 import。

找到操作列两个按钮（在 references 子表格中，约 1745 行）：
```tsx
<Button size="small" onClick={() => editReference(record)} disabled={!canManage}>
    编辑
</Button>
<Button size="small" danger onClick={() => void removeReference(record.id)} disabled={!canManage}>
    删除
</Button>
```
改为：
```tsx
<Button size="small" icon={<EditOutlined />} onClick={() => editReference(record)} disabled={!canManage}>
    编辑
</Button>
<Button size="small" danger icon={<DeleteOutlined />} onClick={() => void removeReference(record.id)} disabled={!canManage}>
    删除
</Button>
```

- [ ] **Step 10: 修改 governance/GlossaryPage.tsx**

该文件已有 import（第 5 行）：
```tsx
import { EyeOutlined } from "@ant-design/icons";
```
改为（追加 EditOutlined, DeleteOutlined）：
```tsx
import { EyeOutlined, EditOutlined, DeleteOutlined } from "@ant-design/icons";
```

找到"编辑"和"删除"按钮（详情 button 已有 icon，跳过）：
```tsx
<Button type="link" size="small" onClick={() => openModal(row)} disabled={!canManage}>
    编辑
</Button>
<Button type="link" size="small" danger onClick={() => removeGlossary(row)} disabled={!canManage}>
    删除
</Button>
```
改为：
```tsx
<Button type="link" size="small" icon={<EditOutlined />} onClick={() => openModal(row)} disabled={!canManage}>
    编辑
</Button>
<Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => removeGlossary(row)} disabled={!canManage}>
    删除
</Button>
```

- [ ] **Step 11: 修改 governance/ElementsPage.tsx**

该文件已有 import（第 5 行）：
```tsx
import { EyeOutlined } from "@ant-design/icons";
```
改为（追加 EditOutlined, DeleteOutlined）：
```tsx
import { EyeOutlined, EditOutlined, DeleteOutlined } from "@ant-design/icons";
```

找到"编辑"和"删除"按钮（详情 button 已有 icon，跳过）：
```tsx
<Button type="link" size="small" onClick={() => openModal(row)} disabled={!canManage}>
    编辑
</Button>
<Button type="link" size="small" danger onClick={() => removeElement(row)} disabled={!canManage}>
    删除
</Button>
```
改为：
```tsx
<Button type="link" size="small" icon={<EditOutlined />} onClick={() => openModal(row)} disabled={!canManage}>
    编辑
</Button>
<Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => removeElement(row)} disabled={!canManage}>
    删除
</Button>
```

- [ ] **Step 12: 修改 governance/ReferenceCodesPage.tsx**

该文件无 `@ant-design/icons` import，在文件顶部 antd import 之后新增一行：
```tsx
import { EditOutlined, DeleteOutlined } from "@ant-design/icons";
```

该文件有三处操作列需要修改：

**（1）目录列（directoryColumns，约 788 行）：**
```tsx
<Button type="link" size="small" onClick={() => openModal(row)} disabled={!canManage}>
    编辑
</Button>
<Button type="link" size="small" danger onClick={() => removeDirectory(row)} disabled={!canManage}>
    删除
</Button>
```
改为：
```tsx
<Button type="link" size="small" icon={<EditOutlined />} onClick={() => openModal(row)} disabled={!canManage}>
    编辑
</Button>
<Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => removeDirectory(row)} disabled={!canManage}>
    删除
</Button>
```

**（2）代码值列（itemColumns，约 815 行）：**
```tsx
<Button type="link" size="small" onClick={() => openItemModal(row)} disabled={!canManage}>
    编辑
</Button>
<Button type="link" size="small" danger onClick={() => removeItem(row)} disabled={!canManage}>
    删除
</Button>
```
改为：
```tsx
<Button type="link" size="small" icon={<EditOutlined />} onClick={() => openItemModal(row)} disabled={!canManage}>
    编辑
</Button>
<Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => removeItem(row)} disabled={!canManage}>
    删除
</Button>
```

**（3）映射列（mappingColumns，约 837 行）：**
```tsx
<Button type="link" size="small" onClick={() => openMappingModal(row)} disabled={!canManage}>
    编辑
</Button>
<Button type="link" size="small" danger onClick={() => removeMapping(row)} disabled={!canManage}>
    删除
</Button>
```
改为：
```tsx
<Button type="link" size="small" icon={<EditOutlined />} onClick={() => openMappingModal(row)} disabled={!canManage}>
    编辑
</Button>
<Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => removeMapping(row)} disabled={!canManage}>
    删除
</Button>
```

- [ ] **Step 13: 修改 governance/AssetOwnershipPage.tsx**

该文件无 `@ant-design/icons` import，在文件顶部 antd import 之后新增一行：
```tsx
import { EditOutlined } from "@ant-design/icons";
```

找到"编辑"按钮：
```tsx
<Button type="link" size="small" onClick={() => {
    setEditDept(record.ownerDeptCode);
    setEditModal({ open: true, record });
}}>编辑</Button>
```
改为：
```tsx
<Button type="link" size="small" icon={<EditOutlined />} onClick={() => {
    setEditDept(record.ownerDeptCode);
    setEditModal({ open: true, record });
}}>编辑</Button>
```

- [ ] **Step 14: 编译检查**

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp && npx tsc --noEmit 2>&1 | head -30
```
Expected: 无新增 TS 错误

- [ ] **Step 15: Commit**

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp
git add src/pages/modeling/ModelTemplatesPage.tsx src/pages/workbench/index.tsx src/pages/governance/TemplatesPage.tsx src/pages/governance/SubjectAreasPage.tsx src/pages/services/DataProductsPage.tsx src/pages/catalog/AssetDetailPage.tsx src/pages/explore/etl/components/ExecutionHistoryTable.tsx src/pages/workbench/WorkflowCenterPage.tsx src/pages/governance/IndicatorsPage.tsx src/pages/governance/GlossaryPage.tsx src/pages/governance/ElementsPage.tsx src/pages/governance/ReferenceCodesPage.tsx src/pages/governance/AssetOwnershipPage.tsx
git commit -m "fix(platform): add EditOutlined/DeleteOutlined/EyeOutlined icons to action buttons"
```

---

## 完成检查

两个 task 完成后，在浏览器验证几个典型页面：

1. 菜单管理 → 角色管理：表格操作列显示眼睛图标（详情）、铅笔图标（编辑）、垃圾桶图标（删除）
2. 用户管理：编辑按钮前有铅笔图标，详情按钮前有眼睛图标
3. 用户详情页右上角：编辑按钮前有铅笔图标（不再有 Iconify 图标）
4. 数据湖配置：详情/编辑/删除三个按钮各自有对应图标
5. Platform-webapp → 业务术语：编辑/删除按钮有图标（详情已有）
6. 参考代码页：三个子表格（目录/代码值/映射）的编辑/删除按钮均有图标
