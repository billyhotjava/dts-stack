# 领域画像（Gate G0）

**勘察日期**：2026-08-01  
**数据来源**：当前源码与 Sprint-81 架构；本地运行时实测；尚未取得客户脱敏 dbt 项目
**结论**：产品政策与通用工程边界可据此设计；工程 fixtures 未通过时，其对应切片保持 DRAFT；运行时认证未通过只让 S3 materialization 保持 DRAFT。客户脱敏包缺失只阻断客户兼容声明和现场验收，不阻断基于重新构造 fixture 的通用实现编码。

## 1. 统一语言

| 术语 | 定义 | 禁用/易混词 | 出处 |
|---|---|---|---|
| 高级 dbt 实现 | 现有模型详情“数据实现”阶段中的显式 DBT_MANAGED 技术视图；不在普通业务可视化中展示 | 旧 SQL 模型中心、独立高级建模列表、共享 dbt 文件管理 | 用户确认 + Sprint-67/81 |
| 逆向建模 | 从既有技术结构形成 canonical ModelSpec DRAFT 的受控过程 | 导入后直接发布、自动猜业务语义 | 用户需求 |
| 逻辑模型 | ModelSpec revision 中的业务语义、粒度、字段角色和治理绑定 | manifest node、物理表 | Sprint-81 |
| 实施版本 | 与 ModelSpec revision 绑定的 immutable Implementation Revision | 共享文件当前值 | Sprint-81 |
| 结构投影 | 从 ModelSpec/dbt artifact 生成的只读表、字段、依赖视图 | 第二套模型台账 | 本 Sprint |
| 运行观测 | candidate 固定运行产生的 manifest/catalog/relation evidence | 逻辑设计真值 | Sprint-81 |
| DBT_MANAGED | 高级/外部 dbt SQL/Jinja 是实施版本的技术源；普通可视化不展示 SQL，业务语义仍由 ModelSpec 管理 | SQL 模型实体、dbt 执行引擎 | 现有 ModelSpec contract |
| DESIGNER_GENERATED | 设计器结构是技术源，dbt SQL/YAML 是隐藏的确定性生成物；仍可通过 dbt 物化 | 不使用 dbt、手工编辑生成 SQL | 现有 compiler contract |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 |
|---|---|---|---|
| BI-01 | 一个模型修订必须精确绑定一个实施修订和 checksum 后才能发布 | 架构硬约束 | 视图与运行制品漂移 |
| BI-02 | 导入只能落 DRAFT，不能绕过 StageGate/ReleaseCandidate | 治理硬约束 | 未治理模型被发布 |
| BI-03 | DBT_MANAGED 技术结构只在显式高级实现中通过 SQL/Jinja 变更；普通可视化不展示或反写 SQL | 业务规则 | SQL/画布双向覆盖、不可复现 |
| BI-04 | 外部和内部均变化时必须 CONFLICT | 业务规则 | 丢失内部语义或外部实现 |
| BI-05 | 物理数据预览必须绑定精确 serving 或成功 candidate evidence，显式加载，并执行 tenant/read、表级密级和列 ALLOW/MASK/DENY；历史 revision 不返回样例行 | 合规硬约束 | 越权、敏感泄露或历史数据错配 |
| BI-06 | 审计不得保存完整 SQL、ZIP 内容、变量或凭据 | 合规硬约束 | 敏感信息进入中央日志 |
| BI-07 | 使用 dbt 物化与 DBT_MANAGED 所有权正交；普通可视化生成模型不得因为选择 dbt 执行而改变所有权 | 架构硬约束 | 物化动作静默改变模型事实源 |
| BI-08 | 重新导入以最近已接受合并检查点、DTS 当前技术实施和新 ZIP 做技术三方比较；ModelSpec 业务语义始终保留并重验映射，缺失/重命名不得自动删除或猜测映射 | 业务规则 | 静默覆盖内部语义或误删模型 |
| BI-09 | PARTIAL 的每个 FAILED/BLOCKED 项必须有稳定失败码、阶段、安全原因、retryable、恢复动作和 correlationId | 可运维硬约束 | 用户无法定位和恢复，审计结果失真 |
| BI-10 | Catalog 的 latestPublishedRef 与 servingRef 独立；PUBLISHED 可发现，只有成功 relation evidence/质量门禁才可切换 serving，失败/stale 保留旧 serving | 架构硬约束 | 未物化资产伪装可消费或新版本失败拖垮旧资产 |

## 3. 真实数据画像

目前没有客户 dbt 包，不能声明客户 dbt Core/manifest/adapter/宏复杂度兼容范围。D09 已冻结兼容判定政策，但没有冻结任何已认证版本范围。工程 fixture 只能证明固定契约行为，不能冒充客户数据画像或客户性能结论。

### 3.1 证据分级与门禁

