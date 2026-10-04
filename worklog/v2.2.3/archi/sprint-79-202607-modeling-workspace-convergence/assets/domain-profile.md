# 领域画像（Gate G0）

**勘察日期**：2026-07-30  
**数据来源**：当前本地 DTS PostgreSQL `dts_platform` + 已提交源码 + 客户提供 Excel ODS 约束  
**结论**：可据此设计 UI 收敛；客户生产数据量与旧入口访问仍是退役未决项。

## 1. 统一语言

| 术语 | 定义 | 禁用/映射词 | 出处 |
|---|---|---|---|
| 数仓建设计划 | 一次规划范围和阶段进度的 canonical 上下文，标识为 `planId` | 旧 `planningId` 只读兼容 | WarehousePlan |
| 业务维度 | 可复用业务概念及属性目录，版本状态为 CURRENT 后供模型引用 | 不等同维度表 | DimensionDefinition |
| 逻辑模型 | DIMENSION/FACT/SUMMARY/APPLICATION 四类 ModelSpec | 不新增“贴源 ModelSpec” | ModelSpec v2 |
| 数据实现 | 模型的来源、映射、物理设置和 dbt 实现 revision | 不写入逻辑模型正文 | ModelImplementation |
| 发布候选 | 计划范围内构建、质量、审核、发布的唯一控制对象 | 禁止第二套发布任务 | ReleaseCandidate |
| 物理资产 | 发布后且真实关系存在的 CatalogDataset | build 成功不等于物理资产 | CatalogAssetKey |
| 数据指标 | 具备版本、口径、计算逻辑和模型字段引用的治理指标 | 不回写旧 semantic metric | GovIndicator |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 | 出处 |
|---|---|---|---|---|
| D-01 | 客户 Excel ODS 暂时不可改 | 业务硬约束 | 破坏离线导入和客户核对 | 用户上下文 |
| D-02 | 模型、发布、资产各只有一个 owner | 架构硬约束 | 状态冲突、无法治理 | DTS A4 |
| D-03 | 业务维度属性映射到模型字段；主键属性必须对应 KEY 字段 | 业务规则 | 维度模型不能通过阶段门禁 | Dimension/ModelSpec 契约 |
| D-04 | 业务维度属性不重复维护字段数据类型 | 业务规则 | 目录与模型字段漂移 | 原型落地约束 |
| D-05 | 发布必须保留 reviewer/operator 职责边界 | 合规硬约束 | 越权发布 | Sprint-76 |
| D-06 | 未经真实关系核验不得登记物理资产 | 架构硬约束 | 假资产、错误血缘 | Sprint-76 |
| D-07 | 旧资产删除前必须完成客户画像、dry-run、零消费者和回滚 | 迁移硬约束 | 客户数据/书签不可恢复 | DTS C2～C4 |

## 3. 真实数据画像

执行命令：

```sql
select count(*) ...;
select model_type, layer, status, count(*) from modeling_model_spec group by ...;
select route, http_method, result, count(*) from modeling_legacy_api_usage
where occurred_at >= now() - interval '30 days' group by ...;
```

| 指标 | 实测值 |
|---|---:|
| `modeling_warehouse_plan` | 4，全部 DRAFT |
| `modeling_dimension_definition` | 7，全部 CURRENT |
| `modeling_model_spec` | 6（5 DRAFT、1 ARCHIVED），全部 DIMENSION/DWD |
| `modeling_model_spec_revision` | 10 |
| ModelSpec 缺少 `plan_id` / `domain_id` | 0 / 0 |
| ModelSpec `object_id` 非空 / legacy ref 非空 | 0 / 0 |
| `modeling_model_implementation` | 0 |
| `modeling_model_release_candidate` | 0 |
| `modeling_physical_relation_observation` | 0 |
| `modeling_business_object` | 0 |
| `modeling_sql_model` | 0 |
| `semantic_model` / `semantic_dimension` | 0 / 0 |
| 近 30 天 `modeling_legacy_api_usage` | 0 |

**对设计的直接影响**

- 当前数据足以验证维度创建和草稿编辑，但不足以证明 FACT/SUMMARY/APPLICATION、发布、物化和退役链。
- 所有列表必须保留分页/懒加载设计；当前 6 条数据不能作为客户规模假设。
- 本地旧表 0 行只允许支持 Batch A 代码清理和观测方案，不允许直接批准客户数据表删除。
- F0 必须用项目 Demo 与财务 Demo 补齐四类表和至少一个 DEV Candidate 的可验收数据，但不得代写客户业务元数据。

## 4. 外部边界

| 系统 | 契约 | 当前状态 | 失败降级 |
|---|---|---|---|
| Excel ODS | 离线导入，表结构暂不可改 | 外部约束 | 只读来源注册，不修改 ODS |
| 数据标准 | 词汇、字段标准、参考数据、计量单位 | 既有 DTS owner | 模型保存可草稿，发布门禁显式阻断 |
| dbt/Airflow | 构建、调度、运行事实 | Sprint-76 主链 | 不可用时停在 Candidate/DEGRADED，不伪成功 |
| Catalog/OpenMetadata | 物理资产、字段、血缘 | 发布后交接 | 外部同步失败投影 DEGRADED |
| 浏览器认证 | Keycloak + DTS portal | 本轮未复验 | F0 阻断 UI DoD |

## 5. 合规要求

| 条款 | 要求 | 验收硬门槛 |
|---|---|---|
| 租户隔离 | tenant/caller 由服务端身份解析，前端请求不得提交 | 是 |
| 权限 | 复用现有 read/write/export 和 Candidate duty resolver | 是 |
| 审计 | 新兼容访问动作登记并写入现有审计/usage ledger | 是 |
| 密级 | 模型与物理资产继续走既有 classification 控制面 | 是 |
| 凭据 | dbt/数据源 secret 不进入 URL、DB、日志和证据 | 是 |

## 未决问题

- 客户环境各模型表行数、状态、legacy refs 和 90 天入口访问分布。
- 财务/项目 Demo 的四类模型是否已有可复用脚本化 fixture，实施时只允许测试数据写入。
- Sprint-76 DEV 物化目标、认证测试账号和共享 Playwright 会话何时恢复。
