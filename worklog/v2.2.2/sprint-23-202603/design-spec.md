# 核心页面体验优化 — 设计规格

## 1. 问题陈述

analytics-webapp 核心页面存在以下体验问题：
1. "分析中心"页面与首页功能重复（都是展示最近的仪表盘/查询 + 新建按钮），无独立价值
2. 查询和仪表盘列表无删除功能，用户创建的数据无法清理
3. 回收站（TrashPage）只展示已删除项，无法恢复
4. "集合"和"你的个人集合"两个侧边栏入口重复，"集合"对非技术用户不友好

## 2. 目标

- 移除冗余的"分析中心"页面
- 查询/仪表盘列表支持行内删除和批量删除（软删除，移至回收站）
- 回收站支持单个恢复和批量恢复
- "集合"改名为"工作空间"，合并两个入口为一个

## 3. 非目标

- 不做永久删除（后端无此 API，软删除不影响性能）
- 不做移动到工作空间（后续迭代）
- 不改后端（DELETE/PUT archived API 已存在）
- 不改 API 路由路径（仍然是 /collections）

## 4. 后端 API 现状

以下 API 已存在，前端只需对接：

| 操作 | API | 说明 |
|------|-----|------|
| 删除查询 | `DELETE /api/card/{id}` | 设置 archived=true |
| 删除仪表盘 | `DELETE /api/dashboard/{id}` | 设置 archived=true |
| 恢复查询 | `PUT /api/card/{id}` body `{ archived: false }` | 取消归档 |
| 恢复仪表盘 | `PUT /api/dashboard/{id}` body `{ archived: false }` | 取消归档 |
| 获取回收站 | `GET /api/trash` | 返回 `{ dashboards: [], cards: [] }` |

前端 API 客户端需补充：`deleteCard()`、`deleteDashboard()` 和 `updateDashboard()` 方法。
恢复操作：查询用已有的 `updateCard()`，仪表盘需新增 `updateDashboard()` 方法。

## 5. F1: 移除分析中心

**改动：**
1. 删除 `src/pages/AnalyzePage.tsx`
2. `src/routes.tsx`：移除 `/analyze` 路由
3. `src/layouts/AppLayout.tsx`：侧边栏移除"分析中心"菜单项
4. `src/i18n.ts`：移除 `nav.analyze` 翻译 key
5. 其他页面中跳转到 `/analyze` 的链接：改为 `/` 或移除

**侧边栏 After：**
```
主要功能
  ├ 首页
  ├ 查询
  ├ 仪表盘
  └ 工作空间
```

## 6. F2: 查询/仪表盘删除功能

两个页面（CardsPage + DashboardsPage）使用统一模式。

### 6.1 行内操作（单个删除）

每行右侧添加 `···` 按钮（antd `Dropdown` + `EllipsisOutlined`），下拉菜单包含"移至回收站"。

点击"移至回收站"→ antd `Modal.confirm` 二次确认 → 调用 DELETE API → 刷新列表 + success 提示。

### 6.2 批量操作

- 每行左侧添加 checkbox
- 顶部添加"全选"checkbox
- 选中 ≥1 项时，顶部显示批量操作栏：`已选 N 项 | [移至回收站] | [取消选择]`
- Grid 视图：checkbox 覆盖在卡片左上角
- List 视图：checkbox 作为表格第一列

批量删除流程：二次确认 → 逐个调用 DELETE API → 刷新列表。

### 6.3 API 客户端补充

在 `analyticsApi.ts` 中添加：
```typescript
async deleteCard(id: number | string): Promise<void> {
  await apiClient.delete(`/analytics/api/card/${id}`);
}

async deleteDashboard(id: number | string): Promise<void> {
  await apiClient.delete(`/analytics/api/dashboard/${id}`);
}

async updateDashboard(id: number | string, body: unknown): Promise<unknown> {
  const { data } = await apiClient.put(`/analytics/api/dashboard/${id}`, body);
  return data;
}
```

**批量操作注意：** 如果批量删除/恢复中某个请求失败，UI 仍应刷新列表展示部分结果，而非回滚。

## 7. F3: 回收站恢复功能

### 7.1 页面改造

当前 TrashPage 只读，改造为支持操作：

```
┌─ 回收站 ──────────────────────────────────────────┐
│ □ 全选                              [恢复选中项]    │
│ ─────────────────────────────────────────────────  │
│ □  📊 财务1        仪表盘   2026-03-28    [恢复]   │
│ □  📋 que2         查询     2026-03-27    [恢复]   │
└────────────────────────────────────────────────────┘
```

### 7.2 功能

1. **单个恢复**：每行右侧"恢复"按钮，点击直接恢复（无需确认，恢复是安全操作）
   - 查询：`PUT /api/card/{id}` body `{ archived: false }`
   - 仪表盘：`PUT /api/dashboard/{id}` body `{ archived: false }`
   - 恢复后从列表移除 + success 提示

2. **批量恢复**：与 F2 相同的 checkbox 模式
   - 选中 ≥1 项时顶部显示"恢复选中项"按钮
   - 逐个调用 PUT API → 刷新列表

3. **信息增强**：每行显示类型图标、名称、类型标签（antd Tag）、时间（使用 `updated_at` 字段，如 API 未返回则省略时间列）

## 8. F4: 工作空间入口合并

### 8.1 侧边栏

- "集合" label 改为 "工作空间"
- 移除 `/collections/root`（"你的个人集合"）菜单项
- 路由路径不变：`/collections`、`/collections/:id`

### 8.2 i18n

- `nav.collections`: "集合" → "工作空间" (zh), "Collections" → "Workspace" (en)
- 移除 `nav.myCollection` key

### 8.3 CollectionsPage

- 页面标题改为"工作空间"
- root 集合在列表中标注 antd Tag "个人"

### 8.4 面包屑

- ROUTE_NAV_MAP 中 `/collections` 的显示名更新为"工作空间"

## 9. 影响范围

### 删除文件
- `src/pages/AnalyzePage.tsx`

### 修改文件
| 文件 | 改动 |
|------|------|
| `src/routes.tsx` | 移除 `/analyze` 路由 |
| `src/layouts/AppLayout.tsx` | 侧边栏：移除分析中心、移除个人集合、集合→工作空间 |
| `src/i18n.ts` | 更新/移除翻译 key |
| `src/api/analyticsApi.ts` | 添加 deleteCard/deleteDashboard 方法 |
| `src/pages/CardsPage.tsx` | 添加行内删除 + 批量选择 |
| `src/pages/DashboardsPage.tsx` | 添加行内删除 + 批量选择 |
| `src/pages/TrashPage.tsx` | 添加恢复功能 + 批量恢复 |
| `src/pages/CollectionsPage.tsx` | 标题改为"工作空间" |
| `src/pages/HomePage.tsx` | 移除跳转到 /analyze 的链接（如果有） |
| `src/pages/PublicDashboardPage.tsx` | 面包屑中 /analyze 链接修改或移除 |
| `src/pages/PublicCardPage.tsx` | 面包屑中 /analyze 链接修改或移除 |

### 不变
- 后端（零改动）
- 路由路径（`/collections` 不改）
- API 端点（已存在）
