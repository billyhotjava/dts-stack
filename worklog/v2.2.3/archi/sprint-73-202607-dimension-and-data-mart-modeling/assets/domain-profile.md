# 领域画像 (Gate G0)

**勘察日期**: 2026-07-26  
**数据来源**: 当前 v2.2.3 运行库 `dts_platform`（本地部署真实数据；不是客户生产库）  
**结论**: 可冻结术语和契约；生产容量与存量重复关系仍需 F0/T02 补测

## 1. 统一语言

| 术语 | 定义 | 禁用/同义词 | 出处 |
|------|------|-------------|------|
| 业务分类 | 模型的业务语义 owner，内部复用 `catalog_domain` | “数据域/主题域”仅作帮助同义词，不新建实体 | Sprint-67 + 当前菜单 |
| 数据集市 | 面向具体应用、主题或消费场景的一组交付范围，可跨多个业务分类 | 不能称为资产目录、资产台账 | DataWorks 参考 + 本 Sprint ADR |
| 业务维度 | 业务分析视角及其语义属性/层级 | 不能等同源表或物理表 | DataWorks 维度文档 |
| 维度表 | 引用一个业务维度 revision 的逻辑模型及实现配置 | 不能称为“维度定义” | 当前 ModelSpec 契约 |
| 资产台账 | 对已登记/发布资产进行核验、治理和消费的控制面 | 不能承担建模前规划 | 当前 `/catalog/assets/ledger` |
| 维度历史处理 | SCD NONE/TYPE1/TYPE2 | 禁用笼统“历史保留” | 当前 `ModelSpecScdPolicy` |
| 数据保留期限 | 物理数据保存天数 | 不等于模型 revision 历史 | DataWorks 生命周期说明 |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 | 出处 |
|---|--------|----------|----------|------|
| 1 | 业务分类、数据集市、资产台账三者不得共用一个实体或页面真值 | 架构规则 | 归属、规划和资产状态互相污染 | DTS A4 + ADR-01 |
| 2 | 每张 DIMENSION ModelSpec 必须引用一个确定 revision 的业务维度 | 业务规则 | 逻辑表无法解释分析语义 | Sprint-67 |
| 3 | 一个业务维度可有多个维度表实现，但同计划/集市/variant 默认只允许一个活动实现 | 业务规则 | 重复建设和下游引用歧义 | ADR-04 |
| 4 | 概念维度不要求源表；进入实现前必须有来源/上游/生成策略之一 | 业务规则 | 过早阻断概念设计或产生不可构建模型 | ADR-05 |
| 5 | DATA_MART 范围维度必须锚定一个业务分类，且集市必须关联该分类 | 业务规则 | 集市范围失去语义 owner | ADR-03 |
| 6 | 物理表名、装载方式、SCD、保留期分别校验 | 产品规则 | 用户无法判断门禁修复位置 | ADR-07 |
| 7 | 只有 PUBLISHED 的物理实现进入资产台账 | 架构规则 | 草稿被误当可消费资产 | ADR-09 |
| 8 | 密级和业务范围严格分离 | 合规硬约束 | 数据集市被误用为密级标签 | DTS D1 |

## 3. 真实数据画像

实测命令：

```sql
select count(*) from catalog_domain;
select count(*) from catalog_dataset;
select count(*) from modeling_warehouse_plan;
select count(*) from modeling_dimension_definition;
select count(*) from modeling_model_spec where model_type='DIMENSION';
```

| 指标 | 实测值 | 设计影响 |
|------|--------|----------|
| 业务分类数 | 6 | 当前样本小，仍采用分页/树懒加载兼容生产 |
| 分类编码空值 | 2/6 | DataMart 关联使用 UUID，不依赖可空 code；F0/T02 画像生产空值 |
| 分类负责人空值 | 6/6 | 数据集市 owner 必须来自人员目录，不能继承当前自由文本空值 |
| 有父分类的分类 | 2/6 | 数据集市不能作为分类树子节点，否则无法跨分类 |
| 资产数 | 503 | 证明资产台账已有实际资产，但不能据此推断数据集市 |
| WarehousePlan | 2 | 计划关联迁移量小，但生产库仍需 dry-run |
| DimensionDefinition | 1（CURRENT=1） | 新字段必须向后兼容并默认 DOMAIN |
| DIMENSION ModelSpec | 1 | 存量实现需兼容 |
| 已绑定 DimensionDefinition 的 DIMENSION ModelSpec | 0/1 | 存量迁移不能用 NOT NULL 直接阻断，需人工映射清单 |
| 单维度最大 ModelSpec 引用数 | 0 | 当前数据无法验证 1:N 真实分布，F0/T02 必测 |
| DataMart 表 | 0 | 证实是缺失能力，不是隐藏入口 |
| `attributes_json` 列 | 0 | 需 expand migration |
| `modeling_model_spec.data_mart_id` 列 | 0 | 需 expand migration |
| 分类重复非空 code 组 | 0 | 可安全设计新编码约束，但不能直接收紧旧表 |

**对设计的直接影响**：

- `CatalogDomain.code/owner` 存在空值，DataMart 不得以分类 code/owner 作为外键或默认责任人。
- 当前 DIMENSION ModelSpec 尚未绑定维度定义，迁移必须输出 unresolved 清单，不允许自动猜测。
- DataMart 与分类采用独立对象 + 多对多关系，不能利用现有 parentId 模拟。

## 4. 外部边界

| 系统 | 契约 | 可用性 | 失败降级 |
|------|------|--------|----------|
| 人员目录 | ownerId 选择 | 当前未实测 | 禁止手填未知账号；保存草稿可保留当前已解析值 |
| 元数据/数据连接 | 已验证 source binding、schema、table、field | 当前来源能力存在 | 来源不可用时允许概念/草稿，阻断实现 |
| DataWorks 文档 | 产品设计参考 | 2026-07-26 可访问 | 仅作参考，不成为运行依赖 |
| 资产目录 | 发布后的统一资产注册 | 当前资产 503 | 注册失败标 PARTIAL，发布证据不可伪造成功 |

## 5. 合规要求

| 条款 | 要求 | 是否验收硬门槛 |
|------|------|----------------|
| 租户隔离 | DataMart、维度、ModelSpec 查询和写入均按 tenant fail-closed | 是 |
| 权限 | 沿用当前 read/write/export 粒度；细粒度动作是已知缺口 | 是 |
| 审计 | DataMart 创建、更新、确认、退役、计划绑定须登记审计动作 | 是 |
| 密级分离 | 数据集市/业务分类不得承载密级语义 | 是 |

## 未决问题

- 客户生产库 DataMart 候选数、每集市分类数、维度属性数、维度表字段数和重复实现分布。
- 存量未绑定维度定义的 DIMENSION ModelSpec 应映射到哪个 definition，必须由 dry-run 输出人工清单。
- 人员目录 owner 选择接口的现网可用性与权限范围。
