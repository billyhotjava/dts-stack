# F1: 数据服务

**优先级**: P1
**状态**: READY

## 目标

收编旁路区「服务 Serve」域：API 服务、Token、BI 链接、业务消费、数据产品（服务视角）。这些页面不在黄金主线里，但通过左轨「平台」折叠区可达。按旁路区策略「先占位后充实」——每页至少**可点入口 + mock 列表骨架**（CompactTable 默认 10 条/页）。命名对齐现网 `ApiServicesPage` / `TokensPage` / `BiLinksPage` / `BusinessConsumptionPage` / `DataProductsPage`，service 用 `apiServicesService` 等分域 service。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-API服务与Token.md) | API 服务 + Token | P1 | READY | S1 |
| [T02](./T02-BI链接与业务消费与数据产品.md) | BI 链接 / 业务消费 / 数据产品（服务视角） | P1 | READY | S1 |

## 完成标准

- [ ] `ApiServicesPage`/`TokensPage`/`BiLinksPage`/`BusinessConsumptionPage`/`DataProductsPage`(服务) 在左轨「平台·服务」下均有可点入口、路由可达。
- [ ] 每页至少呈现 CompactTable mock 列表骨架（默认 10 条/页，状态列用 Swiss 状态点 token）。
- [ ] 全部经 `apiServicesService` 等分域 service 取数，返回 `Promise<Result<T>>`，`VITE_USE_MOCK` 开关下可注入/重置样例。
- [ ] 占位页不报错、不断路由；纳入全局搜索 ⌘K 索引（F4-T02）。
