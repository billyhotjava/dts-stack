# 领域画像 (Gate G0)

**勘察日期**: 2026-08-13
**数据来源**: 当前 `dts_platform` 运行库 + v2.2.3 源码/历史
**结论**: 可据此设计；登录及 Chrome 95 属交付基线缺口，不影响领域契约。

## 1. 统一语言

| 术语 | 定义 | 同义词/禁用词 | 出处 |
|---|---|---|---|
| 可视化模式 | 通过基本信息、字段、来源和物化配置维护模型 | 原“快捷模式”；不称“低级模式” | 用户截图与当前模型工作台 |
| 代码模式 | 查看或维护当前模型的 dbt SQL/Jinja/YAML 实现 | 工程用户可使用 `dbt`；不称“另一个高级建模页面” | 当前 AdvancedDbtWorkspace |
| 表现视图 | 同一 ModelSpec 的 visual/code 展示状态，不落库 | 禁止与 implementation ownership 混用 | `ModelVisualizationCapabilityEvaluator` |
| 实现所有权 | `DESIGNER_GENERATED` 或 `DBT_MANAGED`，决定谁可写物理实现 | 不用“Tab 状态”代替 | ModelSpec / implementation contract |
| 接管代码实现 | 将可视化生成实现显式转换为手工 dbt 维护，**本版本不可逆** | 不用“切换到代码模式”暗示已转换；不承诺“随时可切回” | 本 Sprint ADR-91-02/03/09 |
| ~~转为可视化维护~~ | **本 Sprint 不提供**。回切能力顺延 Sprint-92 | UI 上不出现该动作，也不出现置灰版本 | ADR-91-09、`sprint-92-back-conversion-handoff.md` |
| 系统中间节点 | 编译器为每个 DESIGNER 模型生成的 `stg_<name>.sql`（ephemeral），是实现的一部分而非用户资产 | 不称“临时文件”；不可在 UI 中隐藏到用户看不见 | `ModelingDbtCompiler.java:105-113` |
| 提交实现 | 提交新的 implementation revision | 不等于发布 | dbt draft commit |
| 发布模型 | 通过统一 release candidate 完成构建、质量、审核和发布 | 禁止在代码模式另建“上线”状态机 | ModelLifecycleContract |
| 基础来源 | 模型读取的一个 ODS 物理来源或一个上游模型，是实现的基础输入 | 不与维度引用混为“二选一来源” | F6/T02 |
| 维度引用 | FACT 等模型关联的 DIMENSION 固定 model/implementation revision | 禁止按名称或“当前最新版”浮动连接 | F6/T01/T02 |
| 依赖快照 | `sourceRefs + dependsOn + dimensionRefs` 与 dbt 解析证据对账后的不可变 pins/checksum | 不称“前端关系缓存”；不另建依赖 owner | ADR-91-12 |
| 物化计划 | 对请求目标求依赖闭包后的 `BUILD/REUSE/BLOCK` 有序预览 | 不等于裸 dbt selector；不等于 candidate 状态机 | ADR-91-14、F8 |
| BUILD | 精确目标关系尚不可复用，须进入当前候选构建 | 不代表重新创建逻辑模型 | F8 |
| REUSE | 同环境/目标存在与固定 pins 完全一致且 verified/existing 的关系 | 表名相同不等于 REUSE | F8 |
| 二次物化 | 用户对同一模型再次发起新执行，保留新候选/执行/观察历史 | 不称重试；不得复制 ModelSpec/AssetKey | F8/T03 |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 | 出处 |
|---|---|---|---|---|
| I1 | 一个 model revision 只能绑定一个明确的 implementation ownership | 架构硬约束 | 编译、物化和发布无法判断事实源 | 现有 lifecycle contract |
| I2 | 切换 visual/code 视图不得改变 ownership 或写新修订 | 产品硬约束 | 用户仅查看代码却丢失可视化维护权 | 用户确认 + ADR-91-02 |
| I3 | ownership 转换必须同时推进 ModelSpec 与 implementation revision | 架构硬约束 | 出现 ownership mismatch，模型暂时不可发布 | 当前实测 mismatch=0 |
| I4 | **所有权转换后模型必须仍能编译**：DBT_MANAGED 的制品类型集合必须落在 `compile()` 的接受集合内 | 架构硬约束 | 接管后无法编译→无法物化→无法发布，模型变砖 | `ModelLifecycleService.java:462-476`；复核结论 A |
| I4b | dbt 回切仅接受安全可表达子集；规则不具备时**不提供入口**，而非提供必然拒绝的入口 | 产品硬约束 | 用户面对一个永远失败的按钮，比没有按钮更糟 | ADR-91-09；复核结论 B |
| I7 | dbt 节点身份由服务端派生，客户端不可指定；转换前后 selector 不变 | 架构硬约束 | 物化目标漂移、uniqueId 唯一约束被客户端触发 | ADR-91-08；账本 #21 |
| I5 | 实现提交与发布分离；发布候选必须钉住 model + implementation checksum | 发布硬约束 | 发布旧实现或产生不可追溯资产 | 既有 release candidate 控制面 |
| I6 | 所有权转换必须鉴权、幂等、并发安全并记录审计 | 合规硬约束 | 越权覆盖、重复修订、责任不可追溯 | DTS D2/D4 + lifecycle 约定 |
| I8 | ModelSpec 业务依赖是唯一 owner；dbt `source/ref` 只能作为实现证据对账 | 架构硬约束 | 形成两张图，建模显示成功但物化/血缘断链 | ADR-91-12 |
| I9 | ODS 由数据集成负责接入；建模只绑定已确认来源；STG 只做技术节点 | 能力边界 | 重复造 ODS、资产重复、责任不清 | ADR-91-11 |
| I10 | FACT 可同时拥有一个基础来源和多个维度引用；DIM/FACT 属 DWD，DWS/ADS 依赖固定上游 | 领域硬约束 | 无法表达标准星型链或产生浮动依赖 | F6/T02 |
| I11 | 单表/批量物化必须先解析同一依赖闭包，candidate 必须重算 plan checksum | 运行硬约束 | 漏构建上游、客户端绕过围栏、结果不可重放 | ADR-91-14 |
| I12 | 手工、DESIGNER、ZIP 三种 authoring adapter 共享模型身份、依赖、target 和发布资产身份 | 架构硬约束 | 三套模型/物化/资产孤岛 | ADR-91-13 |
| I13 | 二次物化只新增执行证据，不新增逻辑模型、实现身份或 CatalogAssetKey | 资产硬约束 | 重复资产、血缘分裂、指标引用漂移 | F8/T03 |

