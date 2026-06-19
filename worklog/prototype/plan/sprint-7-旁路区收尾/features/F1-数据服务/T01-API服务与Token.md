# T01: API 服务 + Token

**优先级**: P1
**状态**: READY
**依赖**: S1

## 目标

在旁路区「服务」域落地 API 服务列表与 Token 管理两页，CompactTable mock 列表骨架，接 mock service。

## 技术设计

- 文件：`app/src/platform/serve/ApiServicesPage.tsx`、`app/src/platform/serve/TokensPage.tsx`（命名对齐现网 `pages/serve/ApiServicesPage.tsx`、`TokensPage.tsx`）。
- 表格：用 S1 提供的 `CompactTable`，默认 10 条/页；切换每页条数刷新、`pageSize` 收敛、切 size 重置第 1 页（对齐分页统一约定）。
- API 服务列：服务名、绑定数据集/数据产品、状态点（已发布/草稿/下线）、调用方、更新时间、操作（详情占位）。
- Token 列：名称、关联服务、状态点（有效/已撤销/即将过期）、创建人、过期时间（`tabular-nums` 对齐）、操作（撤销占位）。
- mock service：`apiServicesService`（`listApiServices`、`listTokens` 返回 `Promise<Result<T>>`，复刻现网契约形状）。旁路区按「先占位后充实」，先提供列表骨架即可，详情/撤销可占位。

## 影响范围

- 新增 `app/src/platform/serve/ApiServicesPage.tsx`、`app/src/platform/serve/TokensPage.tsx`
- 新增/扩展 `app/src/mock/services/apiServicesService.ts`（`listApiServices`、`listTokens`）
- mock fixtures：服务与 token 样例数据（含多种状态点态）
- 左轨「平台·服务」入口注册（依赖 S1 外壳）；纳入全局搜索索引（F4-T02）

## 验证

- [ ] 两页左轨「平台·服务」入口可点、路由可达。
- [ ] CompactTable 默认 10 条/页；切换条数刷新且回第 1 页；状态点正确渲染各态。
- [ ] `VITE_USE_MOCK=1` 下展示样例数据；经 `apiServicesService` 取数。
- [ ] Chrome 95：无 oklch/`:has()`/容器查询；legacy 构建产物可加载。

## 完成标准

- [ ] API 服务列表与 Token 列表均为可路由的 CompactTable 骨架，状态列齐全。
- [ ] 全部经 `apiServicesService` 取数，无硬编码业务数据散落组件内。
- [ ] 占位页不报错；命中可被全局搜索跳转。
