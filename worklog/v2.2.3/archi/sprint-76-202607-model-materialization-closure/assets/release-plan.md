# Sprint-76 发布安全计划（Gate G3）

**状态**：GAP（设计已冻结；迁移、回滚命令和真实演练由 F6/T03 产出后才能 PASS）
**变更类型**：schema + API additive expansion + 发布控制面迁移 + UI 行为替换
**风险等级**：高
**高风险原因**：涉及发布事实、权限/职责分离、Catalog 可见性、外部同步、Airflow 执行绑定和存量兼容路由。

## 1. 迁移策略

| 阶段 | 内容 | 本 Sprint 是否包含 | rollback |
|---|---|---|---|
| Expand | 新增 observation/execution binding+entry/run purpose；Candidate 增加 origin/snapshot/claim/Publish Intent command evidence；新增 action/audit dictionary | 是 | Liquibase rollback 必须只删除本 Sprint 尚未被旧代码消费的新结构；演练前保持 GAP |
| Migrate | 后端先提供 Candidate quality/review/approve/publish commands；旧 lifecycle routes 改为委托 current Candidate；前端切换 Build/Publish Intent | 是 | 关闭新入口和 worker，旧 route 保留但不得与 Candidate 双写 |
| Contract | 删除旧 lifecycle 直接 review/publish mutation、移除兼容响应/字段 | 否，独立后续 Sprint | 必须先有 0 调用证据和完整消费方迁移清单 |

约束：

- expand 阶段所有新列 nullable 或有兼容默认；存量数据回填后再添加 NOT NULL/CHECK/唯一约束；
- `active_claim_key` 唯一约束前先 dry-run 重复报告；冲突记录进入人工处置清单，不自动删除 candidate；
- 大表索引使用非阻塞策略；失败残留索引必须可检测和重建；
- 本 Sprint 不 rename/drop 旧列、旧表或旧 route。

## 2. API 与消费方兼容

| 消费方 | 当前契约 | 新契约 | 兼容策略 |
|---|---|---|---|
| ModelSpecDetailPage | lifecycle validate/compile/跳 SQL | Build Intent + Publish Intent | 后端先上线；前端切换后保留旧读模型 |
| SqlModelingPage | build success 后串行 review/approve/publish | Build Intent + Publish Intent | 删除页面串行动作；无 ModelSpec context 只能技术构建 |
| Release workbench | candidate create/lock/retry/refresh/replacement | 增加 quality/review/approve/publish/rollback/cancel | additive routes；旧 routes 不删除 |
| legacy lifecycle clients | `/lifecycle/reviews*`,`/lifecycle/publish`,`rollback`,`release retry` | 校验 current Candidate 后委托同一 command | 兼容期保持 route/response；无 current Candidate 时 fail-closed，不再直接写 ModelSpec |
| Catalog/lineage | 单模型 registration | candidate mandatory local commit | 保留现有 adapter，由 Candidate commit service 编排，不复制资产 identity |
| Airflow/dbt | 当前 per-tag `schedule=None` DAG + 共享 profiles + Docker dbt | deterministic RELEASE_BUILD executor DAG + stable plan DAG；两者 import 单一 Python task factory；tmpfs profile lease；Airflow 是唯一 scheduler | 先部署 factory、pairwise service auth、tmpfs lease 和 thin executor DAG；旧 per-tag route 迁移并 pause 后再启用 plan schedule |

兼容原则：只增不改不删；Publish Intent response 新字段均为 additive。旧页面和新页面不得同时拥有发布 mutation。

## 3. 发布原子性与部分失败

- PUBLISHING 阶段先准备所有 entry 的本地 ModelSpec release、CatalogDataset/field/lineage/physicalAssetRef，并从全部 current PUBLISHED 模型生成默认 MANUAL_ONLY binding scope；
- mandatory local 步骤全部成功后，同一数据库事务提交 Candidate/ModelSpec PUBLISHED、资产可见和 binding DEPLOYING；
- mandatory local 失败进入 PARTIAL，所有 entry 保持 disabled/不可检索；retry 使用相同 candidate/release identity 幂等补齐；
- OpenMetadata/BI 等外部同步在本地提交后通过 outbox 执行；失败只写 syncHealth=DEGRADED；
- Candidate PUBLISHED 后 binding 部署失败不回滚发布，只把 onlineReadiness 投影为 DEGRADED；
- 任一回滚都不 DROP dbt 已生成 relation。

