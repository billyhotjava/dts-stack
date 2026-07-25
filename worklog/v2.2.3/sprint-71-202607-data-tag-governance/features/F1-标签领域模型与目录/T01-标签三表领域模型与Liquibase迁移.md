# T01: 标签三表领域模型与 Liquibase 迁移

**优先级**: P1
**状态**: READY
**依赖**: 无

## 目标

新建标签目录、标签定义、资产打标关联三张表及对应 JPA 实体，作为数据标签体系的结构化事实源。

## 技术设计

### 表结构

**`catalog_tag_category`** — 标签目录（分类树）

| 列 | 类型 | 说明 |
|----|------|------|
| id | uuid PK | |
| code | varchar(64) | 分类编码，全局唯一，ASCII，遵循 dim-code-design 约定 |
| name | varchar(128) | 分类中文名 |
| parent_id | uuid FK → self | 多级分类树，根节点为 null |
| sort_order | int | 同级排序 |
| builtin | boolean | 是否预置分类（预置不可删除，可停用） |
| enabled | boolean | |
| description | varchar(512) | |

唯一约束：`uk_catalog_tag_category_code (code)`
索引：`idx_catalog_tag_category_parent (parent_id)`

**`catalog_tag`** — 标签定义

| 列 | 类型 | 说明 |
|----|------|------|
| id | uuid PK | |
| category_id | uuid FK → catalog_tag_category | 标签归属唯一分类，非空 |
| code | varchar(64) | 标签编码，全局唯一，ASCII |
| name | varchar(128) | 标签中文名 |
| color | varchar(16) | 前端展示色值，可空 |
| builtin | boolean | 预置标签标记 |
| enabled | boolean | |
| description | varchar(512) | |

唯一约束：`uk_catalog_tag_code (code)`
索引：`idx_catalog_tag_category (category_id)`

**`catalog_asset_tag`** — 资产打标关联

| 列 | 类型 | 说明 |
|----|------|------|
| id | uuid PK | |
| tag_id | uuid FK → catalog_tag | 非空 |
| asset_type | varchar(32) | `CatalogAssetType` 枚举名 |
| asset_key | varchar(512) | `CatalogAssetKey` 生成的资产标识 |
| tagged_by | varchar(64) | 打标人 |
| tagged_at | timestamp | |

唯一约束：`uk_catalog_asset_tag (tag_id, asset_type, asset_key)` — 防重复打标
索引：`idx_catalog_asset_tag_asset (asset_type, asset_key)` — 支撑「查某资产的全部标签」
索引：`idx_catalog_asset_tag_tag (tag_id)` — 支撑「按标签检索资产」

### 实体

新建 `domain/catalog/CatalogTagCategory.java`、`CatalogTag.java`、`CatalogAssetTag.java`，
参照同目录 `CatalogClassificationMapping.java` 的既有横切映射表写法（uuid 主键 + `@Column` 显式命名）。

仓储：`repository/catalog/CatalogTagCategoryRepository.java`、`CatalogTagRepository.java`、`CatalogAssetTagRepository.java`。

### 迁移

新建 `src/main/resources/config/liquibase/changelog/20260725_01_catalog_tag.xml`，
并在 `master.xml` 末尾追加 `<include>`（当前最后一条为 `20260724_10_model_artifact_implementation_identity.xml`）。

## 影响范围

- 新增 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/CatalogTagCategory.java`
- 新增 `.../domain/catalog/CatalogTag.java`
- 新增 `.../domain/catalog/CatalogAssetTag.java`
- 新增 `.../repository/catalog/CatalogTagCategoryRepository.java`、`CatalogTagRepository.java`、`CatalogAssetTagRepository.java`
- 新增 `source/dts-platform/src/main/resources/config/liquibase/changelog/20260725_01_catalog_tag.xml`
- 修改 `source/dts-platform/src/main/resources/config/liquibase/changelog/master.xml`（追加 include）
- **不改动** `CatalogDataset.tags` 字段

## 验证

- [ ] RED：先写 `CatalogTagRepositoryIT`，断言三表 CRUD 与唯一约束冲突抛错，运行应失败
- [ ] GREEN：实现实体与迁移后 IT 通过
- [ ] `./mvnw liquibase:update` 在干净库上成功执行
- [ ] 重复打标（同 tag + asset_type + asset_key）触发唯一约束异常
- [ ] 分类树父子关系可正确加载，根节点 `parent_id` 为 null

## 完成标准

- [ ] 三表建成且迁移已注册进 master.xml
- [ ] 三个 Repository 具备基础查询方法
- [ ] IT 覆盖唯一约束与分类树加载
