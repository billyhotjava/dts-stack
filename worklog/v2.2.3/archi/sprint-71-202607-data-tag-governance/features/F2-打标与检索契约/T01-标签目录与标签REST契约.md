# T01: 标签目录与标签 REST 契约

**优先级**: P1
**状态**: READY
**依赖**: F1/T02

## 目标

暴露标签目录与标签定义的 REST 端点，供前端标签管理 Tab 消费。

## 技术设计

新建 `web/rest/catalog/CatalogTagResource.java`，落在既有 `web/rest/catalog/` 包下（与 `CatalogAssetPortalResource` 同级）。

### 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/catalog/tag-categories` | 返回分类树（含每分类下标签计数） |
| POST | `/api/catalog/tag-categories` | 新建分类 |
| PUT | `/api/catalog/tag-categories/{id}` | 更新分类 |
| DELETE | `/api/catalog/tag-categories/{id}` | 删除分类（受 T02 删除保护约束） |
| GET | `/api/catalog/tags` | 分页查询标签，支持 `categoryId` / `keyword` / `enabled` 过滤 |
| POST | `/api/catalog/tags` | 新建标签 |
| PUT | `/api/catalog/tags/{id}` | 更新标签 |
| DELETE | `/api/catalog/tags/{id}` | 删除标签，支持 `?force=true` |

### 约定

- 分页参数遵循项目既有约定，**默认 10 条/页**（见项目分页统一约定；前端切换条数必须刷新）
- 错误响应遵循项目既有错误信封格式，删除保护类错误须返回可读中文原因
- DTO 与实体分离，不直接暴露 JPA 实体

## 影响范围

- 新增 `.../web/rest/catalog/CatalogTagResource.java`
- 新增对应请求/响应 DTO
- 依赖 F1/T02 `CatalogTagService`

## 验证

- [ ] RED：先写 `CatalogTagResourceIT`（MockMvc），断言下列场景后运行应失败
- [ ] 分类树端点返回正确层级与标签计数
- [ ] 标签分页默认返回 10 条
- [ ] 重复 code 创建返回 4xx 且错误信息可读
- [ ] 删除非空分类返回 4xx 且说明原因
- [ ] 未认证请求被拒绝

## 完成标准

- [ ] 8 个端点全部可用且有 IT 覆盖
- [ ] 分页默认 10 条符合项目约定
- [ ] 错误信封与既有契约一致