## 4. 分级发布

1. 迁移 expand，在 clean DB 和存量快照上验证 upgrade/rollback；
2. 部署后端 Candidate commands、domain duty resolver、兼容 route adapter、outbox、Airflow pairwise service-auth allowlist、tmpfs profile lease 和 binding deployment worker，前端仍保持旧入口关闭；Sprint-36/F3 policy 不可用、未配置或 Candidate 双门接入未通过时 PROD kill switch 强制关闭；
3. 对验收 plan/environment 启用新 Candidate publication；跑 SINGLE candidate 全链；
4. 验证三类职责真实账号 + Sprint-36/F3 asset action policy、mandatory fault injection、external sync fault、credential rotation、tmpfs/path/service-auth negative cases、唯一 task factory、DAG atomic deployment/parse/schedule fault；
5. 切换 ModelSpecDetailPage/SqlModelingPage 到 Build/Publish Intent；
6. 观察审计未分类数、PARTIAL 数、candidate/model/catalog 状态一致性和重复 Airflow run；
7. 扩大范围；旧 route contract 阶段另立 Sprint。

启用前必须具备服务端 kill switch，能够停止新 Publish Intent、publication worker 和 schedule deployment，但不删除已提交事实。

## 5. 回滚边界

### 可回滚

- 关闭新 UI 和 Publish Intent入口；
- 停止 publication/outbox/binding deployment worker；平台没有 CRON scheduler 可停止；
- 将 DEPLOYING binding 标记 DISABLED，停止新的 OPERATIONAL_RUN；
- pause 对应 Airflow plan DAG，并对账已创建的 DagRun；
- 保留新表/列，使旧版本代码可继续运行；
- 对未完成 PARTIAL candidate 保持不可消费，等待前向修复。

### 不可自动逆转

- 已执行的 dbt DDL/数据计算；
- 已被外部 Catalog/OpenMetadata/BI 消费的发布通知；
- 已成功完成的生产计算；
- 用户在迁移后对资产或调度配置的合法修改。

因此回滚不得盲删数据或 DROP relation。已产生外部副作用时优先前向修复；需要撤销发布必须走审计化 Candidate ROLLBACK。

### F6/T03 必须补齐的可执行证据

- 真实 Liquibase upgrade/rollback 命令、版本/tag 和输出；
- 新旧后端/前端交叉版本矩阵；
- Airflow DAG template/version、atomic redeploy、pause/unpause 与 actual schedule 对账；
- 数据源 secret、host tmpfs profile lease filesystem/目录权限/固定 host mapping、service token/path allowlist、release/TTL/kill-restart 清理、轮换和回退演练（证据不得包含 secret）；
- RELEASE_BUILD/plan DAG 只 import 同一版本 task factory、legacy per-tag trigger=0 与 pause/rollback 演练；
- Sprint-36/F3 实际 domain/migration/API/IT 完成证据，以及 Candidate duty role + asset action policy 双门禁演练；
- kill switch 实际演练；
- rollback 后 Candidate/ModelSpec/Catalog/binding 对账 SQL；
- 外部同步已发生时的前向修复演练；
- 演练日期、操作者和证据路径。

以上任一缺失，Gate G3 保持 GAP，Sprint 不得 GO。

## 6. 部署顺序与影响面

1. dts-admin 审计资源字典 migration；
2. dts-platform Liquibase expand migration；
3. dts-platform 后端 Candidate/compatibility/publication services；
4. 数据源 secret 到 host tmpfs task-scoped profile lease、Airflow pairwise service auth、唯一 Python task factory 与 thin release executor DAG；
5. publication outbox 与 plan DAG deployment worker；
6. dts-platform-webapp；
7. 灰度启用新入口和 plan schedule；
8. 真实 IT 与 Go/No-Go。

影响模块：`source/dts-platform`、`source/dts-platform-webapp`、`source/dts-admin`、`services/dts-airflow/extra`、thin Airflow DAG renderer、compose runtime mount/service token、Catalog/lineage/OpenMetadata adapter、PostgreSQL migration。
