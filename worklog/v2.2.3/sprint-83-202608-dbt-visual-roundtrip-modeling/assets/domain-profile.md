# 领域画像（Gate G0）

**勘察日期**：2026-08-01  
**数据来源**：当前源码与 Sprint-81 架构；尚未取得客户脱敏 dbt 项目  
**结论**：存在影响核心设计的未决项，Sprint 保持 DRAFT。

## 1. 统一语言

| 术语 | 定义 | 禁用/易混词 | 出处 |
|---|---|---|---|
| 高级建模 | 针对一个 canonical ModelSpec 的 DBT_MANAGED 技术实现编辑模式 | 旧 SQL 模型中心、共享 dbt 文件管理 | 用户需求 + Sprint-81 |
| 逆向建模 | 从既有技术结构形成 canonical ModelSpec DRAFT 的受控过程 | 导入后直接发布、自动猜业务语义 | 用户需求 |
| 逻辑模型 | ModelSpec revision 中的业务语义、粒度、字段角色和治理绑定 | manifest node、物理表 | Sprint-81 |
| 实施版本 | 与 ModelSpec revision 绑定的 immutable Implementation Revision | 共享文件当前值 | Sprint-81 |
| 结构投影 | 从 ModelSpec/dbt artifact 生成的只读表、字段、依赖视图 | 第二套模型台账 | 本 Sprint |
| 运行观测 | candidate 固定运行产生的 manifest/catalog/relation evidence | 逻辑设计真值 | Sprint-81 |
| DBT_MANAGED | SQL/Jinja 是技术源，结构投影只读，业务语义由 ModelSpec 管理 | SQL 模型实体 | 现有 ModelSpec contract |
| DESIGNER_GENERATED | 设计器结构是技术源，dbt SQL/YAML 是确定性生成物 | 手工编辑生成 SQL | 现有 ModelSpec contract |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 |
|---|---|---|---|
| BI-01 | 一个模型修订必须精确绑定一个实施修订和 checksum 后才能发布 | 架构硬约束 | 视图与运行制品漂移 |
| BI-02 | 导入只能落 DRAFT，不能绕过 StageGate/ReleaseCandidate | 治理硬约束 | 未治理模型被发布 |
| BI-03 | DBT_MANAGED 技术结构只通过 SQL/Jinja 变更；画布不隐式反写 | 业务规则 | SQL/画布双向覆盖、不可复现 |
| BI-04 | 外部和内部均变化时必须 CONFLICT | 业务规则 | 丢失内部语义或外部实现 |
| BI-05 | 物理数据预览必须绑定已成功物化证据并执行权限/密级/脱敏 | 合规硬约束 | 越权或敏感数据泄露 |
| BI-06 | 审计不得保存完整 SQL、ZIP 内容、变量或凭据 | 合规硬约束 | 敏感信息进入中央日志 |

## 3. 真实数据画像

目前没有客户 dbt 包，不能声明 dbt Core/manifest/adapter/宏复杂度兼容范围。

已确认的只是现行代码保护上限，不是客户数据画像：

| 指标 | 当前实现约束 | 证据 |
|---|---:|---|
| 单 SQL 文件 | 2 MiB | `DbtSourceProjectModelPackageAdapter.java:52-54` |
| 全部模型 SQL | 16 MiB | 同上 |
| 宏总量 | 4 MiB | 同上 |
| 图节点 | 500 | `...:56` |
| 图边 | 10000 | `...:57` |
| 图深度 | 128 | `...:58` |
| 单次 apply 选择数 | 200 | `ModelSpecImportApplyContract.java:11` |
| 物理预览行数 | 500 | `DbtPreviewService.java` |

### 编码前必须归档的脱敏样本

| Fixture | 必须包含 | 用途 |
|---|---|---|
| FX-01 artifact-rich | dbt_project.yml、models SQL、schema/source YAML、target/manifest.json、target/catalog.json | 完整结构投影与导入 happy path |
| FX-02 source-only | dbt_project.yml、models SQL、literal ref/source、无 target | 静态解析能力和语义补全 |
| FX-03 complex/blocked | macro、var/env_var、dynamic ref、adapter dispatch、ephemeral | BLOCKED/STRUCTURE_VIEW_ONLY 边界 |
| FX-04 drift | 同项目 base/current/incoming 三个版本 | SKIP/UPDATE/CONFLICT/reimport |
| FX-05 malicious | Zip Slip、压缩炸弹、nested archive、profiles/secrets | 安全和日志脱敏 |

每个样本需实测：文件数、总大小、模型/source/test/macro 数、节点/边/深度、dbt Core/manifest schema、adapter、动态表达式比例、缺失字段率和解析耗时。

## 4. 外部边界

| 系统 | 我方契约 | 所有权 | 失败策略 |
|---|---|---|---|
| 外部 dbt 项目 | ZIP revision snapshot | 客户/外部团队 | inspect/preview fail-closed，不执行模型 SQL |
| Airflow/dbt | DbtExecutionGateway | 执行面 | UNKNOWN/FAILED_STALE 对账，不伪成功 |
| Catalog | CatalogAssetKey/Type owner port | Catalog 域 | DRAFT 不登记可消费资产 |
| Quality | immutable evidence port | Quality 域 | evidence 缺失则 StageGate BLOCKED |
| dts-admin | audit outbox/动作字典 | 中央审计 | 投递重试/DLQ，不降级为普通日志 |

## 5. 合规要求

| 要求 | 硬门槛 |
|---|---|
| ZIP/SQL/临时文件不得落不受控宿主机明文目录 | 是 |
| SQL/ZIP/变量/凭据不得进入审计与应用日志 | 是 |
| 查看物理样例数据必须权限、密级、脱敏 fail-closed | 是 |
| 跨租户 model/project/run 查询返回 404/403，不泄露存在性 | 是 |
| 所有写操作进入公共审计与 dts-admin 字典 | 是 |

## 未决问题

- 客户实际 dbt Core、manifest schema 与 adapter 分布。
- packages、macro、seed、snapshot、exposure、metric、group、contract 的首期资源范围。
- 部分成功能否满足客户业务预期，是否要求用户级“整包撤销”。
- 客户所说“可视化后的表”是否包含物理样例数据；当前架构建议包含，但独立于结构视图。
