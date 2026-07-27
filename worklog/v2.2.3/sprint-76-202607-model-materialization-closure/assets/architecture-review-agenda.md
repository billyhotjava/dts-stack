# Sprint-76 架构与方向复审议程

**状态**：REVIEWED（2026-07-27；GO FOR DEV/TEST IMPLEMENTATION，PROD NO-GO）
**目的**：在任何编码前，对本 Sprint 的方向、边界、完整性和扩展性作一次明确 Go/Revise/No-Go 决策。

## 待确认的推荐决策

| 评审点 | 推荐方案 | 重点验证 |
|---|---|---|
| 构建入口 | START_BUILD 唯一状态迁移，Build Intent 只创建/精确复用 SINGLE_MODEL candidate | facade 是否严格服务端派生并返回 canonical run |
| 上线入口 | Publish Intent 记录请求，只推进 RUN_QUALITY→SUBMIT_REVIEW，在人工边界停止 | 高级页自动 review/approve/publish 是否移除；重放/漂移/权限撤销是否 fail-closed |
| 单模型与批量 | SINGLE_MODEL/BATCH 标记；禁止自动合并；批量冲突 409 + 深链 | active claim、retry/replacement 和终态释放是否完整 |
| 普通实现制品 | lifecycle artifact overlay 到 scoped project | 是否需要落 `ModelingSqlModel` 投影；推荐不落 |
| 多模型调度 | candidate 一次 dbt build，每 entry 独立 pipeline run | 失败拆分、依赖图和重试语义是否完整 |
| 调度真值 | Airflow 唯一拥有 CRON/nextRun/DagRun/TaskInstance；平台只保存 desired binding 与业务 run | 是否仍有平台定时器或 nextRun 估算形成双调度 |
| 双图边界 | dbt graph 管模型依赖；Airflow DAG 管 prepare/build/sync/probe/finalize | 是否存在重复维护或要求用户手工连线 |
| 两类 DAG | platform-env+target 的 RELEASE_BUILD executor DAG；tenant+plan+environment+target 的 OPERATIONAL plan DAG | 是否只 import 一个 Python task factory 且不混淆触发顺序 |
| 上线后计算 | 发布创建 MANUAL_ONLY binding；CRON 独立 CAS 修改同一 DAG；运行写 pipeline run | Airflow scheduled-open 的原子性是否足够 |
| DAG 粒度 | 每 binding 一个稳定 DAG、一个 schedule；无 scheduleKey | 是否避免 per-model/per-release/per-cron DAG 膨胀 |
| binding scope | 每次从 plan 下全部 current PUBLISHED 模型聚合 | 连续单模型发布是否会丢失旧模型 |
| target/credential | P0 单安全 target；复用数据源 secrets，平台把 task-scoped profile lease 写入宿主机 tmpfs 映射目录；其他 target fail-closed | 是否彻底退出共享 profiles/普通磁盘，并完成固定路径、权限、清理、伪造和轮换验证 |
| 运行真值 | 扩展 `modeling_pipeline_run` | 是否足以承载 candidate/implementation/bundle 强绑定 |
| 物理核验 | runtime target credential 下直接探测 relation | 平台 JDBC 与 dbt runtime probe 哪种适配边界更稳 |
| 核验证据 | append-only RelationObservation | 是否存在可复用的既有证据表；若无则新表合理 |
| 发布前关系 | 可存在但不登记为可消费 CatalogDataset | 是否满足治理、资产发现和回滚语义 |
| 本地发布原子性 | Candidate/ModelSpec/Catalog/field/lineage/physicalAssetRef/MANUAL_ONLY binding scope 全有或全无 | 100-entry 故障注入、PARTIAL 不可见、旧 lifecycle route 兼容委托 |
| Catalog 注册 | publish 时从 observation 登记并回写 output physicalAssetRef | 是否与现有 DbtAssetSync/registration 冲突；external sync 是否只降级健康 |
| adapter 扩展 | capability registry；PostgreSQL P0，未实测 adapter fail-closed | 达梦/MySQL 后续如何接入而不改核心状态机 |
| FULL/INCREMENTAL/SNAPSHOT | P0=FULL+有 KEY 的 INCREMENTAL；SNAPSHOT 阻断 | 与现有 UI/历史值兼容是否充分 |
| 回滚与清理 | 回滚发布事实，不自动 DROP | 是否需要另立物理清理 Sprint |
| 安全 | 服务端 authority/actor resolver、职责分离、密级传播；明确 Sprint-36 M05 粗粒度权限缺口 | 是否有接口绕过 Candidate；是否把 actor separation 冒充细粒度 RBAC |