## 3. 真实数据画像

运行库查询：

```sql
select implementation_mode, status, count(*)
from modeling_model_spec
group by implementation_mode, status;

select ownership, status, count(*)
from modeling_model_implementation
group by ownership, status;

select status, count(*)
from modeling_dbt_implementation_draft
group by status;
```

| 指标 | 实测值 | 对设计的影响 |
|---|---:|---|
| canonical ModelSpec | 31 | 双模式必须兼容现有模型，不做全量迁移 |
| DBT_MANAGED ModelSpec | 28（均 DRAFT） | 代码模式是主存量路径，必须零回归 |
| DESIGNER_GENERATED ModelSpec | 3（ARCHIVED=2、DRAFT=1） | 不能把唯一 DRAFT 样本直接做破坏性接管；F0 建一次性验收样本 |
| 当前 implementation | DBT_MANAGED ACTIVE=28；DESIGNER_GENERATED ACTIVE=3 | model 与 implementation 数量一一对应 |
| ownership mismatch | 0 | 转换后继续以 0 为硬断言 |
| dbt 草稿 | COMMITTED=50；VALIDATED=1 | 必须兼容已有草稿状态和 source bundle 重建 |
| contract_version | v2=31 | 本 Sprint 不为 legacy v1 增加写能力 |
| dbt 文件容量边界 | 128 文件；单文件 2 MiB；总计 16 MiB | Monaco 只挂载选中文件，保存仍由服务端统一限额 |

