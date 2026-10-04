# T08: ScreenGrantPanel 前端（USER/DEPT/ROLE 授权）

**优先级**: P0
**状态**: READY
**依赖**: T05

## 目标
新建 ScreenGrantPanel 组件，替代旧 ScreenSharePanel，支持 USER/DEPT/ROLE 三种授权方式

## 技术设计

### 组件结构

```
ScreenGrantPanel
├─ 拥有者信息（创建者 + 部门）
├─ 当前授权列表
│   ├─ 每条：图标 + 名称 + 权限下拉(只读/可编辑) + 删除按钮
│   └─ granteeType 图标区分：👤USER 🏢DEPT 🎭ROLE
├─ 添加授权区
│   ├─ 类型切换：用户 / 部门 / 角色（Radio.Group）
│   ├─ 搜索框（根据类型调用不同 API）
│   ├─ 权限选择：只读 / 可编辑（Radio.Group）
│   └─ 添加按钮
└─ 预留：审批设置区域（灰色提示"暂未开放"）
```

### API 调用

| 操作 | API |
|------|-----|
| 加载授权列表 | GET `/api/screens/{id}/grants` |
| 添加授权 | PUT `/api/screens/{id}/grants` |
| 修改权限 | PUT `/api/screens/{id}/grants` (同上，覆盖) |
| 撤销授权 | DELETE `/api/screens/{id}/grants/{grantId}` |
| 搜索用户 | GET `/analytics/api/user/search?q=xxx` |
| 部门列表 | GET `/analytics/api/platform/directory/orgs` (新增代理) |
| 角色列表 | GET `/analytics/api/platform/directory/roles` (新增代理) |

### 权限控制
- 仅 isOwner 或 OP_ADMIN 可打开此面板
- 非 OWNER 看到的"分享"按钮应隐藏或禁用

## 影响范围
- 新建: `screens/components/ScreenGrantPanel.tsx`
- 修改: `ScreenHeader.tsx` — 分享按钮指向新面板
- 修改: `analyticsApi.ts` — 新增 grants 相关 API 方法
- 可能新增: platform directory 代理端点（orgs/roles）

## 验证
- [ ] 可添加用户授权（搜索 + 选择 + 设权限）
- [ ] 可添加部门授权
- [ ] 可添加角色授权
- [ ] 可修改已有授权的权限级别
- [ ] 可撤销授权
- [ ] 非 OWNER 无法打开面板

## 完成标准
- [ ] 三种授权方式均可正常操作
- [ ] UI 交互流畅，权限变更即时生效