## 三个生产前门槛

完整证据和 PASS 条件见 `assets/production-readiness-gates.md`。

| 门槛 | 复审结论 | 冻结边界 | 当前状态 |
|---|---|---|---|
| PG-01 dbt 运行凭据 | REVISE | `InfraSecretService` → 宿主机 tmpfs profile lease → Airflow 固定根目录只读挂载；内部 API 不返回 profile | GAP |
| PG-02 两类 DAG 统一模板 | GO WITH REVISIONS | 一个版本化 Airflow Python task factory；Java 只生成 thin DAG；旧 per-tag DAG 兼容迁移后 pause | GAP |
| PG-03 生产权限与职责分离 | DEPENDENCY_READY / LOCAL_INTEGRATION_PENDING | Sprint-76 领域职责 resolver + Sprint-36/F3 资产动作矩阵双门禁 | 外部依赖已完成；F4/F6/IT-14 待接入 |

生产结论为 **NO-GO**。这不否定 Airflow/dbt 方向，而是说明在三项真实证据关闭前不能启用 PROD target、发布或 CRON。

## 完整性检查

- [ ] UI → API → service → pipeline run → Airflow/dbt → relation probe → Publish Intent → 人工审核/发布 → Catalog/binding → UI 全链无 TBD。
- [ ] 普通配置与高级 dbt 从构建开始共用同一控制面。
- [ ] 单模型快捷入口与批量工作台读取同一 candidate/run/allowedActions。
- [x] 发布构建和上线后生产计算使用同一 gateway/run/probe，但状态影响不同。
- [ ] compile、build、materialized、published、registered 五个状态含义不重叠。
- [ ] 每个状态都有可恢复错误、幂等键和证据 owner。
- [ ] 未发布关系、失败关系、漂移关系和已删除关系都有明确表现。
- [ ] 多模型依赖、部分失败和重试不会产生新平行状态机。
- [ ] 发布、回滚、scope 漂移能使 execution binding 幂等部署或 STALE。
- [ ] 手工运行先落平台 run；CRON DagRun 首任务再原子 open 平台 run；两路都没有孤儿。
- [ ] DAG 原子写入、注册/parse/checksum 对账完成前 binding 不 ACTIVE。
- [ ] Airflow actual schedule/nextRun/paused 与页面投影一致，不由平台估算。
- [x] PUBLISHED 与 onlineReadiness READY 分离，binding 非 ACTIVE 或 relation unhealthy 不显示上线完成。
- [x] mandatory local publication 全有或全无；external sync failure 只降级健康。
- [ ] 不依赖中文模型名、页面临时状态或手工数据库操作。

## 扩展性检查

- [ ] adapter-specific 能力只在 `PhysicalRelationInspector`/capability registry 内分叉。
- [ ] 新增 MySQL/达梦 adapter 不修改 ReleaseCandidate/pipeline/Catalog 核心状态。
- [ ] 新增物化类型通过 capability + compiler strategy 扩展，不在 UI 写死。
- [ ] 新增运行引擎时复用 CandidateBuildRequest/RelationObservation，不复制发布控制面。
- [ ] 新 trigger policy 只扩展 PlanExecutionBinding/Airflow timetable，不污染 ModelImplementation。
- [ ] 新 target 通过 executionTargetKey + secret resolver 拆分 binding，不修改 Candidate/run 核心状态；P0 不声称已支持。
- [ ] Catalog/OpenMetadata/BI/Lineage 注册继续是发布步骤，不回流污染编译器。

