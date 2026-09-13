# 交付基线探针结果（Gate G0）

**探针日期**：2026-07-27
**环境**：v2.2.3 本机 compose；PostgreSQL 17；Airflow 2.9.3；dbt 容器
**结论**：PASS（DEV/TEST 实施入口）；PROD 验收仍待 F6

| # | 探针 | 结果 | 实际证据 | 阻断/缺口 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | `dts-platform`、PostgreSQL、Airflow webserver、dbt 容器当前 running；关键容器 healthy | - |
| P2 | 登录/auth | PASS（复用） | Sprint-74 同日已用真实 `portal_session` 和 Chrome95 通过；无认证/路由漂移，不重复登录 | F6/T02 最终旅程定点复测 |
| P3 | Schema 状态 | PASS_WITH_RED | `modeling_pipeline_run`、ReleaseCandidate、artifact 表存在；Sprint-76 observation 表尚不存在，符合 RED 基线 | F3/T02 migration |
| P4 | 代表数据 | PASS | 实库 11 ModelSpec、4 Implementation、2 artifact、1 lifecycle event；Sprint-74 四类模型可复用 | - |
| P5 | API acceptance harness | GAP | ModelSpec/ReleaseCandidate API 已存在，但 `/lock` 尚未连接真实 materialization dispatch，Build/Publish Intent 尚不存在 | F2/T01～T03、F5/T01 |
| P6 | UI acceptance harness | PASS（复用） | Sprint-74 Chrome95 基线可复用；Sprint-76 构建发布旅程由 F5/F6 新增并定点验证 | F5/F6 |
| P7 | Build/test commands | PASS | Sprint-36/F3 依赖交付后后端聚焦测试 90/90、前端 source contract 2/2、Chrome95 production build 全绿 | 后续按 Feature 运行聚焦验证 |
| P8 | 外部依赖 | PASS_WITH_RED | 两个 dbt DAG 已注册、unpaused，且有历史 success；当前 DAG 为 schedule=None，四个 Sprint-74 目标关系全部不存在 | F2/F3/F7 |

## RED 基线

### 平台记录

```text
modelspec|11
implementation|4
pipeline_run|0
artifact|2
lifecycle_event|1
release_candidate|0
```

查询：

```sql
select 'modelspec', count(*) from modeling_model_spec
union all select 'implementation', count(*) from modeling_model_implementation
union all select 'pipeline_run', count(*) from modeling_pipeline_run
union all select 'artifact', count(*) from modeling_dbt_artifact
union all select 'lifecycle_event', count(*) from modeling_model_lifecycle_event
union all select 'release_candidate', count(*) from modeling_model_release_candidate;
```

### 目标关系

```text
dwd_s74_project_dimension|NULL
dwd_s74_finance_detail|NULL
dws_s74_finance_summary|NULL
ads_s74_finance_dashboard|NULL
```

查询：

```sql
select x.name, to_regclass('public.' || x.name)
from (
  values
    ('dwd_s74_project_dimension'),
    ('dwd_s74_finance_detail'),
    ('dws_s74_finance_summary'),
    ('ads_s74_finance_dashboard')
) x(name);
```

### Airflow

```text
dwh_dbt_dbt_manual|unpaused|latest success 2026-06-25
dwh_project_management_dbt_manual|unpaused|latest success 2026-07-08
```

这证明执行基础设施可用，但不证明 ModelSpec 的物化链已接通。

## 缺口与处置

| 缺口 | 影响 | 处置 | Task |
|---|---|---|---|
| Sprint-76 最终 UI 旅程尚未执行 | 不阻断后端实施；阻断生产验收 | F5 完成页面闭环后，由 F6/T02 定点执行登录与 Chrome95 真实旅程 | F5/F6 |
| 普通 artifact 不能进入 scoped dbt build | 全部普通物化 | overlay runnable bundle | F1/T03 |
| candidate lock 不产生运行/调度 | 物化主链 | durable pipeline run + dispatcher | F2/T01～T02 |
| 无实时 relation probe | 不能证明建表 | inspector + observation | F3 |
| 高级页面直接自动 review/approve/publish | 绕过候选治理 | Publish Intent 只推进 RUN_QUALITY→SUBMIT_REVIEW；reviewer/operator 在 Candidate 工作台独立操作 | F4/T01、F5/T01 |
| candidate REST 缺质量/审核/发布命令且工作台固定 maintainer role | 无法形成真实职责分离 | 补齐 role-aware Candidate commands；服务端解析 authority/actor | F4/T01 |
| 旧 lifecycle publish 先改 ModelSpec 再 registration | 可能形成发布分裂真值 | 旧 route 兼容委托 Candidate；mandatory local publication 全有或全无 | F4/T02～T03 |
| DAG 仅 manual/schedule=None | 上线后数据不会持续刷新 | plan execution binding + plan DAG | F7 |
| 四个目标关系均不存在 | Sprint RED | 作为 IT-02～04 的预期初始状态 | F6/T02 |

## 本 Sprint 验收路径

- API：真实 Spring Security + PostgreSQL，不使用 route mock；
- 构建：真实 ReleaseCandidate + Airflow + dbt build；
- 关系：目标库系统表 + `select ... limit 1`；
- UI：Chrome95，在模型详情构建/提交上线，并由 reviewer/operator 在计划交付工作台完成独立动作，核对同一 candidate/run；
- 证据：命令、run id、invocation id、relation observation、catalog asset id、截图全部进入 `it/evidence/`；
- 凭据、Cookie、token 和连接密码不得写入证据。

## 证据复用与漂移规则

- 本基线冻结的是 Sprint 进入实施所需的 RED 数据集和执行环境事实，不要求每个 Feature 重放登录、Chrome95 或整包构建。
- 认证/路由、compose、Airflow/dbt、目标数据库、profile/target 或 Chrome95 相关代码变化时，只刷新受影响探针。
- F1 纯后端契约与编译器修改只运行聚焦测试；最终登录、Chrome95、真实 dbt/Airflow/PostgreSQL 旅程统一由 F6 生成 Sprint 自身证据。
