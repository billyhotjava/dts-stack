# 领域与真实数据画像（Gate G0）

**勘察日期**：2026-07-27
**数据来源**：v2.2.3 当前 PostgreSQL、Airflow DB、Sprint-74 真实验收记录
**结论**：可据此规划；生产规模和非 PostgreSQL adapter 仍需实施前补充画像

## 1. 统一语言

| 术语 | 定义 | 禁用/易混词 | 出处 |
|---|---|---|---|
| 逻辑模型 | ModelSpecRevision 对业务粒度、字段、键、时间和维度关系的定义 | 物理表、dbt 文件 | Sprint-74 |
| 数据实现 | ModelImplementationRevision 对输入、映射、转换和产出设置的定义 | 已建表、已发布 | Sprint-74 |
| 编译 | 把当前实现生成可重现 SQL/schema artifact | 物化、发布 | 当前 lifecycle compile |
| 构建 | ReleaseCandidate 锁定范围后执行 dbt build | 自动审核、自动发布 | Sprint-69 |
| 发布构建 | 上线前针对 current candidate 执行的一次 RELEASE_BUILD | 周期运行 | Sprint-76 |
| 生产计算 | PUBLISHED 后由计划执行绑定触发的 OPERATIONAL_RUN | 重新审核发布 | Sprint-76 |
| dbt 依赖图 | source/ref 形成的模型依赖和执行顺序 | Airflow 工作流 | Sprint-76 |
| Airflow 工作流 | 按计划触发并串联 build/sync/probe/reconcile | 字段/表依赖图 | Sprint-76 |
| 物化 | build success 且目标数据库关系实时存在 | 有 SQL、run_results success | 本 Sprint ADR-76-01 |
| 关系核验 | 使用执行目标凭据查询 database/schema/identifier 的当前存在性和元数据 | manifest 推断 | 本 Sprint ADR-76-08 |
| 发布 | 通过质量、审核和批准后登记当前 revision 的可消费资产与外部注册 | 构建成功 | Sprint-69 |
| 提交上线 | 用户请求 Candidate 运行质量并提交审核，在人工边界停止 | 自动批准、自动发布 | Sprint-76 分项评审 |
| 发布上线 | reviewer 批准后由 operator 以 Candidate 为唯一 owner 提交 mandatory local publication | 再次 dbt build、配置 CRON | Sprint-76 分项评审 |
| 上线完成 | Candidate PUBLISHED + mandatory local registration complete + binding ACTIVE + relation healthy 的派生状态 | Candidate 独立枚举 | Sprint-76 分项评审 |
| 物理资产 | 已发布的 table/view 及其字段、DDL、运行和血缘证据 | 高级 dbt 工作台 | Sprint-74 |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 | 出处 |
|---|---|---|---|---|
| I01 | 一个物化证据必须锁定 ModelSpec 和 ModelImplementation revision/checksum | 业务硬约束 | 旧实现冒充当前结果 | Sprint-69/74 |
| I02 | compile 不得产生“物化成功”或物理资产 | 业务硬约束 | 用户误判已建表 | 本轮复查 |
| I03 | 输出关系标识必须等于用户确认的 targetPhysicalName | 业务规则 | 表名不可预测、资产无法对应 | Sprint-74 settings |
| I04 | run_results success 不能替代数据库关系 EXISTS | 业务硬约束 | 已删除/错误 schema 的关系被错误发布 | 本轮复查 |
| I05 | 构建前必须 fail-closed 校验 adapter 能力和设置组合 | 业务硬约束 | 分区/SNAPSHOT 等配置被静默忽略 | 本轮复查 |
| I06 | 未发布构建关系不得作为可消费 Catalog 资产展示 | 治理硬约束 | 绕过质量/审核/密级 | Sprint-69/72 |
| I07 | physicalAssetRef 只能指向输出资产 | 架构硬约束 | 输入表被显示为模型产物 | domain-dts A3/A4 |
| I08 | PUBLISHED 资产必须使用统一 CatalogAssetKey 并写输出血缘 | 架构硬约束 | 多套资产身份和断裂血缘 | domain-dts A3 |
| I09 | 密级传播和发布权限不得由物化链绕过 | 合规硬约束 | 越权或降密发布 | domain-dts D1/D4、Sprint-72 |
| I10 | 模型详情/高级页的 Publish Intent 只记录发布请求并推进 RUN_QUALITY→SUBMIT_REVIEW，在人工边界停止 | 架构硬约束 | 平行状态机、自动越权批准/发布 | Sprint-69/76 |
| I11 | 用户不手工维护 dagId/selector/projectDir；这些字段从 plan/release/artifact 派生 | 产品硬约束 | 页面暴露内部实现、上下文漂移 | Sprint-76 |
| I12 | OPERATIONAL_RUN 失败只影响运行/资产健康，不撤销 PUBLISHED 或冒充发布构建失败 | 业务硬约束 | 发布事实被周期故障污染 | Sprint-76 |
| I13 | SINGLE_MODEL_INTENT 不得自动合并、扩展或缩小 BATCH_WORKBENCH scope | 产品硬约束 | 单模型操作意外构建整批范围 | Sprint-76 分项评审 |
| I14 | tenant+environment+model 同时最多一个 active candidate claim，必须数据库强制 | 架构硬约束 | 并发双构建和重复外部成本 | Sprint-76 分项评审 |
| I15 | candidate 技术字段必须从 canonical snapshot 服务端派生 | 安全硬约束 | 请求篡改版本、执行目标或成功证据 | Sprint-76 分项评审 |
| I16 | candidate 内 environment/adapter/profile/target 必须同质；ref 上游必须已发布或在同一 candidate | 业务硬约束 | 一个候选产生隐式多执行边界或绕过上游治理 | Sprint-76 分项评审 |
| I17 | Candidate 是唯一审核/批准/发布状态 owner；旧 lifecycle route 只能兼容委托 | 架构硬约束 | Candidate/ModelSpec 双发布真值 | Sprint-76 分项评审 |
| I18 | mandatory local publication 在 candidate 维度全有或全无；PARTIAL 下 0 个 entry 可消费 | 业务硬约束 | 批量候选部分上线、资产口径不一致 | Sprint-76 分项评审 |
| I19 | external registry 失败只降低 sync health，不回退本地 PUBLISHED | 业务硬约束 | 外部短暂故障破坏本地主真值 | Sprint-76 分项评审 |
| I20 | PUBLISHED 不等于上线完成；只有 binding ACTIVE + relation healthy 才是 online READY | 产品硬约束 | 已发布但不可计算仍显示成功 | Sprint-76 分项评审 |
| I21 | allowedActions 必须由服务端 authority/actor resolver 计算；actor separation 不冒充细粒度 RBAC | 安全硬约束 | 维护者获得 reviewer/operator 权限或虚假合规声明 | domain-dts D4、Sprint-76 分项评审 |
| I22 | Airflow 是 CRON、logical date、next run、DagRun/TaskInstance 的唯一调度真值；平台不得自建并行 scheduler | 架构硬约束 | 双调度、重复计算、页面时间漂移 | Sprint-76 Airflow 复查 |
| I23 | 平台业务 run 与 Airflow DagRun 必须按触发来源使用正确落账顺序并可幂等对账 | 架构硬约束 | 外部孤儿运行或周期运行无业务证据 | Sprint-76 Airflow 复查 |
| I24 | binding scope 必须是 plan/environment/target 下全部 current PUBLISHED 模型，不能只取最近 Candidate | 业务硬约束 | 增量发布后旧模型从计划计算中消失 | Sprint-76 Airflow 复查 |
| I25 | credential 不得进入 Git、DAG、DagRun conf、API/DB/log/evidence；无安全 target 时 fail-closed | 安全硬约束 | 密钥泄露或虚假多目标能力 | domain-dts D6、Sprint-76 Airflow 复查 |

