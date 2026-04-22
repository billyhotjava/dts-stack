# Sprint-13 实现摘要

## 本次实际交付

### 1. 平台语义契约发布

- `dts-platform` 新增 `SemanticContractPublishService`
- `ModelingSqlModelResource` 新增 `POST /api/modeling/sql-models/{id}/semantic/publish`
- SQL 模型页新增“发布到 Analytics”按钮

### 2. Analytics 语义层后端

- 新增语义模型 / Join / 虚拟数据集实体与仓储
- 新增 Liquibase `0046_semantic_layer.xml`
- 新增 `SemanticResource`
- 扩展 `SemanticPublishResource`
- `SemanticQueryService` 已支持：
  - meta / graph
  - preview-sql / query
  - VDS CRUD
  - promote 预览

### 3. 语义卡片执行链路

- `CardResource` 支持 `dataset_query.type=semantic`
- `PublicResource` 支持公开语义卡片执行
- 语义卡片详情页、公开链接、导出链路复用统一结果结构

### 4. 前端工作台

- 新增 `/bi/explore`
- 新增 `/bi/card/new`、`/bi/card/:id/edit`
- 新增 `/bi/virtual-datasets`、`/bi/virtual-datasets/:id`
- 新增语义 Explore 页、语义 Card Editor、VDS 列表页
- 老 `/bi/questions/:id/edit` 增加分流页，兼容旧卡片编辑

### 5. 旧入口切换

- `IndicatorsPage` “新增指标”跳转到 `/bi/card/new`
- `HomePage`、`CardsPage`、`ModelsPage`、`CollectionsPage`
  的新建入口切换到语义卡片

## 本次未展开

- 查询并发限流
- 慢查询熔断和统计面板
- Git PR adapter 的真实仓库对接
- Chrome 95 现场证据补录
