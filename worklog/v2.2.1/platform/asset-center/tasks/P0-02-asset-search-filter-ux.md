# P0-02 资产搜索与筛选体验统一

`status`: `done`
`priority`: `P0`

## 目标

统一资产搜索、筛选、结果展示的交互规则。

## 后端实施点

1. 对齐 `/catalog/search` 与 `/catalog/datasets` 字段语义。
2. 补充高频过滤条件（系统、层级、主题域、密级）。

## 前端实施点

1. 统一筛选器组件（多选、清空、快捷条件）。
2. 搜索结果支持保存查询条件。
3. 列表列宽、排序、标签显示统一。

## 验收标准

- 搜索页与资产页筛选字段语义一致。
- 用户可复用一次筛选配置进行多页查询。

## 实施结果

1. `/api/catalog/search` 补齐与 `/api/catalog/datasets` 对齐的过滤参数：
   - `domainId`、`sourceId`、`classification`、`ownerDept`、`warehouseLayer`、`exposedBy`、`datasetType`、`enabledOnly`。
2. 搜索页新增高频筛选项：
   - 系统（数据源类型）、分层、主题域、密级，并统一为与资产页相同的枚举语义。
3. 搜索页支持“保存条件 / 恢复条件”，并可一键“应用资产筛选”，复用资产页缓存条件。
4. 资产页补充“密级/分层”筛选与“重置筛选”，筛选体验与搜索页保持一致。

## 验证记录

- `source/dts-platform`: `./mvnw -DskipTests compile` 通过。
- `source/dts-platform-webapp`: `pnpm build` 通过。
