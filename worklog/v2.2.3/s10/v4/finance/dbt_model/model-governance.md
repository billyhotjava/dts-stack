# Finance 建模治理说明

## 1. 规划关系

本项目采用以下规划基线，数仓分层与业务规划属性保持正交：

```text
业务分类“研究所业务”
└── 数据域 FINANCE“财务管理域”
    ├── 自有资金核算
    ├── 项目经费核算
    ├── 合同辅助核算
    └── 个人辅助核算
```

- 业务分类是可配置的顶层组织边界，不等同于源系统。
- 数据域承载稳定业务主题，不按“一个应用系统一个数据域”机械拆分。
- 业务过程用于描述事实表对应的具体业务活动；维度表不强制绑定业务过程。
- ODS 来源与数据域分别回答“数据来自哪里”和“数据表达什么业务”，不能互相替代。

## 2. 目标架构

```text
ODS -> STG -> DWD -> DWS -> ADS
```

| 层 | 材质化 | 职责 |
|----|--------|------|
| `ODS` | 现有物理表 | 保留原始落地结构，只承担追溯与装载边界 |
| `STG` | `view` | 同行字段清洗、直接类型转换、字段改名和来源元数据 |
| `DWD` | `table` | 维度标准化、业务派生和明细事实沉淀 |
| `DWS` | `table` | 按稳定粒度沉淀主题汇总 |
| `ADS` | `table` | 面向应用场景输出 KPI 和消费口径 |

## 3. STG 层约束

允许：

- 使用 PostgreSQL 原生 `cast` / `::type` 做直接类型转换。
- 使用 `btrim`、`nullif` 收敛空白与空字符串。
- 字段改名为业务语义名，例如 `金额1` → `total_cost`。
- 保留 `source_row_id`、`source_table` 等来源元数据。

禁止：

- `CASE WHEN`、前缀匹配或枚举映射等业务分类。
- 月份、年度、季度等跨字段或格式化派生。
- 复合字符串拆分、跨列算术和业务标签派生。
- 依赖项目私有宏完成基础解析；逆向导入器必须能静态识别字段和依赖。

STG 与 ODS 保持一行对一行，只做同位变换，不做值翻译。

## 4. DWD 维度与事实

### 4.1 维度编码

- 维度主键和关联码必须使用稳定 ASCII code，不使用中文自然值作为连接键。
- `raw_value` 保存源端中文值，`label` 保存展示名称，`code` 用于下游关联。
- 前缀映射表只负责 `prefix → canonical code`，标准值由对应维度表统一定义。

当前维度/映射模型：

- `dim_fund_source`、`dim_fund_category`、`dim_project_status`
- `dim_balance_direction`、`dim_expense_category`、`dim_personal_subject_category`
- `dim_expense_code_prefix`、`dim_personal_subject_code_prefix`

### 4.2 明细事实

```text
stg → normalized（前缀/原始值归一）→ derived（业务派生）→ final（维度关联）
```

- `biz_dwd_own_fund` → 自有资金核算
- `biz_dwd_project_fund` → 项目经费核算
- `biz_dwd_aux_balance` → 合同辅助核算
- `biz_dwd_aux_balance_personal` → 个人辅助核算

## 5. 依赖与命名规则

- `dwd/*` 禁止直接 `source(...)`。
- `dws/*` 禁止直接 `source(...)` 或 `ref('stg_*')`。
- `ads/*` 禁止直接 `source(...)` 或 `ref('stg_*')`。
- DWS/ADS 只消费 `biz_dwd_*` 或 `biz_dws_*`。
- `xxx_raw` 表示清洗后的源值，`xxx_code` 表示稳定编码，`xxx_id` 表示维度/事实主键，`xxx_label` 表示展示值。
- 每个模型必须声明完整中文描述、输出列及 `data_type`，并启用 `contract.enforced: true`。

## 6. 治理原则

- ODS 不承载新业务口径，STG 不引入业务含义。
- 分类、映射和派生口径在 DWD 收敛，并以维度表为唯一真值源。
- DWS 粒度必须明确，ADS 必须对应具体应用场景。
- 同一业务字段只保留一套规范命名；ODS DDL 仅作为部署参考。
