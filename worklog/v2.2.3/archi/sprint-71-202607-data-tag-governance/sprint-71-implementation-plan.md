# Sprint-71 实施计划：数据标签体系与资产打标闭环

## 全局约束

- 以本目录 `README.md` 及 `features/` 下 12 个 Task 文档为验收事实源。
- 不新增菜单；标签管理只进入既有数据资产页「数据标签」Tab，规范深链为 `/catalog/assets?tab=catalog-tags`。
- 资产锚点统一复用 `CatalogAssetType` + `CatalogAssetKey`，不为 dataset 单造关系表。
- 数据标签与 `classification` / `SecurityLevelCatalog` 严格分离，预置标签不含密级语义。
- `CatalogDataset.tags` 保留、不删除、不回写；结构化标签与旧模糊检索兼容并存。
- 多标签筛选采用 AND 语义；列表标签批量加载，禁止 N+1。
- 批量打标单次最多 500 个资产，整批事务回滚。
- 预置内容以 `code` 幂等安装，升级不得覆盖客户改名、停用状态或自定义标签。
- 所有写操作接入既有权限与审计链路；普通用户只读，越权返回 403。
- 全部实现遵循 RED → GREEN → REFACTOR，并保留真实命令输出作为 `it/` 证据。
- 当前工作树含 Sprint-70 未提交修改；不得回退、覆盖或提交这些修改。

## Task 1：领域模型、标签目录与打标 REST 主链

落实 F1/T01~T03 与 F2/T01~T02：

- 建立 `catalog_tag_category`、`catalog_tag`、`catalog_asset_tag`，补充迁移批次标识所需字段/索引。
- 实现实体、Repository、DTO、分类树与标签 CRUD、删除保护、预置标签幂等安装。
- 实现单资产查询/打标/取消和多资产批量打标，覆盖非 dataset 类型。
- 提供标签目录、标签分页和打标 REST 契约；参数与错误响应符合既有项目约定。
- 先补失败测试，再实现并运行覆盖本 Task 的后端测试。

## Task 2：结构化检索、审计权限与存量迁移

落实 F2/T03~T04 与 F4/T01~T02：

- 资产门户/数据搜索接入 `tagIds` 精确 AND 筛选，并在响应中批量附带结构化标签。
- 标签管理、打标、批量打标、迁移与回滚动作写入既有审计链路并登记资源字典。
- 标签定义写操作仅治理角色可用；资产打标沿用现有资产写权限边界。
- 实现存量 `CatalogDataset.tags` 的 dry-run、执行、批次回滚和受限运维端点。
- 根据只读现网探查结果归档 tags 数据统计与字段下线评估，不修改旧字段。
- 先补失败测试，再实现并运行覆盖本 Task 的后端测试。

## Task 3：前端标签管理、资产打标与标签筛选

落实 F3/T01~T03：

- 新增统一标签 API client、标签展示/编辑组件和 `AssetTagPanel`。
- 数据资产页增加「数据标签」Tab，完成分类树和标签 CRUD；元数据管理页只保留资产语义元数据能力。
- 资产详情与数据集详情接入可复用打标组件，并与密级标识明显区分。
- 数据集列表和数据搜索页接入「同时包含」标签筛选、URL-as-state 和行内标签展示。
- 保持既有工具栏结构，不新增菜单；分页默认 10 条且切换 page size 重置页码。
- 先更新 source-contract/组件测试形成 RED，再实现并运行前端测试与构建。

## Task 4：集成验收、证据与 Sprint 收口

- 在可用环境执行数据库迁移、后端组合测试、前端 source-contract、TypeScript 检查和 production build。
- 在登录/DNS 基线可用时完成真实浏览器 CRUD、dataset/非 dataset 打标、标签筛选 URL 复现和 Chrome95 验收。
- 将真实输出、统计和截图登记到 `it/`；无法执行的门禁必须记录明确阻断证据。
- 逐项复核 12 个 Task 与 Sprint 完成标准，更新 Feature/Task/Sprint/queue 状态，状态只反映真实证据。
- 运行 `git diff --check`、GitNexus `detect_changes` 和全量代码审查，修复重要问题后再宣告完成。
