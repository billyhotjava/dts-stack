# 标准到物理模型竞品参照

## 结论

成熟数据中台产品通常不是从数据标准直接生成最终业务 SQL，而是分成五步：

```text
数据标准 / 业务术语 / 码表 / 命名规则
  -> 逻辑模型或模型规格
  -> 物理表 / 视图 / 物化视图 / dbt model
  -> ETL/SQL 代码骨架
  -> 人工微调 + 标准契约反校验 + 发布记录
```

这条链路的关键不是“自动生成 SQL”本身，而是让模型在进入开发前已经带着标准契约：字段名、类型、主键、分区、码表、安全级别、来源系统、物化形态、质量规则和发布环境。

## 竞品模式

| 产品/体系 | 官方能力摘录 | 对 DTS 的启发 |
|-----------|--------------|---------------|
| Dataphin | 通过逻辑数据模型自动生成物理模型和 SQL 代码；智能建模可自动生成维度表、事实表、指标对应的物理存储和调度任务。 | DTS 需要把标准包和模板变成逻辑模型/模型规格，再生成物理模型和任务草稿。 |
| DataWorks 智能数据建模 | 逻辑模型可发布并物化为 MaxCompute、Hologres 等引擎中的物理表或视图；维度表、明细表、汇总表、应用表可直接发布物化。 | DTS 需要支持按 ODS/DWD/DWS/ADS 分层生成物理 DDL、视图或 dbt model，并保留发布记录。 |
| DataWorks + Datablau | 支持数据标准、智能引标、正向/逆向 DDL、模型落标监控，并与 DataWorks 开发流程关联。 | DTS 不能只做正向生成，还要支持已有表反向落标、模型和实际物理表差异监控。 |
| DataArts Studio | 逻辑模型描述业务规则，并支持转换为物理模型；物理特性放在物理建模阶段考虑。 | DTS 应把业务标准和物理实现分层：标准管语义和约束，物理模型管引擎、分区、索引、存储策略。 |
| dbt | 模型通过 materialization 持久化为 table、view、incremental、ephemeral、materialized view；model contract 定义模型输出结构，不符合则不构建。 | DTS 的 SQL/dbt 微调必须回到 contract：字段结构、类型、约束、测试和下游消费都要可验证。 |

参考官方资料：

- Dataphin 产品能力：https://cn.aliyun.com/product/dataphin
- Dataphin 规范建模流程：https://www.alibabacloud.com/help/zh/dataphin/semimanaged-v4/use-cases/specification-defines-best-practices
- DataWorks 发布模型：https://help.aliyun.com/zh/dataworks/user-guide/publish-and-materialize-a-table
- DataWorks Datablau 建模能力：https://help.aliyun.com/zh/dataworks/user-guide/overview-7
- DataArts Studio 逻辑模型：https://support.huaweicloud.com/usermanual-dataartsstudio/dataartsstudio_01_0540.html
- dbt materializations：https://docs.getdbt.com/docs/build/materializations
- dbt model contracts：https://docs.getdbt.com/docs/mesh/govern/model-contracts

## DTS 推荐设计

### 1. 标准生成的是模型规格，不是最终 SQL

模型规格是中间事实源，字段建议如下：

| 字段 | 说明 |
|------|------|
| `layer` | ODS / DWD / DWS / ADS |
| `domain` | 主题域或业务域 |
| `businessProcess` | 业务过程，例如下单、发货、回款 |
| `sourceRefs` | 来源系统、库表、接口或文件 |
| `standardRefs` | 数据元、业务术语、公共码表、标准模板 |
| `fields` | 字段名、类型、可空、主键、分区、码表、安全级别 |
| `grain` | 明细粒度或汇总粒度 |
| `materialization` | table / view / incremental / materialized_view |
| `refreshPolicy` | 全量、增量、分区重跑、回溯窗口 |
| `qualityRules` | not_null、unique、relationships、accepted_values、range |
| `reviewChecklist` | 命名、分层、落标、权限、质量、血缘 |

### 2. ODS 不应被设计成“全自动标准化终态”

ODS 的合理产品形态是两层：

```text
ODS Raw
  保留来源结构、来源字段、抽取批次、原始值、源系统时间。

ODS Standardized
  在不改变业务事实的前提下做字段命名、类型归一、码值映射、脱敏标识、主键/分区补齐。
```

这样现场接入速度和治理闭环能兼得。DWD/DWS/ADS 再逐步提高标准门禁强度。

### 3. SQL 微调必须是受控微调

允许人工改 SQL，但保存和发布时要做四类反校验：

| 校验 | 阻断条件 |
|------|----------|
| 字段契约 | SQL 输出字段缺失、类型不一致、主键/分区缺失 |
| 标准契约 | 字段未绑定数据元、码表字段未绑定公共码表 |
| 血缘契约 | SQL 引用了未登记来源，或输出字段无法追踪来源 |
| 质量契约 | 必需 dbt tests / 质量规则缺失或失败 |

### 4. 用户体验应是“先生成，再解释，再允许改”

建议页面流：

```text
选择标准包 / 模板 / 来源表
  -> 生成模型规格草稿
  -> 预览 ODS/DWD/DWS/ADS 分层建议
  -> 预览 DDL / dbt model / schema.yml / ETL SQL
  -> 人工调整字段映射和 SQL
  -> 运行标准门禁和 sample preview
  -> 发布到开发环境
  -> 生成质量规则和发布记录
```

### 5. DTS 的差异化点

- 不只做建模工具，而是把标准、低代码旅程、SQL/dbt、指标、报表发布串成闭环。
- 支持“业务用户先看业务对象和指标缺口，高级用户再进入 SQL/dbt 微调”。
- 物理模型不是终点，后续必须进入指标口径、报表消费和运行证据。
