# F2: catalog 列同步捕获 meta

**优先级**: P0
**状态**: READY
**依赖**: F1（契约定义）

## 目标
让平台 catalog 的列同步捕获 dbt 列 meta（semantic_type/standard_code），并在 `CatalogAssetColumnContract` 携带这两字段。**T00 spike 是全 sprint 前置**——决定 F2 走 OM 镜像还是直读 dbt。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T00 | spike：验证 OM dbt ingestion 是否已抓 `meta`（前置，决定 T02 路线） | P0 | READY | - |
| T01 | `CatalogAssetColumnContract` 加 `semanticType`/`standardCode` 字段 | P0 | READY | F1-T01 |
| T02 | 列同步捕获 meta（OM 镜像 or 直读 dbt，依 T00） | P0 | READY | T00, T01 |

## 完成标准
- [ ] T00 结论明确：OM 是否抓 meta、F2 路线（OM 镜像 / 直读 dbt）。
- [ ] `CatalogAssetColumnContract` 携 `semanticType`/`standardCode`；所有构造点同步。
- [ ] 列同步把 dbt meta 填进列契约；无 meta 的列回退 null（不破现有 catalog）。