## 评审输出

### 分项评审记录

| 日期 | 分项 | 结论 | 接受决策 | 待后续复审 |
|---|---|---|---|---|
| 2026-07-27 | ReleaseCandidate 所有权、范围与幂等 | GO WITH REVISIONS | ADR-76-02、12、13、20～24；单模型不自动合并批量候选；DRAFT 不占坑；START_BUILD active claim；CANCELLED 显式释放；服务端派生；确定性 dagRunId；单执行目标 | Publish Intent/上线语义；DAG/schedule/target 问题已由后续 Airflow 分项收敛 |
| 2026-07-27 | Publish Intent / 上线语义 | GO WITH REVISIONS | ADR-76-17～18、25～29；构建完成首次计算；Publish Intent 只推进质量和提交审核；Candidate 唯一发布 owner；mandatory local 原子可见；external sync 仅 DEGRADED；默认 MANUAL_ONLY binding；PUBLISHED≠上线完成；服务端角色解析 | Airflow 落地已由后续分项收敛；Sprint-36 M05 细粒度权限缺口仍保留 |
| 2026-07-27 | Airflow 落地、计划 DAG 与执行目标 | REVISE COMPLETED / 待最终复审 | Airflow 是唯一调度真值；RELEASE_BUILD/plan DAG 复用同一 task template；manual 与 CRON 使用不同正确落账顺序；binding 无 scheduleKey、聚合全部 current PUBLISHED scope；P0 单 target；原子 DAG 部署；实际状态来自 Airflow | secret backend/provider 与轮换实证、现有 Docker dbt template 复用、Sprint-36 M05 权限交付边界 |
| 2026-07-27 | 三个生产前门槛 | REVISE；PROD NO-GO | ADR-76-36～39；tmpfs profile lease；pairwise Airflow service auth；单一 Python task factory + thin DAG；domain duty role 与 M05 action policy 双门禁 | PG-01/02 进入实现前仍需最终复审；PG-03 等待 Sprint-36/F3 实际完成 |

本轮要求修改：

1. 删除“普通 host-mapped runtime-secret 目录即可生产”的表述，统一为“宿主机 tmpfs + 固定根目录 + leaseId 派生 + 0700/0600 + TTL”。
2. 删除“两个 DAG 各自复用相似模板”的歧义，统一为 `services/dts-airflow/extra` 中唯一 Python task factory，Java 只生成薄 DAG 定义。
3. 为 Airflow prepare/open/sync/probe/finalize/release 增加 pairwise service token、principal/path allowlist；任何回写失败不得 `|| true`。
4. Sprint-76 不复制 M05 权限表；Sprint-36/F3 端口已就绪，PG-03 由 F4 消费该端口并交付 Candidate 领域职责 resolver，IT-14 关闭前仍为生产 NO-GO。
5. 上述修改已经写回 Contract/Task/IT；Sprint 可进入 DEV/TEST 实施。生产仍由 PG-01/02/03 和 F6 证据硬阻断。

### 最终复审结论

```text
结论：GO FOR DEV/TEST IMPLEMENTATION；PROD NO-GO
接受 ADR：ADR-76-01～39
要求修改：所有 REVISE 项已回写；实施不得引入第二运行台账、第二发布 owner、
          第二 Airflow client/runtime template 或共享明文 profile。
Feature/Task 调整：F0=DONE；F1=IN_PROGRESS；其余 Feature 按依赖逐项进入实施。
进入生产的前置：PG-01/02/03 全部有真实 PASS 证据；IT-01～20 与 F6 Go/No-Go 完成。
评审日期：2026-07-27
```

这一定义刻意区分“可以开始实现”和“可以进入生产”。登录、Chrome95、构建基线不按 Feature 重复执行；只有证据所对应的环境/代码发生漂移，或 F6 需要最终验收证据时才定点复测。
