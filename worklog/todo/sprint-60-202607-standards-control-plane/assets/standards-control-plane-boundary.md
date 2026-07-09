# 标准控制面边界说明

## 为什么不是指导文档

如果标准只停留在治理中心页面，它最多解决“人知道应该怎么做”。DTS 需要的是“系统知道能不能发布”。

标准控制面必须提供四类能力：

| 能力 | 说明 | 消费方 |
|------|------|--------|
| 命名与口径 | 业务术语、别名、定义、版本、归属域 | 业务对象、指标、报表字段 |
| 字段约束 | 数据元、类型、可空、主键、密级、码表 | 资产字段、SQL/dbt 模型、语义维度 |
| 枚举归一 | 公共码表、码值、来源系统映射 | 入湖清洗、dbt seed、指标筛选、报表筛选 |
| 模型生成 | 模型规格、分层、物化形态、刷新策略、质量规则 | ODS/DWD/DWS/ADS、DDL、dbt model、ETL/SQL 骨架 |
| 模板和门禁 | 模型模板、字段模板、命名规则、审核清单 | 低代码候选模型、SQL 建模、发布审核 |

## 当前状态矩阵

| 标准能力 | 已有基础 | 缺口 | 优先动作 |
|----------|----------|------|----------|
| 业务术语 | 列表、版本、评审、引用关系 | 未绑定业务对象/指标口径 | 给业务对象和指标增加术语绑定与版本 |
| 数据元 | 列表、码表引用、引用关系、导入 | 已接 SQL 建模，但指标/维度未完整消费 | 扩展到语义维度、指标依赖、发布门禁 |
| 公共码表 | 目录、码值、映射、seed 同步入口 | 和指标筛选、报表筛选缺少统一引用 | 进入 schema.yml tests 和消费筛选元数据 |
| 标准模板 | 模板字段、命名规则、metadataStandardIds、reviewChecklist | 没有成为低代码/SQL 建模生成输入 | 模板应用到模型候选与审核清单 |
| 标准包 | 术语、数据元、码表一体导入 | 导入后缺“标准就绪度”视图 | 加标准包就绪/影响分析 |
| 模型规格 | SQL/dbt 建模已有字段绑定基础 | 缺标准到 ODS/DWD/DWS/ADS 草稿、DDL、ETL 骨架 | 增加模型规格中间层和 SQL 微调反校验 |

## 推荐数据关系

```text
BusinessTerm
  -> SemanticBusinessObject.glossaryTermId
  -> SemanticMetric.glossaryTermId
  -> ReportDataset.businessTermRefs

MetadataStandard
  -> CatalogColumnSchema.standardId
  -> SqlModel.standardBindings[]
  -> SemanticDimension.metadataStandardId
  -> SemanticMetric.requiredStandardCodes[]

ReferenceCode
  -> MetadataStandard.codeSet
  -> dbt seed
  -> schema.yml relationships test
  -> Metric filter value domain

ModelTemplate
  -> LowCode model candidate
  -> SqlModel initial fields
  -> Publish review checklist

ModelSpecification
  -> ODS/DWD/DWS/ADS layer
  -> Physical DDL / dbt model / schema.yml
  -> ETL SQL skeleton
  -> Contract validation after SQL tuning
```

## 门禁分层

| 层级 | 门禁 | 阻断条件 |
|------|------|----------|
| 资产字段 | 标准映射校验 | DWD 字段未绑定数据元、类型/可空不一致 |
| 模型规格 | 分层建模门禁 | ODS/DWD/DWS/ADS 缺来源、粒度、主键、分区、标准绑定 |
| 物理模型 | DDL/dbt 门禁 | 物理字段与模型规格不一致、删除重建风险未确认 |
| SQL/dbt 模型 | 标准门禁 | 模型字段未绑定数据元、码表字段无标准编码、版本漂移 |
| SQL 微调 | 契约反校验 | SQL 输出字段、类型、血缘或质量规则偏离模型规格 |
| 语义指标 | 口径门禁 | 指标无业务术语、公式字段无标准绑定、粒度不一致 |
| 发布审核 | 消费门禁 | 模型未 APPROVED、指标非 ACTIVE、权限/密级缺失 |
| 运行证据 | 验收门禁 | dbt tests 未跑、报表无数据集、运维实例不可追踪 |

## 页面边界

- 标准管理页：维护、导入、版本、引用、影响分析。
- 资产目录页：展示字段是否落标，提供自动匹配与校验。
- 低代码向导：展示业务对象和指标设计的标准缺口，提供跳转修复。
- SQL 建模页：执行模型规格预览、DDL/dbt/ETL 骨架生成、字段标准绑定、标准门禁、schema.yml 生成。
- 指标工作台：绑定业务术语、维度数据元、指标口径版本。
- 发布审核页：把标准状态作为阻断或警告。

## 不做的事

- 不复制一套新标准中心。
- 不把标准做成静态帮助文案。
- 不允许指标、模型、报表各自维护一套“局部标准”。
- 不在低代码页面直接实现复杂标准维护，只给缺口和修复入口。
- 不把 SQL 编辑器变成绕过模型规格和标准契约的后门。