## 3. 真实数据画像

| 指标 | 实测值 | 查询/命令 |
|---|---:|---|
| ModelSpec 当前数 | 11 | `select count(*) from modeling_model_spec` |
| ModelImplementation 当前数 | 4 | `select count(*) from modeling_model_implementation` |
| pipeline run 当前数 | 0 | `select count(*) from modeling_pipeline_run` |
| dbt artifact 当前数 | 2 | `select count(*) from modeling_dbt_artifact` |
| lifecycle event 当前数 | 1 | `select count(*) from modeling_model_lifecycle_event` |
| ReleaseCandidate 当前数 | 0 | `select count(*) from modeling_model_release_candidate` |
| Sprint-74 目标关系存在数 | 0/4 | `to_regclass('public.' || target)` |
| dbt DAG 注册/启用 | 2/2 | Airflow `dag` 表 |
| 两个 DAG 历史 success | 均有 | Airflow `dag_run` 表 |
| Airflow executor/version | 2.9.3 / LocalExecutor | live Airflow config |
| Airflow default timezone | UTC | `airflow config get-value core default_timezone` |
| 动态 DAG 目录 | platform 与 scheduler 共享 RW；scheduler 3s 扫描 | compose volume + scheduler config |
| dbt target | 当前 profile 仅一个 `dev` Postgres output | live `profiles.yml` 结构复查 |

### 代表模型

| 模型 | 模式 | 目标名 | 当前结果 |
|---|---|---|---|
| 项目维度 | DESIGNER_GENERATED | `dwd_s74_project_dimension` | relation 不存在 |
| 财务明细 | DESIGNER_GENERATED | `dwd_s74_finance_detail` | relation 不存在 |
| 财务汇总 | DESIGNER_GENERATED | `dws_s74_finance_summary` | relation 不存在 |
| 财务看板 | DBT_MANAGED | `ads_s74_finance_dashboard` | compile artifact 存在，relation 不存在 |

