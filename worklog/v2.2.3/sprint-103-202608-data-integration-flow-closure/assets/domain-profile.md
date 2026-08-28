# 领域与数据画像

## 领域词汇

| 术语 | 定义 | 不等同于 |
|---|---|---|
| 数据集成流程 | 一个接入任务的源、目标资产、映射、调度、接入后质量验证及其生命周期 | 通用工作流平台 |
| 任务设计草稿 | 尚未准入的类型化 `IngestionTask` 配置 | React Flow graphDsl |
| canonical plan | 执行相关任务字段的规范化表示 | 用户任意脚本或 UI 布局 |
| plan checksum | canonical plan 的稳定摘要，用于并发和一致性 | 更新时间或 graph checksum |
| 拓扑投影 | 从指定 draft/revision 生成的只读节点/边视图 | 独立业务配置 |
| 准入版本 | 通过治理门禁并可激活的 `IngestionTaskRevision` | 浏览器最新输入 |
| 调度发布 | 将准入版本对应 DAG 暂存、校验并原子发布 | 保存草稿 |
| 运行实例 | 绑定 task、revision、plan checksum 的 `IngestionExecution` | 独立 Airflow dagRunId |
| 重试 | 基于原实例冻结输入创建有关联的新实例 | 任意 DAG 再触发一次 |
| 目标数据资产 | 由 `CatalogAssetType.DATASET + CatalogAssetKey` 唯一标识的接入目标；当前质量兼容引用解析为 `dataset:<uuid>` | 编排页自建资源 ID |
| `qualityPolicyRef` | 现有兼容字段，格式为 `dataset:<uuid>`，用于把 execution 绑定到同一目标数据集的已发布规则 | 可任意选择的策略实体或复制规则 JSON |
| 接入过程校验 | 数据提交前执行的连接、映射、schema/type、权限、密级与暂存区检查 | 正式资产质量证据 |
| 接入后质量验证 | execution 对应批次/分区/快照提交成功后，由质量域 workflow 执行的正式验证 | 接入 admission 或同步事务内阻塞步骤 |
| 当前质量证据 | 与当前 ingestion execution 绑定的 workflow/run、触发状态、结果和新鲜度 | 数据集历史上任意一次成功质量运行 |
| 可信可用 | 当前质量证据为 `CURRENT/PASSED` 且消费资格为 `ELIGIBLE` 时的派生展示 | 新资产生命周期或持久化总状态 |
| ELT 转换制品 | 已发布、版本化且有输入输出身份的转换实现 | 画布内联 SQL/Python |

## 流程分类

| 分类 | 判断标准 | 本 Sprint |
|---|---|---|
| `SINGLE_TASK_EL` | 单任务完成 source→load，可有 mapping/schedule | 主范围 |
| `VERSIONED_ELT` | load 后引用版本化转换制品 | 待未来 capability |
| `MULTI_JOB_DAG` | 多个已发布作业有分支/汇聚/前后置 | 只盘点需求 |
| `DOMAIN_WORKFLOW` | 审批、质量处置、模型发布等业务状态 | 由领域 owner 负责 |

## 领域不变量

1. 类型化任务配置与 revision 是唯一执行事实；拓扑只能单向投影。
2. 一个 execution 固定到一个 task、revision、plan checksum，运行中不追随最新草稿。
3. 保存草稿、服务端校验、准入发布是不同门；任一门失败不进入下一状态。
4. 同一任务最多一个 ACTIVE revision；调度命令只作用于 owner 可证明的 DAG。
5. 跨域能力只引用稳定对象 ID，执行与状态仍归原领域 owner。
6. graphDsl 只作为 legacy 数据读取/导出，不可发布，不可覆盖 canonical plan。
7. 失败、重试、取消是显式业务状态，不能用 toast 或 Airflow 临时状态替代账本。
8. 权限、部门、密级和审计在服务端 fail closed；前端隐藏不是安全控制。
9. 凭据、脚本正文、依赖密钥不进入 URL、审计详情、日志、checksum 明文或 `toString()`。
10. 历史 DAG 先归类再接管/退役；无批准不批量修改。
11. 数据资产只有一个 canonical identity；接入、质量、目录和消费资格不得各建平行资产主键。
12. 接入过程校验只决定本次写入能否继续，不产生 `PASSED` 资产质量结论。
13. 正式质量 workflow 只在 execution 提交成功后触发，并以 `datasetId + ingestionExecutionId` 保证绑定与幂等。
14. 接入成功与质量通过是两个正交事实；质量失败、重试或耗尽不能改写接入成功账本。
15. 新 execution 提交后，上一批次的通过结果必须对“当前可信”失效，直到新证据进入 `CURRENT`。
16. 资产生命周期、质量结果、质量证据新鲜度、消费资格分别建模；不新增笼统的“可信/已验证”生命周期状态。

## 运行数据画像（2026-08-27）

| 对象 | 数量/分布 | 产品与架构含义 |
|---|---|---|
| `ingestion_task` | 13；ACTIVE 7，删除 6 | 先全量盘点 7 条活跃任务，无需抽样推测 |
| 活跃源类型 | httpreader 1、mysqlreader 2、txtfilereader 4 | 首个金丝雀复用现有 connector，不发明新类型 |
| `graph_dsl` | 12 SQL NULL、1 JSON null、0 对象 | 没有自由画布使用证据，不是新链路事实源 |
| revision | ACTIVE 11、DRAFT 2、SUPERSEDED 30 | 版本 owner 已存在，可扩展 plan identity |
| DAG deployment state | ACTIVE 3、NULL 40 | 旧 revision 大量可空，migration 必须兼容 |
| execution | 88；成功 7、失败 81；2026-08-03～08-07 | 失败率约 92.05%，需先选稳定金丝雀 |
| Airflow DAG | 57；ingestion 34 | 仅 3 个匹配当前标识，31 个需逐项归类 |
| Airflow import error | 0 | 调度器当前可加载，不代表业务链路闭合 |
| 审计动作目录 | 590；本域相关动作启用且有记录 | 扩展现有审计 owner，不新建旁路 |

## 外部边界

- Airflow：调度与运行基础设施，不是业务身份或授权边界。
- Addax：接入执行适配器，其配置来自版本化任务。
- 数据目录/密级：提供稳定对象与分类控制；design 不复制主数据。
- 数据质量：保持独立 workflow 与规则执行 owner；接入域只在提交后传递目标 `datasetId` 与 `ingestionExecutionId`，并回链 workflow/run 证据。
- 数据资产：目录域维护唯一资产身份与资产详情投影；接入域负责观察目标资产，质量域负责正式证据，消费资格由既有语义契约派生。
- 建模/dbt：未来若提供转换制品，由其 owner 发布版本；本 Sprint 不接入。
- 审计中心：所有变更与命令统一进入现有严格审计链。

## 待 F0 补齐

- 全量 7 条活跃任务与客户流程的复杂度分类。
- 连续成功的金丝雀任务、数据量、耗时和可恢复性。
- 81 条失败 execution 的 connector/错误码/revision 抽样归因。
- 31 个未匹配 DAG 的来源、最后运行、暂停状态与调用方。
- 合法、非法、并发冲突三个类型化 task design 样本。
- 一个可解析为 canonical dataset 的目标资产，以及该数据集至少一条已发布、启用的质量规则绑定。
- 金丝雀 execution、quality workflow、quality run 与资产详情之间可重复核对的完整证据链。
- 活跃任务 `qualityPolicyRef` 的已配置/未配置/无有效规则分布，禁止用理想数据假设开放控件。
