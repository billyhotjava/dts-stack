# Sprint-7: 数据目录与元数据体系完善

**时间**: 2026-04
**状态**: READY（下一个开始）
**目标**: 补全数据目录与元数据的核心缺口，建立现代湖仓级别的数据资产管理体系，同时重构数据目录 UI/UX，为 Sprint-8 指标中心重构解除阻塞。

---

## 现状与缺口（代码审查后修正，2026-04-05）

### 已实现（不需要重建）

| 模块 | 实际状态 |
|------|---------|
| `CatalogDataset.domain` | `@ManyToOne FK` → `CatalogDomain`，domain_id 外键已存在 |
| `GET /api/catalog/datasets?domainId=X` | 已支持按域 UUID 过滤 |
| `GET /api/catalog/domains/tree` | 已实现，返回层级树 |
| `SubjectAreasPage.tsx` | 左树+右内容框架已有，但资产数硬编码为 `-` |
| `DatasetsPage.tsx` | 列表+多维筛选（域/类型/密级/分层）已有 |
| `AssetDetailPage.tsx` | 列表+抽屉详情已有，**非** Tab 独立详情页 |
| `LineagePage.tsx` | 影响分析表格已有，**无**可视化图 |
| `DataSearchPage.tsx` | 多类型搜索（DATASET/TABLE/COLUMN）已有 |
| `CatalogDatasetLineage` | 上下游血缘实体已有，方向/类型齐全 |

### 真正缺口（需要新建/补充）

| 缺口 | 影响 |
|------|------|
| 无 `GET /api/catalog/domains/{id}/asset-stats` | SubjectAreasPage 资产数显示 `-` |
| 无 `GET /api/catalog/datasets/{id}/fields` | 无法在指标创建时自动补全字段候选 |
| 无 `POST /api/catalog/lineage/import-dbt-manifest` | dbt 血缘无法导入 |
| 无 `GET /api/catalog/datasets/{id}/indicator-deps` | 数据集详情无法显示关联指标 |
| 无 `DatasetPicker.tsx` 组件 | 指标/质量规则中 dataset 仍是自由文本输入 |
| 无 `CatalogDataProduct` 实体 | 跨部门数据共享无数据产品概念 |
| AssetDetailPage 是列表+抽屉，非 Tab 详情页 | 用户无法一站式查看资产全貌 |
| LineagePage 无可视化图 | 血缘关系只能看表格，无法直观理解链路 |

---

## 阻塞解除条件（Sprint-8 依赖项）

| Sprint-8 阻塞点 | Sprint-7 解除方式 |
|----------------|----------------|
| 指标创建时无法选择数据集 | F1: asset-stats API + F2: DatasetPicker + fields API |
| 质量规则 dataset 自由文本 | F2: DatasetPicker 组件可复用 |
| 主题域管理「资产数」显示 `-` | F1: SubjectAreasPage 接入 asset-stats |

---

## Feature 清单

### P0 — 解除 Sprint-8 阻塞（必须先完成）

#### F1: 域资产统计 API + SubjectAreasPage 接入

**目标**: 主题域详情面板显示真实「资产数 / 指标数 / 规则数」，替换硬编码的 `-`。
**无需 DB 迁移**：domain_id FK 已存在，只需统计查询。

| 子任务 | 内容 |
|--------|------|
| T01 | 后端：`CatalogDomainResource` 新增 `GET /api/catalog/domains/{id}/asset-stats`，返回 `{datasetCount, indicatorCount, qualityRuleCount}` |
| T02 | 前端：`SubjectAreasPage.tsx` 选中域时调用 asset-stats API，替换「资产数：-」、「落标率 -」等占位符 |

#### F2: 字段列表 API + DatasetPicker 组件

**目标**: 提供 dataset 选择器组件，选中后自动加载字段候选列表，供指标创建和质量规则复用。

| 子任务 | 内容 |
|--------|------|
| T01 | 后端：`CatalogDatasetResource` 新增 `GET /api/catalog/datasets/{id}/fields`，从 `CatalogColumnSchema` 返回字段名+类型列表 |
| T02 | 前端：新建 `DatasetPicker.tsx`（可复用组件），支持域过滤 + 关键字搜索 + 选中后暴露 `onFieldsLoaded(fields[])` 回调 |
| T03 | 前端：`IndicatorsPage.tsx` / `IndicatorWizard.tsx` 中 sourceTable 输入改为 DatasetPicker，dimensionFields 自动补全候选 |

---

### P1 — 血缘与可观测性

#### F3: dbt 血缘导入

