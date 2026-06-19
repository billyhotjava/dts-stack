# F1: 目录与搜索

**优先级**: P0
**状态**: READY

## 目标

把现网 catalog 的资产入口（搜索、数据集、数据产品）落进阶段③ slot，让用户在「资产」阶段能搜到、列出、点开 S4 画布产出的 ODS 宽表及其它数据集。纯前端 mock，CompactTable 默认 10 条/页，命名对齐现网。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-资产搜索.md) | 资产搜索（DataSearch，关键词+过滤） | P0 | READY | S1 |
| [T02](./T02-数据集列表与详情.md) | 数据集列表 + 详情（Datasets/DatasetDetail） | P0 | READY | S1 |
| [T03](./T03-数据产品.md) | 数据产品（DataProducts） | P0 | READY | S1 |

## 完成标准

- [ ] `DataSearchPage` 关键词 + 过滤（域 / 层 / 类型）可用，结果 CompactTable 渲染，点击跳数据集详情。
- [ ] `DatasetsPage` 列表（CompactTable）+ `DatasetDetailPage` 详情可路由可访问，详情页展示 schema/字段、标签、关联血缘入口。
- [ ] `DataProductsPage` 列表可访问并接 mock。
- [ ] 样例 `ODS.宽表` 在搜索与数据集列表中可见，详情页字段来自 S4 画布产出。
- [ ] 全部页面接 mock service（`catalogDomainService` / `dataProductsService`），`VITE_USE_MOCK` 开关生效。
