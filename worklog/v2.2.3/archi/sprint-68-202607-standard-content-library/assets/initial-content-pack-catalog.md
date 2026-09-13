# Sprint-68 首批内容包目录

以下数量是内容建设目标区间，不是为了凑数的完成标准。每条内容仍需来源、许可、重复和质量门禁。

| 包编码 | 名称 | 主要内容 | 目标规模 | 默认画像 | 来源策略 |
|---|---|---|---:|---|---|
| `dts-core-common-enum` | 通用状态与布尔 | 是/否、启停、有效性等最小通用枚举 | 5～10 张码表 | 通用企业版 | DTS 自有定义 |
| `dts-core-person` | 自然人基础 | 性别、婚姻、民族、学历、证件类及关联数据元 | 8～12 张码表，60～100 数据元 | 通用企业版 | 授权/合法来源 + DTS 定义 |
| `dts-core-organization` | 机构基础 | 机构、部门、经济类型、社会信用相关数据元 | 6～10 张码表，60～100 数据元 | 通用企业版 | 授权来源 + DTS 定义 |
| `dts-core-region-cn` | 中国行政区划 | 省、市、县三级代码与层级关系 | 约 3,000～3,500 码值 | 通用企业版 | 经许可的权威结构化来源 |
| `dts-core-international` | 国际基础 | 国家/地区、币种、语种 | 3～5 张码表 | 按需 | 经许可的权威结构化来源 |
| `dts-core-industry-economy` | 行业与经济分类 | 国民经济行业、企业规模等 | 1,000～1,600 码值 | 通用企业版 | 官方可合法再分发数据或授权 |
| `dts-core-measurement-si` | SI 与常用计量单位 | 量纲、单位、符号、基准单位、换算 | 100～200 单位 | 通用企业版 | 授权标准数据 + DTS 审核 |
| `dts-core-time-audit-elements` | 时间与审计数据元 | 日期、时间、期间、创建/更新/来源追溯 | 50～100 数据元 | 通用企业版 | DTS 自有定义 |
| `dts-core-business-elements` | 通用业务数据元 | 人、组织、地址、联系、金额、状态等 | 300～500 数据元 | 通用企业版 | 多来源审核、去重后自有表达 |
| `dts-core-business-terms` | 通用参考术语 | 客户、供应商、员工、项目、合同、订单、资产等 | 150～300 术语 | 通用企业版 | DTS 参考定义，默认 REFERENCE |
| `dts-core-governance` | 治理与安全基础 | 安全等级、敏感类别、质量/生命周期状态 | 8～15 张码表，50～100 数据元 | 通用企业版 | DTS 产品策略 + 合规评审 |
| `pjm-project-management-reference` | 项目管理参考扩展 | 通过准入的项目、进度、风险、质量、预算术语/数据元/映射 | 受准入报告约束 | 项目管理版 | PJM/dbt 候选 + 人工评审 |

## 默认安装画像

### 通用企业版

默认安装：

- `dts-core-common-enum`
- `dts-core-person`
- `dts-core-organization`
- `dts-core-region-cn`
- `dts-core-industry-economy`
- `dts-core-measurement-si`
- `dts-core-time-audit-elements`
- `dts-core-business-elements`
- `dts-core-business-terms`
- `dts-core-governance`

### 项目管理版

在通用企业版之上增加：

- `pjm-project-management-reference`

### 最小治理版

只安装：

- `dts-core-common-enum`
- `dts-core-measurement-si`
- `dts-core-time-audit-elements`
- `dts-core-governance`

## 内容状态规则

- 公共码表和单位：来源审查通过后可为 `ACTIVE`。
- 通用数据元：可为 `ACTIVE`，但客户责任部门和来源系统不得由 DTS 伪填。
- 通用业务术语：默认 `REFERENCE` 或 `DRAFT`，由客户确认后转 `ACTIVE`。
- PJM 候选：未通过全部 Gate 时只能存在于准入报告，不得进入发布包。
