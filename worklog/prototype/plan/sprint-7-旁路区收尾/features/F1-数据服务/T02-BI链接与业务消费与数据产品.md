# T02: BI 链接 / 业务消费 / 数据产品（服务视角）

**优先级**: P1
**状态**: READY
**依赖**: S1

## 目标

在旁路区「服务」域落地 BI 链接、业务消费、数据产品（服务视角）三页，均为 CompactTable mock 列表骨架。

## 技术设计

- 文件：`app/src/platform/serve/BiLinksPage.tsx`、`app/src/platform/serve/BusinessConsumptionPage.tsx`、`app/src/platform/serve/DataProductsPage.tsx`（命名对齐现网；`DataProductsPage` 在阶段③资产已有数据产品入口，本页为**服务视角**——对外发布/消费角度，避免与资产视角混淆）。
- 表格：用 `CompactTable`，默认 10 条/页；切换条数刷新、`pageSize` 收敛、回第 1 页。
- BI 链接列：链接名、目标 BI 工具、绑定数据集、状态点（启用/停用）、更新时间、操作（打开占位）。
- 业务消费列：消费方、订阅资产、消费方式（API/BI/导出）、状态点、最近消费时间、操作。
- 数据产品（服务）列：产品名、版本、发布状态点、订阅数、负责人、操作（详情占位）。
- mock service：复用/扩展 `apiServicesService` 或新增轻量 `serveService`（`listBiLinks`、`listBusinessConsumption`、`listServeDataProducts` 返回 `Promise<Result<T>>`）。旁路区先占位列表骨架即可。

## 影响范围

- 新增 `app/src/platform/serve/BiLinksPage.tsx`、`BusinessConsumptionPage.tsx`、`DataProductsPage.tsx`
- 新增/扩展 mock service（`serveService` 或 `apiServicesService` 内方法）
- mock fixtures：BI 链接 / 业务消费 / 数据产品（服务）样例
- 左轨「平台·服务」入口注册；纳入全局搜索索引（F4-T02）

## 验证

- [ ] 三页左轨「平台·服务」入口可点、路由可达。
- [ ] CompactTable 默认 10 条/页；切换条数刷新且回第 1 页；状态点正确渲染。
- [ ] `VITE_USE_MOCK=1` 下展示样例数据；经 mock service 取数。
- [ ] 数据产品（服务视角）与阶段③资产视角入口不冲突、命名清晰。
- [ ] Chrome 95：无 oklch/`:has()`/容器查询；legacy 构建产物可加载。

## 完成标准

- [ ] 三页均为可路由的 CompactTable 骨架，状态列齐全。
- [ ] 全部经 mock service 取数，无硬编码业务数据散落组件内。
- [ ] 占位页不报错；命中可被全局搜索跳转。
