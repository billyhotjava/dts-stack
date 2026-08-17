# 领域与运行数据画像（Gate G0）

**采集日期**：2026-08-17
**环境**：`/opt/prod/s10/v2.2.3` 本地容器与 PostgreSQL 运行实例
**结论**：当前足以证明服务可达和小型 DWS/ADS 数据存在，但不足以证明商业智能业务旅程、权限边界或客户规模。F0/T02 可执行当前会话复验、本地数据集/legacy 基线与静态盘点；目标环境画像 F0/T03 和最终验收 F6/T01 保持 BLOCKED。

## 1. 统一语言

| 术语 | 本 Sprint 定义 | 明确不等于 |
|---|---|---|
| 查询数据集 | 平台发布的 `QueryDatasetAsset` 某一不可漂移版本，是 BI 的治理输入 | Analytics database、裸 JDBC 数据源、临时 VDS |
| 分析（Analysis） | 基于已发布数据集契约的维度、指标、筛选与可视化定义 | Metabase Question、任意 SQL 编辑器、看板 |
| 看板（Dashboard） | 一组已发布分析 revision 的布局、参数与交互编排 | 分析本身、大屏自由画布 |
| 大屏（Screen） | 面向展示场景的自由画布及其发布版本，组件复用已发布分析 | 第二个查询引擎、第二份 AnalysisQuerySpec |
| 发布版本 | 校验通过并钉定数据集/分析/看板依赖快照的 revision | “已保存”、实体当前可编辑草稿 |
| 受众 | 部门、角色、密级、有效期共同决定的消费范围 | 作者、owner、公开链接 |
| 兼容资产 | 历史 Card/MBQL/route 可读或可迁移的存量 | 未来的新建主线 |

## 2. 业务不变量

1. 只有平台 `PUBLISHED` 的 DWS/ADS 查询数据集可进入默认 BI 主线；DWD 需显式高级授权，ODS/STG 仅诊断。
2. 分析必须钉定数据集 ID、版本、契约版本和 checksum；任何不一致返回 409，不静默切到 latest。
3. 新 UI 不接收 raw SQL/MBQL；派生指标仅在已批准字段与函数白名单内计算。
4. 分析、看板、大屏不得绕过统一认证、平台授权、RLS、脱敏和查询预算。
5. 保存草稿不等于发布；发布必须钉定依赖 revision 和受众快照。
6. 发布物只能由受众匹配且具有 `read` 的用户消费；编辑使用 `write`，导出使用 `export`。
7. 公共匿名链接默认关闭；显式启用时必须有审批、有效期、密级检查、IP/密码策略和审计。
8. 不可证明等价的旧 MBQL 最终分类为 legacy-read-only；Sprint-94 S0 不改变其当前行为，任何后续迁移都必须 preview、幂等、可回滚。
9. 同一稳定资产身份不得因重新发布或迁移生成第二本台账。
10. 业务标签与 `classification` 分离；Analytics 不覆盖平台治理事实。

## 3. 本地运行数据画像

### 3.1 Analytics / Platform 业务数据

| 数据对象 | 当前数量 | 可用于验收 | 结论 |
|---|---:|---|---|
| `analytics_card` | 0 | 否 | 无分析正向或旧 MBQL 兼容样本 |
| Dashboard | 1（有效 0） | 否 | 无可发布看板样本 |
| Dashboard Card | 0 | 否 | 无依赖钉定样本 |
| Analytics Database | 0 | 否 | 旧数据库入口无运行数据 |
| Semantic Model | 0 | 否 | 无语义模型样本 |
| Virtual Dataset | 0 | 否 | 无 VDS 迁移样本 |
| Screen | 1（active） | 仅结构参考 | 可读设计结构，不足以证明 Analysis revision 复用 |
| Platform Query Dataset Asset | 0 | 否 | 主线入口无已发布样本 |
| Platform Query Dataset Version | 0 | 否 | 无版本/checksum/失效样本 |
| Platform BI Report Link | 1（enabled，DTS_BI） | 仅结构参考 | 可验证存量兼容，不足以覆盖受众矩阵 |

### 3.2 `biadmin` DWS/ADS 代表数据

当前共发现 25 个 DWS/ADS 关系，适合作为隔离的 Sprint 验收数据源。代表分布：

