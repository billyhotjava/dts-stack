# T02: subjects / objects / dimensions CRUD 页

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
原生实现主题域、业务对象（含 table-mappings）、维度的列表/创建/编辑，消费 `/api/semantic`。

## 技术设计
- subjects：`listSemanticSubjectDomains`/`create`/`update`（+ governance domain 字段）。
- objects：`listSemanticBusinessObjects`/`create`/`update` + table-mappings（`getObjectTableMappings`/`putObjectTableMappings`）。
- dimensions：`listSemanticDimensions`/`create`/`update`（fieldName/dataType/semanticType）。
- antd 表格 + 抽屉/弹窗表单（参照平台既有 modeling 页风格）；分页统一约定（见 [[pagination-unification-convention]]）。

## 影响范围
- `dts-platform-webapp`：subjects/objects/dimensions section 组件 + `semanticModelingApi`。

## 验证
- [ ] 三类实体 CRUD 贯通 /api/semantic；table-mappings 可编辑。
- [ ] 构建通过；分页符合统一约定。

## 完成标准
- [ ] subjects/objects(+mappings)/dimensions CRUD 可用、贯通后端、构建绿。
