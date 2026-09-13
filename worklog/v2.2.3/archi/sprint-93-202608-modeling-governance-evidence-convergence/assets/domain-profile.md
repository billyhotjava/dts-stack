# 领域画像（Gate G0）

**勘察日期**：2026-08-16
**数据来源**：本地运行环境 `dts_platform` 数据库、当前源码与 Sprint-86～91 文档；不是客户生产画像
**结论**：统一语言和核心不变量可据此设计；真实登录与 normalization preview 已验证。2026-08-17 已补齐 308/367 条语义投影并使 43 条服务投影全部收敛到 `SYNCED`；剩余 59 条、真实字段血缘、质量通过样本、Chrome 95 和隔离验收仍须在集中 E2E 中验证。

## 1. 统一语言

| 术语 | 定义 | 禁用/区分词 | 出处 |
|---|---|---|---|
| 物理数据资产 | 可稳定寻址的表、视图或物化视图；拥有 DATASET AssetKey | 临时表、ephemeral 节点不是资产 | Sprint-86 D01/D02 |
| 语义模型资产 | 一个 ModelSpec 的稳定语义身份，修订不可变 | 不等同于物理表 | Sprint-86 D06；`CatalogModelServingService` |
| 业务归属数据域 | Catalog 资产的业务归属，字段为 `domainId` | 禁止简称“主题域” | Sprint-86、资产治理边界 |
| 应用主题域 | 建模应用层的 `subjectDomainId` | 不得映射为 Catalog `domainId` | Sprint-86 |
| 工程质量 | dbt compile/build/test 与构建制品证据 | 不等于治理数据质量 | 当前模型 lifecycle |
| 治理数据质量 | 已发布规则版本、该版本绑定及其真实运行结果 | 模板、latest 漂移、dbt build 不能替代 | `QualityRunService` |
| 资产观察 | producer 对一个稳定关系在某时刻提供的证据 | 不覆盖历史 observation | Sprint-86 D08/D10 |
| 消费资格 | discovery/governance/publication/serving/lifecycle 加质量和权限的综合判定 | `PUBLISHED` 单一状态不能代表可消费 | `CatalogAssetSemanticsContract` |
| 元数据采集 | 表/字段结构、同步、Schema 漂移等技术事实 | 不拥有业务治理台账 | Sprint-89 |
| 业务元数据治理 | 负责人、部门、业务分类、数据域、密级等 DTS 事实 | 不等同于 OM 缓存 | Catalog governance |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 | 出处 |
|---|---|---|---|---|
| I01 | 一个稳定物理关系只能有一个 AssetKey 和一个目录身份 | 架构硬约束 | 重复资产、血缘/质量断链 | Sprint-86；DTS A3/A4 |
| I02 | 语义模型和物理资产分离，以 revision/candidate/observation 连接 | 架构硬约束 | 模型修改覆盖物理历史 | Sprint-86 D06/D08 |
| I03 | 二次物化只新增 attempt/observation，并推进 servingRef | 业务规则 | 重复模型、无法回滚 | 用户确认；Sprint-86 |
| I04 | 治理质量必须钉定 ruleVersion、binding 和 run | 发布硬门槛 | latest 漂移导致错误放行 | Sprint-81 质量桥接设计 |
| I05 | 采集/重跑不得覆盖人工 VERIFIED 血缘 | 治理硬约束 | 人工核验结论丢失 | Sprint-90 |
| I06 | OpenMetadata 故障不能删除 DTS 资产 | 可用性硬约束 | 目录随外部系统波动 | ADR-85-04 / Sprint-89 |
| I07 | 密级与业务标签分离，分类修改必须审计 | 合规硬约束 | 合规语义混乱、审计不完整 | DTS D1/D2 |
| I08 | 所级数据管理员可在质量通过后自助发布；部门管理员仅在所属范围操作 | 权限规则 | 不必要审批或越权 | 20260814 自助发布迁移；用户确认 |
| I09 | Catalog 数据域与建模主题域不得混用 | 领域硬约束 | 资产归属和应用分类串线 | Sprint-86 |

## 3. 当前运行数据画像

以下为 2026-08-16 对本地运行库的只读查询结果，仅用于确认断点和规划迁移，不代表客户生产量级。

| 指标 | 实测值 | 查询口径 |
|---|---:|---|
| `catalog_dataset` | 367 | 全量 |
| 已归业务数据域 | 43 / 367 | `domain_id is not null` |
| 已定密/分类 | 201 / 367 | `classification` 非空 |
| 已分配 owner | 310 / 367 | `owner` 非空 |
| 已分配 ownerDept | 0 / 367 | `owner_dept` 非空 |
| 生命周期 | 367 `PENDING_GOVERNANCE` | 按 `lifecycle_status` 分组 |
| OM 资产缓存/映射 | 0 / 0 | `om_asset_cache` / `catalog_asset_mapping` |
| 五轴语义投影 | 0 | `catalog_asset_semantic_projection` |
| 模型服务投影 | 43，全部 `SYNC_PENDING` | 按 `sync_status` 分组 |
| 当前表级血缘 | 53 | 28 MODEL_DEPENDENCY VERIFIED；20 AUTO_VIEW DECLARED；1 ADDAX DECLARED；2 ADDAX KNOWN_UNVERIFIED |
| 当前字段级血缘 | 0 | `valid_to is null` |
| 治理质量运行 | 119，全部 FAILED | 仅覆盖 1 个 dataset |
| normalization 待处理 | 3 | 均为 SOURCE；3/3 `automatic=true`、0 歧义；preview 连续两次 hash 一致，未 apply |