| 证据层 | 允许来源 | 证明范围 | 不证明 | 门禁影响 |
|---|---|---|---|---|
| `G0-RUNTIME / RT-01` | 仓库内最小 dbt 项目 + 隔离 PostgreSQL | 精确 Core/adapter/transitive/image digest 的 parse/compile/build/run、artifact 与 relation evidence | 客户宏、包结构或数据规模兼容 | 阻断所有 materialization Task；不阻断纯 inspect/import projection |
| `G1-PARSER / FX-01～03` | 重新构造的无敏感 fixture；或固定 commit、许可与归档副本明确的公开项目 | artifact-rich/source-only/complex 的规范化解析、投影、阻断和错误契约 | 客户适配、客户 P95 或现场支持声明 | 只阻断消费对应 fixture 的排期切片进入 READY |
| `G4-REGRESSION / FX-01～05` | 编码后固定的工程、漂移与恶意包矩阵 | 实现回归、安全、恢复和兼容边界没有漂移 | 客户真实分布 | 阻断对应切片 DONE |
| `CUSTOMER-VALIDATION` | 获授权客户脱敏包；无法脱敏时由客户现场运行画像脚本，只回传统计/checksum | 客户版本、宏、资源、复杂度、脏数据与性能声明 | 未采样客户或其他 adapter | 只阻断客户兼容声明、现场验收与客户可见 NFR PASS |

公开 fixture 必须固定 commit、许可证、来源和 SHA-256，并在仓库或离线制品中保存可重复副本；测试和构建不得依赖运行时联网下载。

2026-08-02 完成一次本地运行时观测：

| 指标 | 实测值 | 查询/命令 |
|---|---|---|
| Dockerfile adapter 声明 | `dbt-postgres==1.10.0` | `builds/dts-dbt/Dockerfile:56-58` |
| 镜像实际 dbt Core | `2.0.0-alpha.5` | `docker run --rm dts-dbt:1.10.0 --version` |
| 镜像 adapter/common | `dbt-postgres 1.10.0`、`dbt-adapters 1.24.5`、`dbt-common 1.38.0` | `docker run --rm --entrypoint python dts-dbt:1.10.0 -m pip show dbt-core dbt-postgres dbt-adapters dbt-common` |

**对设计的直接影响**：

- 镜像标签与 adapter 版本不能作为 dbt Core 兼容证明；首个 PostgreSQL profile 必须先精确锁定依赖并真实运行认证。
- `inspection`、`importProjection` 与 `materialization` 分开判定；前两项通过不能绕过 `materialization=CERTIFIED` 门。
- 当前 PostgreSQL 只是首期 materialization adapter 候选，不是已认证承诺；MySQL、达梦及其他 adapter 保持 UNSUPPORTED。

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
| FX-02 source-only | dbt_project.yml、models SQL、literal ref/source；schema YAML 分 enforced+完整 name/data_type、非 enforced/缺 type、无字段三支；无 target | 静态解析、可信完整字段契约、STRUCTURE_VIEW_ONLY 与 apply BLOCKED |
| FX-03 complex/blocked | 自定义 macro 隐藏依赖、var/env_var、dynamic ref、adapter dispatch、ephemeral | 调用 model/downstream BLOCKED 与 TECHNICAL_ONLY 边界 |
| FX-04 drift | 同项目 accepted base/current Implementation/incoming ZIP 三个技术版本，另含 ModelSpec 语义未变、语义变更且映射有效、语义变更且映射失效三组 | SKIP/UPDATE/CONFLICT/BLOCKED_REMAP/reimport |
| FX-05 malicious | Zip Slip、压缩炸弹、nested archive、profiles/secrets | 安全和日志脱敏 |

每个样本需实测：文件数、总大小、模型/source/test/macro 数、节点/边/深度、dbt Core/manifest schema、adapter、动态表达式比例、缺失字段率和解析耗时。

## 4. 外部边界

| 系统 | 我方契约 | 所有权 | 失败策略 |
|---|---|---|---|
| 外部 dbt 项目 | 用户上传 ZIP revision snapshot；P0 不接 Git 或在线 packages | 客户/外部团队 | inspect/preview fail-closed，不执行模型 SQL；原始 ZIP 仅暂存 |
| Airflow/dbt | DbtExecutionGateway | 执行面 | UNKNOWN/FAILED_STALE 对账，不伪成功 |
| Catalog | 既有 CatalogAssetType/Key + latestPublishedRef/servingRef 投影 | Catalog 域 | DRAFT 不登记；PUBLISHED 可发现不可消费；MATERIALIZED 才切换 serving；外部同步 outbox 重试 |
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

- 客户实际 dbt Core、manifest schema 与 adapter 分布，以及第一个精确锁定的 PostgreSQL certification profile。
- 当前 source parser 尚未投影 schema YAML columns，也未可靠传播自定义 macro 隐藏依赖；必须在同一 parser seam 内补齐并由 FX-02/03 验证。
- seed、snapshot、analysis、exposure、metric、semantic model 等均不作为 P0 业务候选；真实包中若存在其依赖关系，仍需画像验证阻断传播是否完整。
- 前向撤销已采用逐项资格校验；仍需通过真实依赖样本验证可撤销率和用户提示是否足够。
- Catalog/样例范围已由 D08/D12 冻结；仍需用真实密级、ALLOW/MASK/DENY 和大字段样本验证响应体积、查询超时与用户提示。
