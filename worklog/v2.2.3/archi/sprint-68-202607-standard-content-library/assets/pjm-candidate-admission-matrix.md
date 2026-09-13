# PJM/dbt 候选准入矩阵

## 1. 审计范围

候选根目录：`worklog/v2.2.3/s10/v4/pjm`。

优先事实源：

1. `dbt_model/models/pm_sources_v2.yml`
2. `dbt_model/models/pm_stg_v2.yml`
3. `dbt_model/models/pm_schema_v2.yml`
4. `dbt_model/models/stg|dwd|dws|ads/*.sql`
5. `dbt_model/model-governance.md`
6. `metric-registry.json` 与 `metric-handbook.md`，仅用于指标边界核对

## 2. 预审事实

- `models.tsv` 中 STG、DWD、DWS、ADS 和 dim 均为 `DRAFT`。
- `pm_schema_v2.yml`、`pm_stg_v2.yml` 的关键测试多配置为 `severity: warn`。
- 当前进入完整语义链路的 ODS 只有进度、质量、技术状态、风险和预算 5 类；跟进措施、重要物料等仍注明“待后续纳入”。
- 预算域是无时间轴的当前快照，不能被包装成通用月度预算事实。
- Alias dim 中存在上下文相关和有损归并，不能直接等价为公共码表。

因此，第一阶段完成证明“模型链路已形成”，不证明“内容可直接进入 DTS 通用基线”。

## 3. 候选矩阵

| PJM 来源 | 示例 | 初始结论 | 可进入位置 | 主要条件/理由 |
|---|---|---|---|---|
| Canonical dim：风险等级 | 高/中/低 | CONDITIONAL | 项目管理扩展包 | 语义较通用，但需冻结排序、定义和适用范围 |
| Canonical dim：风险分类 | 技术/进度/成本/设计/质量/其他 | CONDITIONAL | 项目管理扩展包 | 分类体系不是所有行业统一真值 |
| Canonical dim：节点类型 | 一般/重要/重大/里程碑 | CONDITIONAL | 项目管理扩展包 | 仅项目计划节点语境成立 |
| Canonical dim：完成情况 | 正常待完成、超期已完成等 | CONDITIONAL | 项目管理扩展包 | 强依赖 PJM 的延期/变更组合逻辑 |
| Canonical dim：质量状态/原因 | 归零状态、设计/工艺/外协等 | CONDITIONAL | PJM/质量扩展包 | “归零”及原因分类具有行业语境 |
| Canonical dim：技术更改/签署 | I/II/III、评审签署状态 | CONDITIONAL | PJM 技术状态扩展包 | 上下文强，必须经业务专家审核 |
| Alias dim：风险等级、节点类型 | 高风险→高、里程碑→里程碑节点 | CONDITIONAL | `reference-code-mappings` | 必须保留 `source_system=PJM_V4` |
| Alias dim：布尔 | 已完成→是、已签署→是 | REJECT | 不导入 | 把业务状态压成布尔会丢失上下文 |
| Alias dim：风险分类 | 供应链/管理/资源→进度 | REJECT | 不导入通用包 | 有损归并，会污染其他客户分类 |
| Alias dim：签署状态 | context + raw → canonical | CONDITIONAL | PJM 专属映射 | 只有保留 context 才可用；当前 05 CSV 不支持复合上下文 |
| ODS 字段映射 | Excel 表头→ODS 列 | CONDITIONAL | 候选抽取输入 | 不能整表导入；需去掉“下拉选择/格式”噪声并做去重 |
| STG 字段 | `*_raw`、source_row_id | REJECT | 不导入 | 技术层字段，不是业务标准正文 |
| DWD 通用字段候选 | project_no、dept、owner、日期、金额 | CONDITIONAL | 通用/项目管理数据元 | 需证明跨域复用、统一类型并去重 |
| DWD 派生字段 | is_overdue、submit_month 等 | CONDITIONAL | 模型派生或指标 | 默认不作为原始数据元；必须声明派生规则 |
| DWS/ADS 指标 | 完成率、健康分、预算执行率 | REJECT（标准模块） | 指标注册表 | 指标正文和公式由指标模块持有 |
| 预算三本账字段 | 预算、预付、账面成本、应付 | CONDITIONAL | 项目预算扩展包 | 当前是快照口径；不得宣称通用会计标准 |
| `target/`、`logs/` | manifest、compiled SQL、dbt.log | REJECT | 不导入 | 可重建中间产物或运行日志 |
| `test/` CSV/XLSX/SQL | 测试行、生成脚本 | REJECT | 不导入 | 测试数据不是标准内容 |
| `screen-instances/`、BI HTML/图片 | 页面制品 | REJECT | 不导入 | 消费层展示，不是标准事实源 |
| ZIP、`.bak` | pjm-dbt-model.zip、旧看板备份 | REJECT | 不导入 | 发布/备份中间产物，且可能重复 |

## 4. 数据元准入细则

PJM 字段只有同时满足以下条件才可进入候选：

- 去掉 `raw`、`source_`、ODS 表名、Excel 格式提示和报表序号后仍有明确业务含义；
- 中文名与英文名一一对应，不使用客户缩写；
- 出现在至少两个事实域，或属于经评审确认的项目管理基础主数据；
- 数据类型、长度、精度、可空性和安全等级有证据，不从 ODS 的全 `varchar` 直接照搬；
- 若引用码表/单位，其稳定键在同包或依赖包中存在；
- 与现有 DTS 数据元按 `field_name_en + domain`、中文同义词和定义相似度去重；
- 不包含具体项目、研究室、人员、供应商或测试行数据。

## 5. 码表准入细则

- Canonical dim 才能成为码表目录/码值候选。
- Alias dim 只能成为来源映射，不得与 canonical 码值混装。
- 有损 many-to-one 映射必须有业务规则、来源系统和适用上下文；默认拒绝通用包。
- SQL 中的 boolean flags、排序和严重程度不是独立码表，按扩展属性处理。
- `accepted_values` 只证明模型当前接受这些值，不自动证明它们是国家、行业或 DTS 通用标准。

## 6. 晋升门禁

| Gate | 通过证据 |
|---|---|
| G1 治理状态 | 候选模型/维度从 DRAFT 经过评审晋升，留存审批记录 |
| G2 阻断测试 | unique/not_null/accepted-values/reference tests 以 error 级通过 |
| G3 血缘 | source→STG→DWD 可追溯；只来自 UI/看板的词拒绝 |
| G4 去重 | 与 DTS 基线和包内稳定键无未解释重复 |
| G5 适用性 | 通用核心或 PJM 扩展范围有明确结论 |
| G6 内容审查 | 无测试行、客户值、技术元数据、生成物或未授权标准正文 |

未通过任一 Gate 的候选不得写入发布用 `standard-packages`。
