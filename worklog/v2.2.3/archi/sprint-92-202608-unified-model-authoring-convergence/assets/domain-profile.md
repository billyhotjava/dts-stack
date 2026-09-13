# 领域画像（Gate G0）

**勘察日期**：2026-08-19  
**数据来源**：v2.2.3 源码、Sprint-91/93 近期开源证据、2026-08-13 运行库历史画像  
**结论**：核心语义和架构边界可据此设计；当前数据分布与代表性 projection 样本仍需 F0 刷新，迁移 Task 在刷新前不得 READY。

## 1. 统一语言

| 术语 | 定义 | 同义词/禁用词 | 出处 |
|---|---|---|---|
| 模型定义（ModelSpec） | 字段、来源、依赖、分层、粒度和稳定身份的 canonical 业务定义 | 禁止按来源称“可视化模型/dbt 模型” | Sprint-91/93 |
| 模型实现 | 同一 ModelSpec 的可执行 dbt bundle、implementation revision 和 artifact | “代码维护方式”仅兼容文案 | Sprint-76/91 |
| 创作草稿 | 同时暂存业务定义和实现 bundle 的受控草稿 | 不是第二本模型台账 | Sprint-92 ADR-03 |
| 表现视图 | visual/code 对同一草稿的展示和编辑方式 | 不是 ownership | 用户确认 2026-08-19 |
| 来源（provenance） | 平台生成、手工代码或 ZIP 导入的可追溯事实 | 不得用于授权或整页只读 | 用户确认 2026-08-19 |
| 投影覆盖度 | 代码可安全表达为结构化节点的程度 | 不等于发布/质量状态 | Sprint-92 ADR-06 |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 | 出处 |
|---|---|---|---|---|
| B01 | 一个 ModelSpec 只有一个稳定 ID；visual/code 不复制模型 | 架构硬约束 | 重复资产、血缘和候选 | Sprint-91/93 |
| B02 | `PUBLISHED` 修订不可原地编辑，变更必须派生 DRAFT | 生命周期硬约束 | 已发布证据被改写 | 用户确认；Sprint-76 |
| B03 | provenance 不决定写权限 | 产品规则 | ZIP/手工模型永久只读 | 用户确认 2026-08-19 |
| B04 | 无法无损投影时保留原始代码，不猜测改写 | 数据安全硬约束 | SQL 语义或文件丢失 | Sprint-92 ADR-06/07 |
| B05 | `sourceRefs + dependsOn + dimensionRefs` 是唯一业务依赖 owner | 架构硬约束 | visual/code 依赖图分叉 | Sprint-91 ADR-12 |
| B06 | 发布/物化/治理消费相同 model/implementation/dependency pins | 生命周期硬约束 | 页面成功但资产/血缘断链 | Sprint-91/93 |
| B07 | 写动作保留 CAS、幂等、权限和审计；审计不记录 SQL 正文 | 合规硬约束 | 并发覆盖或敏感内容泄露 | DTS invariant D2/D4 |

## 3. 真实数据画像

### 3.1 已有实测事实

| 指标 | 实测值 | 证据/查询 |
|---|---:|---|
| canonical ModelSpec 数（2026-08-13） | 31 | Sprint-91 `assets/domain-profile.md` §3 |
| `DBT_MANAGED`（2026-08-13） | 28 | 同上 |
| `DESIGNER_GENERATED`（2026-08-13） | 3 | 同上 |
| ownership mismatch（2026-08-13） | 0 | 同上 |
| dbt draft 状态（2026-08-13） | COMMITTED=50、VALIDATED=1 | 同上 |
| 单草稿文件边界 | 128 文件；单文件 2 MiB；总计 16 MiB | `DbtImplementationDraftContract.java` |

以上数值是历史快照，只能作为迁移下限，不得当作 2026-08-19 当前值。

### 3.2 F0/T01 必须执行的只读画像

```sql
select implementation_mode, status, count(*)
from modeling_model_spec
group by implementation_mode, status
order by implementation_mode, status;

select status, count(*), min(created_date), max(last_modified_date)
from modeling_dbt_implementation_draft
group by status
order by status;

select count(*) filter (where source_bundle_snapshot is null) as missing_source_bundle,
       count(*) filter (where expires_at < current_timestamp and status <> 'COMMITTED') as expired_open_drafts
from modeling_dbt_implementation_draft;
```

F0/T02 对至少三类隔离样本执行只读 inspect：平台生成、手工代码、ZIP 导入；记录 bundle 文件数、字节数、sourceKind/lossless、projection coverage、raw node 数和诊断，不记录 SQL 正文。

### 3.3 对设计的直接影响

- 存量以 DBT 标记为主，不能用“一次性改枚举”解决；首轮必须兼容读写并分批补齐 provenance/projection。
- 128/2 MiB/16 MiB 是既有硬上限，新 facade 必须原样继承，不能用 JSON 聚合绕开。
- 当前 FULL/PARTIAL/NONE 分布未知，F2 的实现必须先以 fail-closed raw node 为安全默认。
- open draft 和过期状态可能存在，迁移不得复活过期草稿，也不得改写 COMMITTED receipt。

## 4. 外部边界

| 系统/模块 | 我方契约 | 可用性与降级 |
|---|---|---|
| dts-dbt | static validate、build/test、物化执行 | 不可用时允许保存草稿，禁止校验/提交/发布；返回可重试错误 |
| PostgreSQL | ModelSpec、implementation、draft、artifact、candidate 持久化 | 事务失败全部回滚；不得前端补写状态 |
| Keycloak/dts-admin | 登录、`CATALOG_MAINTAINERS`、read/write/export | 不可用时 fail closed；本 Sprint 不设计对方权限模型 |
| Sprint-93 治理链 | AssetKey、质量、血缘、serving projection | authoring commit 不直接写第二套治理状态；发布/物化后沿既有 seam 观察 |

## 5. 合规要求

| 要求 | 是否硬门槛 | 落点 |
|---|---|---|
| 模型/实现写入沿用当前 guard，越权返回 403 | 是 | F4/T02、IT-06 |
| 所有保存、校验、提交、fork 和兼容转换动作分类审计 | 是 | F4/T02 |
| 审计/日志不得记录 SQL、YAML 正文或 token | 是 | `assets/runbook.md` |
| 上传 ZIP 原始 bundle 继续沿用既有安全检查与冻结证据 | 是 | F2/T03 |
| 业务标签与密级 classification 不合并 | 是 | 本 Sprint 不触碰该结构 |

## 6. 未决问题

- 当前运行库分布、过期草稿和 source bundle 缺失率：F0/T01。
- 三类样本 projection coverage：F0/T02。
- 旧 REST 的真实调用量：F5/T01；未取得观测证据前不得删除。