| 关系 | 行数 | 代表粒度 | 非空检查 |
|---|---:|---:|---|
| `biz_ads_progress_kpi_v2` | 16 | 16 | 粒度键空值 0 |
| `biz_ads_budget_kpi_v2` | 1 | 1 | 粒度键空值 0 |
| `biz_dws_progress_monthly_v2` | 50 | 10 个项目 | project key 空值 0 |
| `biz_dws_budget_v2` | 10 | 10 个项目 | project key 空值 0 |

这些是本地演示/测试量级，不能外推客户 P95、并发、基数、脏数据或密级分布。任何容量结论必须在 F0/T03 获取目标环境画像后校准。

## 4. 分阶段隔离验收样本

使用前缀 `E2E_BI_202608_*`，不得修改现有 PJM 或客户业务数据；优先以现有 DWS/ADS 表创建平台查询数据集引用，不复制业务表。fixture 随能力 owner 建立，不允许 F0 预造尚未实现的目标状态。

| 阶段/owner | 样本 | 目的与最少事实 |
|---|---|---|
| F0/T02 | DS-PUBLISHED | 复用当前 QueryDataset 发布 API；DWS/ADS、PUBLISHED、version=1、现有可证明字段与 classification |
| F0/T02 | DS-STALE | 使用现有版本能力建立 v1/v2；只作为后续不可变 snapshot 测试输入，不伪造尚未实现的 checksum |
| F0/T02 + F0/T03 | DS-DENIED | 本地建立资产；取得 A4 后由 T03 补无 read/密级负向证据 |
| F0/T02 | LEGACY-CARD 三分类基线 | 仅使用当前 Card/MBQL 能力建立 convertible、legacy-read-only、invalid 输入；不写 `dts.analysis/v1` |
| F2/T01 | ANALYSIS-DRAFT | Analysis v1 实现后建立合法 spec，消费者不可见 |
| F4/T01 | ANALYSIS-PUBLISHED | revision 发布后钉定数据集 snapshot/checksum，结果可人工核算 |
| F4/T01 | DASHBOARD-PUBLISHED | 至少两个已发布 Analysis revision 与一个参数映射 |
| F4/T02 + F0/T03 | AUDIENCE | A1 维护、A2 独立发布、A3 授权消费、A4 非授权负向 |
| 退役 S3 | SCREEN-PUBLISHED | 至少一个 analysis component 与一个 legacy adapter component；不属于 Sprint-94 |

## 5. 数据剖面探针

F0/T02 记录当前实例可证明的静态事实；F0/T03 在取得目标环境只读权限和观测来源后记录分布。两者均不得读取敏感值：

- 已发布 Query Dataset 的数量、分层、owner 部门、密级、版本数、字段数、指标数、空 checksum 数。
- Analysis/Card 的类型、可转换比例、legacy-read-only 比例、invalid 比例；近 30 天调用量由 F0/T03 记录可用窗口或 UNKNOWN 原因。
- Dashboard/Screen 的依赖数量、孤儿引用、未发布依赖和公开链接数量。
- 部门/角色/密级受众的分布与空值，不输出成员名单或敏感字段值。
- 查询行数、耗时、并发和导出量分位数；无数据时明确写 GAP，不用默认值伪造。

## 6. G0 判定

| 检查 | 状态 | 说明 |
|---|---|---|
| 统一语言与不变量 | PASS | 已冻结在本文 §1～2 |
| 本地小型数据源 | PASS | 25 个 DWS/ADS 关系可供隔离样本绑定 |
| BI 主线样本 | GAP | Query Dataset、Card、有效 Dashboard 均为空 |
| 旧资产兼容基线 | GAP | 无真实 MBQL/Card 分布；F0/T02 只能建立隔离输入，不能代表客户存量 |
| 受众与密级样本 | GAP | 只有 1 条登记，无法覆盖角色矩阵 |
| 客户容量画像 | GAP | 仅本地演示量级 |

在 GAP 关闭前，只允许 F0/T01、F0/T02 和不改变运行事实的契约设计；不得把 F1～F6 标记为 READY。F0/T03 缺目标环境只读权限、A1～A4 账号或观测来源时保持 BLOCKED。