**目标**: 上传 dbt `manifest.json`，解析模型依赖写入 `catalog_dataset_lineage`，实现 ODS→ADS 全链路血缘。
**已有基础**: `CatalogDatasetLineage` 实体、上下游关系结构已完整。

| 子任务 | 内容 |
|--------|------|
| T01 | 后端：新建 `CatalogDbtLineageService`，解析 manifest.json 的 `nodes`/`sources`/`parent_map`，批量 upsert `catalog_dataset_lineage`（通过 hiveTable 名称匹配现有数据集） |
| T02 | 后端：`CatalogDatasetResource` 新增 `POST /api/catalog/lineage/import-dbt-manifest`（`@RequestParam MultipartFile`） |
| T03 | 前端：`LineagePage.tsx` 增加「导入 dbt 血缘」按钮 + 上传对话框，上传成功后刷新当前选中数据集的血缘视图 |

#### F4: 血缘可视化图升级

**目标**: `LineagePage.tsx` 在现有影响分析表格基础上新增可视化图，节点按仓库层级着色，支持上下游高亮。
**已有基础**: `getCatalogLineageImpact` API 返回 nodes/edges，lineage 表结构完整。

| 子任务 | 内容 |
|--------|------|
| T01 | 前端：`LineagePage.tsx` 顶部新增「图视图」Tab，使用 `@antv/g6` 或 `reactflow`（优先检查项目已安装的依赖）渲染 DAG，节点颜色：ODS=灰/DWD=蓝/DWS=青/ADS=绿/指标=紫 |
| T02 | 前端：节点点击时在右侧展示影响分析面板（复用现有 `selectedNode` 状态逻辑） |

#### F5: 指标↔数据集血缘

**目标**: 数据集详情中显示依赖它的指标列表，实现数据→语义层的可追溯性。

| 子任务 | 内容 |
|--------|------|
| T01 | 后端：`CatalogDatasetResource` 新增 `GET /api/catalog/datasets/{id}/indicator-deps`，查询 `gov_indicator_definition` 中 `source_table` 匹配当前数据集 hiveTable 的指标 |
| T02 | 前端：AssetDetailPage 抽屉详情中新增「关联指标」区块，展示指标名/类型/状态 |

#### F6: Data Product（数据产品）

**目标**: 将一组数据集 + 指标打包为可命名、可授权的数据产品，支撑跨部门共享。

| 子任务 | 内容 |
|--------|------|
| T01 | 后端：新建 `CatalogDataProduct` 实体（name, code, ownerDept, datasetIds JSONB, indicatorCodes JSONB, status, description），Liquibase 迁移 |
| T02 | 后端：CRUD REST `GET/POST/PUT/DELETE /api/catalog/data-products` |
| T03 | 前端：数据产品列表页（卡片视图，状态标签：草稿/已发布/已下线），挂载到 catalog 路由下 |

---

### P_UX — 数据目录 UI/UX 全面重构

**目标**: 重构现有 6 个 catalog 页面，达到 Databricks Unity Catalog 级别的交互水准。
**原则**: 在现有组件基础上升级，不推倒重建。

#### F7: DatasetsPage — 资产门户化改造

**当前状态**: 列表+筛选栏（顶部）+ 对账回归清单（下方卡片）。

| 子任务 | 内容 |
|--------|------|
| T01 | 将现有顶部水平筛选栏改为「左侧域树（280px Sider）+ 右侧内容区」布局，域树数据复用 `GET /api/catalog/domains/tree` |
| T02 | 右侧顶部新增全局 Spotlight 搜索框（宽输入框，回车触发），替代现有 keyword input |
| T03 | 对账回归清单移到折叠面板（默认折叠），不占主视图空间 |
| T04 | 资产列表改为卡片网格（3列）：每卡显示名称、域标签、仓库分层、密级、最后更新时间、健康状态 |

#### F8: AssetDetailPage — 转为 Tab 独立详情页

**当前状态**: 列表+抽屉（detail in Drawer）。

| 子任务 | 内容 |
|--------|------|
| T01 | 检查路由配置，确认 `/catalog/datasets/:id` 路由是否存在；如不存在则添加并将 AssetDetailPage 改为独立 URL 页面 |
| T02 | 将 Drawer 内容重构为 antd `<Tabs>`：概览 / 字段详情 / 血缘图 / 治理健康 / 权限申请 |
| T03 | 「概览」Tab：现有 profileForm（owner/tags/description）+ 域/类型/密级/分层 展示 |
| T04 | 「字段详情」Tab：改用 `GET /api/catalog/datasets/{id}/fields`（F2 新增），不再依赖 OpenMetadata tech metadata |
| T05 | 「血缘图」Tab：嵌入 LineagePage 血缘图组件（mini 尺寸，固定当前 datasetId），「查看完整血缘」跳转到 LineagePage |

