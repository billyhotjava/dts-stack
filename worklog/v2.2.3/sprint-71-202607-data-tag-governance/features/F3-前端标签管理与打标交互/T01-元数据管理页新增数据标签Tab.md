# T01: 元数据管理页新增数据标签 Tab

**优先级**: P1
**状态**: READY
**依赖**: F2/T01

## 目标

在既有「元数据管理」页新增「数据标签」Tab，承载标签目录树与标签 CRUD，不新增菜单项。

## 技术设计

改造 `pages/catalog/MetadataManagementPage.tsx`（既有页，已有 source-contract 测试
`MetadataManagementPage.source-contract.test.ts`，改造须同步更新）。

### 布局

左右两栏：
- **左栏**：标签分类树，支持新建/重命名/删除分类，预置分类显示 builtin 标记且删除按钮禁用
- **右栏**：选中分类下的标签列表（表格），支持新建/编辑/停用/删除标签，展示标签色值与使用计数

### 交互要求

- 分页遵循项目统一约定：**默认 10 条/页，切换每页条数必须触发刷新**，切换条数时重置到第 1 页
- 删除受保护对象（非空分类、builtin、已打标标签）时，后端返回的中文原因须原样展示给用户，不可吞掉
- 已打标标签删除需二次确认，明确告知将影响多少个资产
- 表格优先使用项目既有 `CompactTable` 组件，保持与其他治理页一致

### 设计约束

标签色值选择器仅提供受控调色板（与项目 design token 一致），不开放任意取色，避免界面色彩失控。

## 影响范围

- 修改 `dts-platform-webapp/src/pages/catalog/MetadataManagementPage.tsx`
- 修改 `MetadataManagementPage.source-contract.test.ts`
- 新增标签管理相关组件与 API client
- **不修改** dts-admin 菜单种子（不新增菜单项）

## 验证

- [ ] RED：先更新 source-contract 测试，断言下列内容后运行应失败
- [ ] Tab 存在且可切换，分类树正确渲染层级
- [ ] 标签表格默认 10 条/页，切换条数触发刷新并重置到第 1 页
- [ ] builtin 分类/标签的删除按钮为禁用态
- [ ] 删除保护错误信息正确展示
- [ ] `npx tsc --noEmit` 与前端 build 通过
- [ ] 真实浏览器 smoke：登录后进入元数据管理页，完成一次标签新建→编辑→删除，截图存 `it/`
- [ ] Chrome95 兼容性验证（项目浏览器下限）

## 完成标准

- [ ] Tab 可用，CRUD 闭环
- [ ] 未新增任何菜单项
- [ ] source-contract + 浏览器 smoke 双证据齐备
