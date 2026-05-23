# T01: 主题域动作码修复

**优先级**: P0
**状态**: DONE
**依赖**: F2

## 目标

将 `CatalogDomainResource` 的主题域 CRUD 从 `CATALOG_ASSET_*` 改为主题域专属 actionCode。

## 技术设计

- 新增/使用 `CATALOG_DOMAIN_CREATE`、`CATALOG_DOMAIN_UPDATE`、`CATALOG_DOMAIN_DELETE`、`CATALOG_DOMAIN_MOVE`。
- 列表/tree 使用主题域专属查询动作；fallback 支撑查询降噪留到 T04。

## 影响范围

- `CatalogDomainResource.java`
- dts-admin DB action seed
- platform focused tests

## 验证

- [x] 创建主题域显示“新增主题域”。
- [x] 修改主题域显示“修改主题域”。
- [x] 删除主题域显示“删除主题域”。

## 完成标准

- [x] 主题域不再被分类为数据资产操作。