#### F9: LineagePage — 图可视化替换表格

**当前状态**: 选择数据集 → 触发 `getCatalogLineageImpact` → 展示 nodes 表格 + edges 表格。

> **注意**: F4 T01 已完成图组件本体，F9 是继续完善 LineagePage 全页面 UX。

| 子任务 | 内容 |
|--------|------|
| T01 | 图布局改为自动层次化（dagre layout）：ODS 最左，指标最右，列颜色与 F4 T01 一致 |
| T02 | 节点 tooltip：悬停显示 db.table、仓库层级、最后同步时间 |
| T03 | 「影响分析」侧边面板：点击节点后展示该节点的 downstream 指标/规则数（调用 F5 T01 的 indicator-deps 端点） |
| T04 | 图导出：右上角「导出 PNG」按钮 |

#### F10: DataSearchPage — 搜索结果升级

**当前状态**: 表格展示 DATASET/TABLE/COLUMN 混合结果，无高亮，无快速预览。

| 子任务 | 内容 |
|--------|------|
| T01 | 将单一表格改为按 assetKind 分类的 Tab 展示（数据集/字段/表）；保留现有 `searchCatalog` API 调用不变 |
| T02 | 结果卡片化：dataset 卡片显示名称（关键字高亮）、域标签、密级、分层标签、字段数量 |
| T03 | 点击搜索结果数据集时跳转到 F8 的 Tab 详情页（`/catalog/datasets/{id}`）而非重定向到 DatasetsPage |

---

### Reserved — 预留字段（仅建列，不实现功能）

| 字段/表 | 用途 | 版本 |
|--------|------|------|
| `catalog_dataset.row_security_policy` VARCHAR | 行级安全策略，部门隔离 | v2.3.x |
| `catalog_dataset.sla_refresh_cron` VARCHAR | SLA 刷新频率声明 | v2.3.x |
| `catalog_dataset_lineage.source_column` VARCHAR | 列级血缘（上游字段） | v2.3.x |
| `catalog_dataset_lineage.target_column` VARCHAR | 列级血缘（下游字段） | v2.3.x |

---

### Deferred — 推迟

| 项目 | 推迟原因 |
|------|---------|
| 工作流审批 UI（线上审批） | `AdminWorkflowConfigClient` 为 stub，BPM 集成待定 |
| SQL 自动解析血缘 | 需要 SQL Parser 集成，独立工作量 |
| OpenMetadata 双向同步 | 现有单向拉取已够用 |

---

## 模块影响范围

| 模块 | 改动类型 |
|------|---------|
| `CatalogDomainResource.java` | + asset-stats 端点 |
| `CatalogDatasetResource.java` | + fields 端点、indicator-deps 端点、dbt-manifest 导入端点 |
| `CatalogDbtLineageService.java` | 新建：解析 manifest.json |
| `CatalogDataProduct.java` | 新建实体 |
| `CatalogDataProductResource.java` | 新建 CRUD |
| Liquibase | data_product 表 + reserved 字段迁移 |
| `SubjectAreasPage.tsx` | + asset-stats API 调用 |
| `DatasetPicker.tsx` | 新建组件 |
| `IndicatorsPage.tsx` / `IndicatorWizard.tsx` | sourceTable 改为 DatasetPicker |
| `DatasetsPage.tsx` | 布局改造（左树+右内容+卡片网格） |
| `AssetDetailPage.tsx` | 改为 Tab 独立详情页 |
| `LineagePage.tsx` | 新增可视化图 Tab |
| `DataSearchPage.tsx` | 搜索结果 Tab 化 + 卡片化 |

---

## 验收标准

- [ ] SubjectAreasPage 选中主题域时显示真实「资产数 / 指标数 / 质量规则数」
- [ ] 指标创建表单 sourceTable 改为 DatasetPicker，选中后 dimensionFields 自动补全字段候选
- [ ] 质量规则 dataset 绑定改为 DatasetPicker
- [ ] dbt manifest.json 可上传并写入血缘图
- [ ] LineagePage 新增可视化 DAG 图，节点按仓库层级着色
- [ ] `/catalog/datasets/{id}` 独立详情页存在，Tab 布局：概览/字段/血缘/治理健康/权限
- [ ] DataSearchPage 搜索结果按类型分 Tab，点击数据集跳转到独立详情页
- [ ] CatalogDataProduct 实体和 CRUD API 可用
- [ ] Sprint-8 DatasetPicker 依赖满足，指标中心可启动实施
