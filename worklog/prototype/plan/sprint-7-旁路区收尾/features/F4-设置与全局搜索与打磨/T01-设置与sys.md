# T01: 设置 / sys

**优先级**: P1
**状态**: READY
**依赖**: S1

## 目标

在旁路区「设置」域落地 settings（个人/项目偏好）与 sys（系统管理）两页骨架，接 mock service。

## 技术设计

- 文件：`app/src/platform/settings/SettingsPage.tsx`、`SysPage.tsx`（命名对齐现网 `pages/settings`、`pages/sys`）。
- 设置（settings）：分组表单骨架——外观/语言、通知偏好、项目默认值；旁路区先占位即可。同时可挂「重置样例数据」「`VITE_USE_MOCK` 状态」等原型开发入口（对齐设计文档 §9）。
- 系统（sys）：系统管理列表骨架——用户/角色/菜单/字典占位，CompactTable 默认 10 条/页；切换条数刷新、`pageSize` 收敛、回第 1 页。
- mock service：新增/复用 `settingsService`（`getSettings`、`listSysEntries` 返回 `Promise<Result<T>>`）。

## 影响范围

- 新增 `app/src/platform/settings/SettingsPage.tsx`、`SysPage.tsx`
- 新增/扩展 `app/src/mock/services/settingsService.ts`
- mock fixtures：设置项 / 系统管理样例
- 左轨「平台·设置」入口注册；纳入全局搜索索引（T02）

## 验证

- [ ] 两页左轨「平台·设置」入口可点、路由可达。
- [ ] sys 列表用 CompactTable 默认 10 条/页；切换条数刷新且回第 1 页。
- [ ] settings 页可见原型开发入口（重置样例数据 / mock 开关状态）。
- [ ] `VITE_USE_MOCK=1` 下展示样例数据；经 `settingsService` 取数。
- [ ] Chrome 95：无 oklch/`:has()`/容器查询；legacy 构建产物可加载。

## 完成标准

- [ ] 两页均为可路由骨架。
- [ ] 全部经 `settingsService` 取数，无硬编码业务数据散落组件内。
- [ ] 占位页不报错；命中可被全局搜索跳转。
