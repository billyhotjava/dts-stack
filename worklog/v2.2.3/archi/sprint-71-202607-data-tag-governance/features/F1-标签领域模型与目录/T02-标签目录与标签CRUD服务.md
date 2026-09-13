# T02: 标签目录与标签 CRUD 服务

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

提供标签目录与标签定义的增删改查服务，含校验规则、删除保护与审计接入。

## 技术设计

新建 `service/catalog/CatalogTagService.java`：

### 目录能力
- `listCategoryTree()` — 返回多级分类树（一次查询 + 内存组树，避免递归 N+1）
- `createCategory` / `updateCategory` / `deleteCategory`
- 删除保护：分类下存在标签或子分类时拒绝删除，返回明确错误信息
- `builtin=true` 的预置分类禁止删除，仅允许停用

### 标签能力
- `listTags(categoryId, keyword, enabled)` — 分页查询
- `createTag` / `updateTag` / `deleteTag`
- 删除标签时：若已有资产打标，默认拒绝；提供 `force` 参数级联清理 `catalog_asset_tag` 并记录审计
- `builtin=true` 的预置标签禁止删除与改名，仅允许停用与改色

### 校验
- `code` 必须为 ASCII、非空、全局唯一，遵循项目 dim-code-design 约定（禁止中文/空格作为编码）
- `name` 非空
- 停用标签不影响既有打标关系，但不可用于新增打标

### 审计
接入既有审计目录管道（参照 sprint-34 审计目录 DB 化成果），动作分类需在 `dts-admin` 审计资源字典中登记，
否则审计日志会落到未分类。具体登记方式参照
`dts-admin/src/main/resources/config/liquibase/changelog/20260105-01_audit_operation_mapping_platform_catalog.xml`。

## 影响范围

- 新增 `.../service/catalog/CatalogTagService.java`
- 新增 `.../service/catalog/dto/` 下标签相关 DTO
- 修改 dts-admin 审计资源字典 changelog（新增标签动作分类）
- 依赖 T01 的三个 Repository

## 验证

- [ ] RED：先写 `CatalogTagServiceTest`（单元）+ `CatalogTagServiceIT`（含库），断言下列场景后运行应失败
- [ ] 非空分类删除被拒绝
- [ ] builtin 分类/标签删除被拒绝
- [ ] 重复 code 创建被拒绝
- [ ] 非 ASCII code 被拒绝
- [ ] 已打标标签 `force=false` 删除被拒绝、`force=true` 级联清理成功
- [ ] 停用标签不可用于新增打标，但既有打标关系保留
- [ ] 审计日志分类正确（不落「未分类」）

## 完成标准

- [ ] CRUD 全通，全部校验与删除保护生效
- [ ] 分类树查询无 N+1
- [ ] 审计动作已在资源字典登记并验证分类正确
