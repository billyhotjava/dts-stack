# DTS v2.2.3 项目健康度数据治理 Demo

[返回 Demo 分类入口](../README.md)

## 1. 目标

本目录用于独立实施“研发项目健康度”数据治理 Demo。Demo 与财务 Demo、客户现有数据完全隔离，不复用、不修改、不删除客户通过离线 Excel 导入的任何 ODS 表，也不继续承接此前项目管理表单需求。

Demo 选择“研发项目健康度”作为中性业务场景，以尽量少的数据表打通以下模块：

```text
数据源
  → Schema 探测与入湖任务
  → Demo ODS
  → 元数据目录与来源确认
  → 业务分类、数据域、业务过程、数据集市与默认建模上下文
  → 业务术语、数据元、码表、计量单位
  → 概念维度与 DIMENSION/FACT/SUMMARY/APPLICATION 四类模型（维度表/明细表/汇总表/应用表）
  → 质量规则与发布门禁
  → 指标
  → 资产、标签、密级、血缘和权限
  → BI/API/数据产品
  → 运行、审计与验收证据
```

## 2. 最小规模

| 对象 | 数量 | 说明 |
|---|---:|---|
| Demo 源表 | 3 | 组织、项目、任务快照 |
| Demo ODS 表 | 3 | 由 DTS 入湖任务创建，不手工混入客户 ODS |
| 业务分类（根） | 1 | 研发项目治理 |
| 数据域（公共层） | 1 | 研发项目治理域 |
| 业务过程 | 1 | 项目健康监测 |
| 数据集市 | 1 | 项目健康分析集市 |
| 默认建模上下文 | 1 | 服务端初始化；规划 UI 未暴露计划维护（缺口） |
| 业务维度定义 | 3 | 日期、组织、项目（无属性，主键在维度表字段声明） |
| ModelSpec | 6 | 3 个 DIMENSION、1 个 FACT、1 个 SUMMARY、1 个 APPLICATION |
| 公共码表 | 3 | 任务状态、风险等级、项目健康状态 |
| 核心治理指标 | 6 | 任务数、完成数、完成率、延期数、高风险数、实际成本 |
| 消费出口 | 3 | 1 个 BI 看板、1 个 API、1 个数据产品 |

## 3. 目录内容

| 文件 | 用途 |
|---|---|
| [01-architecture-and-scope.md](01-architecture-and-scope.md) | 场景边界、模块关系、对象所有权和实施原则 |
| [02-data-governance-design.md](02-data-governance-design.md) | 源数据、标准、码表、维度、模型、质量和指标设计 |
| [03-ui-runbook.md](03-ui-runbook.md) | 按当前 DTS UI 手工实施的顺序、入口和填写值 |
| [04-acceptance-checklist.md](04-acceptance-checklist.md) | 正向、阻断、修复、权限、血缘、消费和运维验收 |
| [06-model-ddl.md](06-model-ddl.md) | 6 个模型的 PostgreSQL DDL、字段注释与页面录入对照表（建表帮助） |
| [05-local-validation-report.md](05-local-validation-report.md) | PostgreSQL/dbt 基线、阻断和修复的本地制品验证结果 |
| [assets/model-field-matrix.csv](assets/model-field-matrix.csv) | 6 个模型的逐字段 UI 录入矩阵 |
| [assets/demo-object-register.csv](assets/demo-object-register.csv) | 手工记录各模块实际 ID、状态和证据路径 |
| [dbt/README.md](dbt/README.md) | 6 个 ModelSpec 的高级 dbt 实现和阻断级测试 |
| [sql/01-source-bootstrap.sql](sql/01-source-bootstrap.sql) | 建立 3 张隔离源表并写入基线数据 |
| [sql/02-source-dirty-cases.sql](sql/02-source-dirty-cases.sql) | 注入可控脏数据，验证质量阻断 |
| [sql/03-source-remediation-and-increment.sql](sql/03-source-remediation-and-increment.sql) | 修复脏数据并增加新一期快照 |
| [sql/99-source-cleanup.sql](sql/99-source-cleanup.sql) | 仅清理 `it_demo_src` 的显式回收脚本 |
| [evidence/README.md](evidence/README.md) | 验收证据归档规则 |

## 4. 实施顺序

1. 在独立 Demo PostgreSQL 数据库中运行 `sql/01-source-bootstrap.sql`。
2. 按 `03-ui-runbook.md` 完成数据源、Schema 探测、入湖和元数据登记。
3. 完成业务分类、数据集市、建设计划和来源确认。
4. 建立标准、码表、计量单位和 3 个业务维度定义。
5. 依据 `assets/model-field-matrix.csv` 建立 6 个模型，并将 `dbt/` 节点绑定相同 ModelSpec revision。
6. 完成字段标准、密级、质量、dbt 编译测试和发布门禁。
7. 建立指标、BI、API、数据产品和资产授权。
8. 运行脏数据脚本，证明质量规则能阻断；再运行修复脚本，证明恢复和增量处理。
9. 按 `04-acceptance-checklist.md` 归档运行、血缘、权限和审计证据。

## 5. 隔离与安全原则

- 客户 ODS 是只读边界，本 Demo 禁止对客户表执行 `ALTER`、`DROP`、`TRUNCATE` 或回填。
- Demo 源端只允许写入 `it_demo_src` Schema。
- Demo 入湖目标统一使用 `ods_it_demo_` 前缀。
- Demo 模型物理表统一使用 `it_demo_` 前缀。
- 清理脚本与初始化脚本分离，清理脚本只包含明确的 Demo Schema。
- Demo 使用合成姓名和合成项目，不包含客户名称、真实人员、手机号、证件号或业务秘密。
- 数据密级使用平台既有“公开/公共”目录值；密级不使用业务标签代替。
- 当前权限控制面只有 `read/write/export` 粒度，验收材料不得声称已经具备更细的动作权限。

## 6. 当前可执行结论

| 范围 | 结论 |
|---|---|
| 数据源、Schema、入湖任务、元数据、标准、计划、维度和模型草稿 | 可实施 |
| 质量规则、指标、资产治理、权限和消费配置 | 可实施，需以实际运行证据验收 |
| 模型最终物化 | 源码仍存在 profile lease 返回字段契约不一致，修复、打包、部署并实测前不得判定通过 |
| 自动生成完整客户验收包 | 前端仍显式标记测试结果、运行证据和验收包聚合 API 缺口，当前采用手工证据归档 |

本目录是实施与验收底稿，不代表当前环境已经完成配置或运行。

## 7. 重构后对齐（2026-08-07）

数据建模功能重构后，本目录旧操作步骤中的以下对象与入口已变更：

| 旧 | 新 |
|---|---|
| 业务域（`/governance/subjects`） | 业务分类（根）+ 数据域（公共层），入口 `/data-modeling/planning/*` |
| 建设计划（`/modeling/plans`） | 建模空间隐藏为默认单空间，计划由服务端默认上下文提供；规划 UI 暂无计划维护页 |
| 维度属性/主键属性 | 概念维度不再维护属性；主键与维度属性编码在维度表字段层声明 |
| 模型中心（`/modeling/models`） | 维度建模工作台“+”菜单创建维度表/明细表/汇总表/应用表 |
| 指标工作台（`/modeling/metric-workbench`） | `/data-modeling/metrics/*` |
| 血缘（`/catalog/lineage/*`） | `/data-modeling/graphs/*` |

手工操作以 [03-ui-runbook.md](03-ui-runbook.md) 更新后的章节为准（尤其第 6、7 章的概念维度与维度表创建步骤）。
