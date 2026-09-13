# 产品与架构决策登记表

**用途**：这是 Sprint-83 从 DRAFT 进入 READY 的硬门。每项必须由讨论确认后标记 `ACCEPTED` 或给出替代方案；未确认不得编码。

| ID | 决策问题 | 架构建议 | 当前状态 | 影响 Feature |
|---|---|---|---|---|
| D01 | 普通业务可视化展示什么 | 显示逻辑表/字段、业务或模型依赖、物理表结构和 D12 受控样例；SQL/Jinja、macro、project path、compiled SQL 和完整 dbt DAG 全部隐藏 | ACCEPTED（2026-08-01） | F1/F2 |
| D02 | DBT_MANAGED 与 dbt 物化的关系 | `DBT_MANAGED` 仅表示高级/外部 dbt SQL/Jinja 拥有技术实现；不表示业务页面显示 SQL，也不表示只有它能用 dbt。`DESIGNER_GENERATED` 仍以可视化设计为事实源，由系统生成隐藏 dbt 制品并通过同一网关物化 | ACCEPTED（2026-08-01） | F1/F2/F3/F4 |
| D03 | 高级 dbt 实现的信息架构 | 放在现有模型详情“数据实现”阶段，和普通可视化互斥显示；复用同一模型上下文，不新增菜单、独立模型清单或第二个模型中心 | ACCEPTED（2026-08-01） | F2 |
| D04 | 外部接入方式 | P0 只支持用户上传 ZIP 快照；不接 Git 地址、凭据、clone/sync/push 或在线 packages 下载 | ACCEPTED（2026-08-01） | F3/F5 |
| D05 | 重新导入冲突 | 以最近一次已接受合并检查点为 base，对 DTS 当前技术实施 current 与新 ZIP incoming 做逐节点三方比较：无外部变化 SKIP、仅 incoming 变化显式 UPDATE、仅 current 变化保留 current、双边收敛 SKIP、双边分歧 CONFLICT；ModelSpec 业务语义始终保留，只有映射失效才 BLOCKED_REMAP；缺失/重命名不自动删除或猜测映射 | ACCEPTED（2026-08-01） | F1/F3 |
| D06 | 导出范围 | Sprint-83 明确不做 ZIP 导出；当前 UI 不出现导出/Git 入口。未来如纳入，另建 Feature，只允许 revision-pinned、secret-clean 的受控 ZIP；Git push 继续独立评审 | ACCEPTED_DEFERRED（2026-08-02） | 非目标 |
| D07 | 部分成功与撤销 | 允许 PARTIAL；每个 FAILED/BLOCKED 项必须返回稳定失败码、失败阶段、安全原因、是否可重试、恢复动作和 correlationId，禁止只显示汇总或 toast；retry 幂等且不重复成功项；撤销使用受资格约束的前向修订，不删历史 | ACCEPTED（2026-08-01） | F3/F5 |
| D08 | Catalog 登记时点 | DRAFT 仅在建模域；PUBLISHED 以既有 CatalogAssetKey 登记可发现逻辑资产并推进 latestPublishedRef，但不让新修订可消费；成功 MATERIALIZED 且 relation evidence/质量门禁成立后才切换 servingRef 并关联物理资产。失败/stale/build-only 不切换 serving，旧成功 serving 可继续消费 | ACCEPTED（2026-08-01） | F4 |
| D09 | 首期 dbt 版本/adapter | 兼容拆分为 inspect、import projection、materialization 三轴；具体 Core+adapter+数据源组合必须精确锁定并由 fixture/真实运行认证。首期只把 PostgreSQL 作为 adapter 候选；未认证组合物化 fail-closed | ACCEPTED_POLICY / CERTIFIED_EVIDENCE / DEPLOYMENT_PENDING（2026-08-03） | F0/F1/F6 |
| D10 | source-only 包处理 | 只做静态 inspect。普通 model 只有 enforced schema contract（字段 `name+data_type` 完整）、literal 依赖闭包和业务语义同时成立时才可 apply；其余结构最多只读，apply BLOCKED。BLOCKED 项不可选择，独立合格闭包可单独 apply；PARTIAL 只来自已选合格项启动后的逐项失败。动态/macro/package 缺口不可人工豁免；成功项保持 DBT_MANAGED | ACCEPTED（2026-08-02） | F1/F3/F5 |
| D11 | 所有权转换 | Sprint-83 不实现所有权转换；导入模型始终保持 DBT_MANAGED，选择 dbt 物化也不改变 ownership。未来另立 preview→confirm→apply Feature，复用现有 maintainer/admin + write，不发明权限或原地覆盖 | ACCEPTED_DEFERRED（2026-08-02） | 非目标 |
| D12 | 数据预览上限和脱敏 | S3 物化切片提供用户显式加载的受控样例，默认 100、硬上限 500、不分页/导出/持久缓存；普通视图只读 serving，技术维护者可读成功未发布 candidate 并标记非正式；历史 revision 仅看结构不返回样例行；表级密级和列 ALLOW/MASK/DENY 全部服务端 fail-closed | ACCEPTED（2026-08-01） | F2/F5 |
| D13 | inspect 到 preview 的可信交接 | 安全 inspect 返回有效期 30 分钟的 `inspectionProof`，签名载荷绑定 tenant、canonical actor、规范化技术包 checksum 和 expiry。preview 必须验证原技术包未变化，仅允许提交白名单 `context/selection/semanticOverrides`；无效和过期分别返回稳定码 `DBT_IMPORT_INSPECTION_PROOF_INVALID`、`DBT_IMPORT_INSPECTION_PROOF_EXPIRED`。复用平台托管的签名/密钥能力，不新建 inspect 表、不自造应用 secret | ACCEPTED（2026-08-02） | F3/F5 |

## 决策收口结论

1. D01～D13 的产品与架构政策均已确认；D06、D11 为明确延期非目标。
2. D09 的政策已确认；H83-01 只产生 runtime 候选与原始 RT-01 证据，F0/T05 是唯一认证状态 owner，负责登记 `certificationProfileId` 并执行 `NOT_CERTIFIED → CERTIFIED`。F0/T02 只提供 parser/fixture 证据，F6/T01 只消费并回归认证结果，不能被“确认决策”或镜像标签替代。
3. D10 的行为边界已冻结，fixture 只用于验证实现，不再重新讨论是否允许无字段或动态依赖强行导入；其实现排入 P1 不改变政策，未完成时必须 fail-closed。
4. 详细兼容、source-only 与 `inspectionProof` 契约见 [`dbt-compatibility-and-source-only-contract.md`](dbt-compatibility-and-source-only-contract.md)；apply 状态代数和旧值前向迁移见 [`import-partial-result-contract.md`](import-partial-result-contract.md)。
5. 客户脱敏包只控制客户兼容声明和现场验收；通用工程 DoR 使用重新构造或许可固定、可离线复现的 fixtures，禁止两类证据互相冒充。