**样本策略**：

- DBT_MANAGED 读取样本可使用现有 `E2E_PJM_20260812_*` 模型。
- DESIGNER_GENERATED 转换验收必须新建可回收的 `E2E_S91_*` 模型，不修改当前唯一 DRAFT 日期维度模型。
- 所有 ownership 转换测试结束后，以新修订保留审计证据；不物理删除现网模型。
- F6～F8 统一使用 `E2E_S91_CHAIN_*`：复用 1 个已有且含测试数据的 ODS binding，创建 1 个 DWD DIM、1 个 DWD FACT、1 个 DWS SUMMARY、1 个 ADS APPLICATION；不使用 ZIP 建主链。
- 样本事实必须记录 source resolved version、model/implementation/dependency checksum；STG/proxy 只记录在 canonical bundle，不计为业务模型数量。

## 4. 外部边界

| 系统/边界 | 契约 | 当前可用性 | 失败降级 |
|---|---|---|---|
| dts-platform | ModelSpec、representation、draft、lifecycle、release API owner | 容器 healthy | 后端不可用时页面保留模型上下文并显示可重试错误，不本地伪造 capability |
| dts-dbt | 编译/物化运行环境 | 容器运行中 | 预览可做静态生成；物化/发布保持失败态并由现有候选重试 |
| 数据集成/ODS | 提供已接入、字段已确认且有数据的 source binding | 由 F0/T03 选择并只读核验 | 不可用时全链路样本 BLOCKED；建模不得自行 DDL/装数兜底 |
| dts-platform-webapp | 唯一用户入口 | 首页 HTTP 200 | 不恢复旧页面作兜底 |
| Keycloak/权限 | `CATALOG_MAINTAINERS` | 容器 healthy，真实登录待探针 | fail closed；只读用户仅看允许的表示 |

## 5. 合规要求

| 条款 | 要求 | 验收硬门槛 |
|---|---|---|
| 权限 | 转换、保存、校验、提交继续走现有 command guard；所级管理员可执行已授权的显式生命周期命令，部门角色受所属范围限制 | 是；越权必须 403，前端不得自动审批/发布 |
| 审计 | `MODEL_IMPLEMENTATION_OWNERSHIP_TRANSITION` 记录 actor、source/target ownership、revision/checksum、transitionId | 是；动作须使用已登记审计分类 |
| 并发 | If-Match 与 implementation ETag 不匹配时拒绝覆盖 | 是；返回 409/412 并可刷新恢复 |
| 文件安全 | 继续拒绝 credentials、secret、key/cert 等敏感路径及超限文件 | 是；不得放宽现有 contract |
| 数据留存 | committed implementation/artifact 与发布证据不可因所有权变化而物理删除 | 是；历史修订可追溯 |

## 未决问题

- 登录账号与 Chrome 95 实机路径尚未在本轮执行，归 F0/T01，不改变上述领域设计。
- 现场是否需要 YAML schema 专门补全能力可在 F4 验收后评估；本 Sprint 只承诺语法高亮与诊断定位，不承诺智能补全。
- **接管不可逆是否可长期接受，是产品决策**。若不可接受，Sprint-92 需按 `sprint-92-back-conversion-handoff.md` 的「重启前置条件」建设回切能力；若可接受，则应反向决定是否下线旧 `convert-to-designer-generated`。本 Sprint 两者都不做。
- **现网已有 stale candidate 保护**：候选钉住 model/implementation revision 与 checksum，漂移时返回 `MODEL_RELEASE_CANDIDATE_STALE`；F5/T01 负责补强回归证据，不另建状态机。
- F0/T03 尚需选定真实 ODS source binding；未记录 ID/resolved version/行数前，F6～F8 的运行态验收保持 DRAFT/BLOCKED。
- 可视化转换本期白名单限定于字段映射、cast、filter、dedup、INNER/LEFT join、groupBy 与基础 aggregation；窗口函数、任意宏/表达式继续走代码维护。