**实际查询**：

```sql
select count(*) from catalog_dataset;
select sync_status, count(*) from modeling_catalog_model_serving_projection group by sync_status;
select relation_type, verification_status, count(*)
  from catalog_dataset_lineage where valid_to is null group by relation_type, verification_status;
select status, count(*), count(distinct dataset_id) from gov_quality_run group by status;
```

**对设计的直接影响**：

- 语义投影不是“小比例缺失”，而是本地全量缺失；必须先 preview 分类，再分批补齐，禁止把 367 行一次性猜测回填。
- 43 条服务投影全部 pending，必须实现消费回执和重试，不接受人工改状态。
- 字段血缘没有运行样本，F4 必须先拿到跳过原因和真实 manifest，不能以 fixture 冒充闭环。
- 质量运行只覆盖一个数据集且全部失败，不能据此验证模型发布质量；F0 必须准备隔离的 pass/fail/expired 样本。
- 概览不能继续通过最多 200 行扫描计算全量统计；应读取统一统计投影并显式展示 asOf。
- 当前 xiezm 默认视图中，概览显示 200、目录显示 286，而数据库为 367；必须先统一访问范围与统计口径，再把数字差异判为数据缺失。

### 3.1 2026-08-17 实现收口复查

本表是对启动基线的追加快照，不覆盖 §3 的历史事实。

| 指标 | 实测值 | 结论 |
|---|---:|---|
| `catalog_dataset` | 367 | 目录身份总量未因实现收口增加 |
| 五轴语义投影 | 308 | 仍有 59 条必须经 preview/issue 处理 |
| 模型服务投影 | 43 `SYNCED` | 最大 `sync_attempts=3`，不再存在运行态 pending |
| 当前表级血缘 | 51 | 最终 IT 按真实 ODS→DWD→DWS→ADS 链对账 |
| 当前字段级血缘 | 0 | 仍是 IT-07 硬阻塞，不得以表级边代替 |
| 治理质量运行 | 134 `FAILED` | 仅覆盖 1 个 dataset，仍缺真实 passing 样本 |

复查使用只读 SQL；未通过 SQL 改写服务投影、资产语义或质量结果。

## 4. 外部边界

| 系统/模块 | 我方契约 | 当前可用性 | 失败降级 |
|---|---|---|---|
| OpenMetadata | 技术元数据同步与缓存 | 容器运行，但 DTS 缓存/映射为 0 | 资产继续由 DTS 目录展示；同步状态显示失败/待同步 |
| dbt/Airflow | 构建、测试、manifest、调用与物理关系证据 | 容器运行；真实字段血缘样本未确认 | 工程质量/字段血缘显示 UNKNOWN，不伪造通过 |
| dts-ingestion | 来源采集和资产观察 producer | 服务健康；尚未接入统一 observation | 采集事实继续保留，语义状态为 CONDITIONAL |
| dts-admin | 菜单、权限与审计字典 | 服务健康 | 新动作未登记则不得上线 |
| Chrome 95 / 真实账号 | 页面最终验收 | xiezm + `ROLE_INST_DATA_OWNER` 已在本地 Chrome 150 完成受保护页面/API smoke；Chrome 95 未执行 | F5/F6 最终验收仍 BLOCKED，不用现代 Chrome 代替 Chrome 95 |

## 5. 合规要求

| 要求 | 是否硬门槛 | 验证方式 |
|---|---|---|
| 业务标签与密级分离 | 是 | DTO/UI/审计契约测试 |
| 写操作按角色和部门范围 fail-closed | 是 | xiezm 正向 + 部门越权负向测试 |
| 观察、回填、同步重试、质量门禁均留痕 | 是 | 审计资源字典与数据库记录断言 |
| 迁移 preview/apply/rollback 可追踪 | 是 | batchId、previewHash、correlationId、rollback 演练 |
| 历史 candidate/run/lineage 不硬删除 | 是 | FK/append-only/valid_to 测试 |

## 6. 未决问题

1. 目标环境资产规模、日增长、分层/关系类型分布和脏数据比例。
2. normalization 待处理子集已确认 3/3 自动解析；367 个存量资产中由真实生产 adapter 提供 producer/evidence 的比例仍未知。
3. 字段血缘缺失的具体原因和可用于验收的真实 dbt manifest。
4. 所级、部门级数据管理员的完整权限矩阵仍待客户确认；本期只消费现有 guard。
5. 目标 Chrome 95 的控制台、网络与布局基线；现代 Chrome 的登录、DNS 和关键路由已通过。