**对设计的直接影响**：

- 当前数据量适合关闭功能正确性，但不能外推生产性能；NFR 将候选 entry 上限和 artifact 大小做硬限制；
- 四类现成 ModelSpec 可作为 RED→GREEN 数据，不再另造脱离真实契约的假模型；
- DAG 和 dbt 通道可复用，缺口集中在 runnable bundle、durable dispatch、relation probe 和发布资产交接；
- 当前 dbt DAG 为 manual/schedule=None，持续计算必须部署 Airflow 原生 CRON plan DAG，不能由平台轮询器冒充调度；
- 当前 `DbtDagService` 直接覆盖 DAG 文件且 sync 使用 `all_done`/`|| true`，需要原子写入和失败传播；
- 当前 profile 只有一个 target 且含 tracked 明文凭据；在 secret 迁移前不得声称生产/多 target 能力；
- 当前 platform/Airflow 共享 profiles 宿主机目录，canonical 方案必须改为宿主机 tmpfs lease；Airflow 仅按固定 root + leaseId 挂载；
- 当前 Airflow callback 缺 pairwise service token/路径授权；两类 DAG 必须 import 单一 Python task factory，不能继续复制 Java/Bash runtime；
- 当前 Candidate 仍固定 MODEL_MAINTAINER；Sprint-36/F3 资产动作矩阵已 DONE，但 Candidate 发布/计划链尚未消费，domain duty role 与资产动作矩阵双门禁仍待 Sprint-76 闭合；
- 非 PostgreSQL 目标无真实环境证据，必须 capability fail-closed。

## 4. 外部边界

| 系统 | 我方契约 | 当前可用性 | 失败策略 |
|---|---|---|---|
| Airflow | 唯一 CRON/next-run/DagRun/TaskInstance 真值；触发/查询 dbt DAG run | 2.9.3 LocalExecutor 可用，有历史 success | pipeline run 保留 QUEUED/RUNNING/FAILED/UNKNOWN；不得平台侧估算调度成功 |
| dbt 容器 | scoped project + operation=build | 可用 | bundle checksum + invocation id 追踪 |
| 目标数据库 | relation metadata probe | PostgreSQL 可验收 | probe 失败/超时 fail-closed |
| Catalog | mandatory local publication 登记 dataset/fields/lineage | 既有能力 | 失败则 Candidate PARTIAL 且全部资产不可消费，可幂等重试 |
| OpenMetadata | PUBLISHED 后外部登记/同步 | 非本 Sprint 主真值 | 失败不得影响本地 PUBLISHED；syncHealth=DEGRADED 并走 outbox 重试 |

## 5. 合规要求

| 条款 | 要求 | 是否验收硬门槛 |
|---|---|---|
| 权限 | 服务端必须解析 maintainer/reviewer/operator 并执行 actor separation；发布资产还必须消费 Sprint-36/F3 action policy。任一未完成不得声称 PROD 或细粒度 RBAC 已闭合 | 是 |
| 审计 | START_BUILD、RETRY_BUILD、PUBLICATION_REQUESTED、SUBMIT_REVIEW、APPROVE/REJECT、PUBLISH、registration retry、schedule change 必须分类登记 | 是 |
| 密级 | 输出取上游最高密级，不得通过构建降级 | 是 |
| 凭据 | dbt/数据库凭据不得进入 Git、DAG、DagRun conf、API/DB、日志或 evidence；必须有可轮换 secret owner | 是 |
| 租户 | candidate/run/observation/catalog 全链 fail-closed 隔离 | 是 |

## 未决问题

- 客户生产候选平均/最大模型数尚未画像；本 Sprint 暂定硬上限 100，实施前以真实数据校正；
- 达梦/MySQL 目标环境尚无真实 probe harness；不得在本 Sprint 完成声明中写“已支持”；
- 物化关系的后续自动清理/保留策略执行不在本 Sprint，`retentionDays` 仅作为治理元数据；
- P0 已收敛为每 `(tenant,plan,environment,executionTargetKey)` 一个 binding/一个 schedule；不提供 scheduleKey、多 target 或跨 target `ref()`；
- 数据源 secret 到宿主机 tmpfs task-scoped profile lease 的 filesystem、固定 host mapping、service auth、清理与轮换由 F2/T04 完成，在其 PASS 前构建/持续计算不得进入生产 READY；
- Sprint-36/F3 的 domain/migration/API/test 已交付；Sprint-76 在 Candidate duty resolver、发布/计划消费和 IT-14 完成前，仍不启用 PROD 发布/CRON。
