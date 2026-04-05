# 菜单管理 UX 优化

**日期**: 2026-04-05  
**文件**: `source/dts-admin-webapp/src/admin/views/portal-menus.tsx`  
**类型**: UX 小修 — 无 API 变更，无新功能

---

## 问题

| # | 问题 | 根因 |
|---|------|------|
| P1 | 菜单名称显示 `id1302-数据大屏` | `MenuRow` 中 `name = \`id${id}-${baseName}\`` 把数据库 ID 暴露给用户 |
| P2 | 操作按钮竖排导致行高翻倍 | `flex flex-wrap justify-end` 在窄列中换行 |
| P3 | 完整路径列冗余且带 id 前缀 | 路径复用了含 id 前缀的 `name` 变量，树形结构已表达层级 |
| P4 | 3 个大统计卡片占用过多垂直空间 | 独立卡片组件，信息密度极低 |
| P5 | 黄色警告提示视觉权重过高 | `color="warning"` 的 Text 组件 |

---

## 设计方案

### P1 — 名称去前缀
- `MenuRow` 中 `name` 变量**仅用于 fullPath 构建**（内部 key 用途）
- 显示时改用独立的 `displayLabel = baseName`（即 `displayName ?? name`，不拼 id）
- fullPath 计算和 `key` 仍用含 id 的 `name`，保持唯一性不变

### P2 — 操作按钮横排
- 操作列改为 `flex items-center justify-end gap-1 flex-nowrap`
- 按钮文字缩短："禁用菜单" → 根据状态显示 **禁用 / 启用**，"配置角色" 保持
- 自定义菜单的"编辑"和"删除"折入 antd `Dropdown`（触发按钮为 `…`）
- 最多 3 个可见按钮（启用/禁用、配置角色、…）

### P3 — 删除完整路径列
- 删除 `<th>完整路径</th>` 及对应 `<td>`
- `fullPathLabel` 变量仍保留，作为菜单名 `title` tooltip 使用（hover 时可见）

### P4 — 统计移入页头
- 删除 3 个 `<Card>` 统计组件
- 在页头 `菜单管理` 标题右侧行内展示 antd `Tag`/`Badge`：`共 N 项 · 启用 N · 禁用 N`

### P5 — 警告降级
- 将 `color="warning"` 的提示文字改为普通 `text-muted-foreground`，降低视觉干扰

---

## 变更范围

- **仅修改** `portal-menus.tsx` 一个文件
- 新增 antd `Dropdown` import（antd 已是项目依赖，无新依赖）
- 无后端接口变更，无状态逻辑变更
