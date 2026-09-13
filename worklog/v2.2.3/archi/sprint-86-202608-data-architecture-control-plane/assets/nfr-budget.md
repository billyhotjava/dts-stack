# Sprint-86 非功能预算与 Fitness Functions（Gate G1）

**状态**：ACCEPTED_DESIGN（Gate G1：PASS；运行验证转 Sprint-87）

**评审入口**：IT-07、[`consolidated-approval-pack.md`](consolidated-approval-pack.md)

**用途**：把会改变架构选择的容量、时延、幂等、审计和兼容约束前置到 ADR；不在本 Sprint 冒充已完成性能测试。

## 1. 已知事实与禁止假设

| 事实 | 当前证据 | 约束 |
|---|---|---|
| 本地资产 83、归域 0 | `domain-profile.md` §3 | 只能验证语义，不能代表客户容量 |
| 客户域口径 10～50、两层 | 用户口径，见 `domain-profile.md` §3 | F4 可按此形成首版单一形态，但必须保留超规模降级 |
| 资产统计扫描上限 5000 | `CatalogAssetPortalService` 既有实现 | 纳管扩展后不得继续假设请求内全量扫描可扩展 |
| 每次资产地图加载已有两轮扫描 | `domain-profile.md` §3.1 | 统计方案须与 ADR-86-04/16 同批冻结 |
| 批量物化规模、客户资产量和增长率未知 | `domain-profile.md` §7 | 先以本文件已批准设计值约束方案；未取得画像前 Sprint-87 F0 仍保持 BLOCKED_INPUT |

## 2. 决策预算

`GAP` 表示预算值或实现策略尚未冻结；`PROPOSED` 只允许进入评审，不代表可实施；`ACCEPTED_DESIGN` 表示架构约束已冻结，但运行时 fitness function 尚未在后续实施 Sprint 执行。

| ID | 质量属性 | 已批准设计预算/硬约束 | Fitness function | Owner | 状态 |
|---|---|---|---|---|---|
| NFR-86-01 | 资产统计容量 | 首版设计容量 100,000 个资产、50 个数据域；不得静默漏计；兼容 fallback 触顶只返回 `≥N/isApproximate/asOf` | 下一 Sprint `AssetStatsCapacityIT` 构造 100,000 条资产及未归域/50 域分布，断言精确投影；fallback 5001 条断言 `≥5000` 而非伪精确值 | F2/T01 | ACCEPTED_DESIGN |
| NFR-86-02 | 统计请求成本 | 100,000 资产/50 域下统计 API P95 ≤ 1.5 秒，单请求统计 SQL ≤ 3；禁止无界全量扫描 | `AssetStatsQueryPlanIT` 统计查询数并保存 `EXPLAIN`；超过 3 条、P95 超限或目标量级出现未批准 Seq Scan 即失败 | F2/T01 | ACCEPTED_DESIGN |
| NFR-86-03 | 统计新鲜度 | 增量投影延迟 ≤ 5 分钟；>10 分钟标 `STALE`；每 24 小时至少一次 reconciliation；API 返回 `asOf` | `AssetStatsFreshnessIT` 在 5/10 分钟边界断言 freshness/asOf，并注入漂移证明 24 小时对账可修复 | F2/T01、F4/T01 | ACCEPTED_DESIGN |
| NFR-86-04 | 批量选择规模 | `BATCH_MAX=100` 个 root models；UI 可跨页显式保留选择，但不提供无界“选择全部结果” | `ReleaseCandidateBatchContractIT` 断言 100 成功、101 返回 422 且零候选/零派发；前端测试断言跨页选择可见 | F1/T02、F4/T01 | ACCEPTED_DESIGN |
| NFR-86-05 | 依赖 DAG 规模 | `DAG_NODE_MAX=500`、`DAG_EDGE_MAX=2000`、最大深度 20；跨计划只允许固定已发布 revision | `ModelDependencyDagIT` 在 500/2000/20 边界断言拓扑/去重；任一 +1、成环或 revision 漂移返回具名错误且零派发 | F1/T02 | ACCEPTED_DESIGN |
| NFR-86-06 | 候选与重试幂等 | 创建必须带 Idempotency-Key；相同 key/hash 重放同一结果，不同 payload 冲突；同 revision 合法重试新增 attempt/observation | `MaterializationIdempotencyIT` 重放创建请求断言无重复候选；payload 冲突失败；授权重试新增 attempt；新 revision 新建 entry | F1/T02、F5/T01 | ACCEPTED_DESIGN |
| NFR-86-07 | 长任务时限与反馈 | 候选预检/创建在 500 节点下 P95 ≤ 5 秒；轮询前台 5 秒/隐藏页 30 秒；10 分钟无 heartbeat 为 STALE；默认运行上限 120 分钟；取消 5 秒受理、5 分钟终态或 `CANCEL_FAILED` | API 基准 IT + `materialization-status.spec.ts` 注入隐藏页、无 heartbeat、超时、部分失败和取消，断言无并发轮询/无限 loading 且历史可查 | F4/T01、F5/T01 | ACCEPTED_DESIGN |
| NFR-86-08 | 批量审计 | 一个批次审计锚点 + 每个对象结果；失败项、重试、操作者、attempt 和 correlationId 可追溯 | `BulkAssetAuditIT` 对混合结果断言批次/单项事件与失败原因；未授权请求断言无业务写入且拒绝事件可查 | F1/T02、F2/T01 | ACCEPTED_DESIGN |
| NFR-86-09 | 权限与职责分离 | ADR-86-17 方案 A 已批准：唯一 application command boundary；`ROLE_ADMIN/ROLE_OP_ADMIN/ROLE_INST_DATA_OWNER` 可写全局字典、部门角色只读；UI 不作安全边界，审计不冒充预防控制 | `ArchitectureOwnerAuthorizationIT` 用非 allowlist actor 断言所有写入口 403/拒绝；ArchUnit/依赖规则断言消费者和 Controller 不直写架构字典 Repository；审计断言拒绝/成功均可追溯 | F5/T01 | ACCEPTED_DESIGN |
| NFR-86-10 | 浏览器与页面契约 | Chrome 95、服务端分页默认 10、四态和 source-contract；320/768/1024/1440 宽度可达 | `modeling-chrome95.spec.ts` + 对应 `*.source-contract.test.ts` 断言分页和空/加载/错/成功；真实 E2E 必须点击菜单入口 | F4/T01、F5/T01 | ACCEPTED_DESIGN |
| NFR-86-11 | 索引 | domain、layer、五轴状态、physical locator 与投影连接列须有匹配索引；100,000 资产目标查询不得无界 Seq Scan | `CatalogAssetIndexIT` 查询系统表断言索引存在，`AssetStatsQueryPlanIT` 保存计划并证明目标查询使用索引 | F2/T01、F5/T01 | ACCEPTED_DESIGN |
| NFR-86-12 | 并发与事务 | 同 `plan + environment` 最多 1 个 active candidate；同 candidate entry 最多 1 个 active attempt；候选创建全有或全无 | `MaterializationConcurrencyIT` 并发提交相同/冲突请求，断言唯一候选/attempt、约定 409 和无半成品派发 | F1/T02、F5/T01 | ACCEPTED_DESIGN |
| NFR-86-13 | 平台全局可见性 | 当前架构字典不按 tenant 隔离；遗留 tenant 字段由服务端固定平台 scope，不造成静默过滤或第二份数据 | `ArchitectureScopeContractIT` 使用不同现有角色断言同一全局读投影；非 allowlist 写入失败，客户端 tenant 输入被拒绝/忽略并审计 | F1/T01、F5/T01 | ACCEPTED_DESIGN |

## 3. G1 通过条件

- [x] xiezm 接受本文件的设计容量；Sprint-87 F0 仍须补客户/生产真实画像。
- [x] ADR-86-04/16/18/19 与 NFR-86-01～03 同批冻结，统计方案不会反向破坏资产状态和来源语义。
- [x] ADR-86-15 冻结 `BATCH_MAX`、DAG 边界、原子性、幂等和重试授权。
- [x] ADR-86-17 区分预防控制、侦测控制和剩余风险，不把审计后置检测表述为权限强制；运行时 fitness function 转入下一实施 Sprint。
- [x] 每项预算已有批准值/方案、负责人、具名可执行 fitness function 和对应 Sprint-87 Task。
- [x] IT-07 记录真实参与者、选择、反例、异议和批准结论。

Gate G1 的**架构设计**已通过；所有运行时 fitness functions 仍未执行，涉及统计、批量物化、权限或 UI 性能的 Sprint-87 Task 继续受 F0/G0 与各自 DoR 约束。

## 4. 未达标项处置

| 缺口 | 影响 | 当前处置 | 关联 Task |
|---|---|---|---|
| 客户资产量、增长率和典型批量规模未知 | 已批准值只能作为设计容量，不能证明客户环境可运行 | Sprint-87 F0 补画像并在超出设计容量时重新评审 | F5/T01、Sprint-87 F0/T01 |
| 资产统计运行验证未执行 | 纳管范围扩大后仍可能因实现偏差触发全量扫描或伪精确计数 | 已批准 ADR-86-16 增量投影 + 24h 对账；Sprint-87 执行 NFR-86-01～03/11 fitness functions | Sprint-87 F3/T01 |
| 批量/DAG/超时运行验证未执行 | 设计值尚未被真实实现证明 | Sprint-87 按 N04～N10 实现并执行边界/故障注入测试 | Sprint-87 F2/T01、F5/T01、F6/T01 |
| 产品权限粗粒度，后端宽角色不是数据架构专属授权 | 代码 owner 与 actor 职责可能继续混淆 | IT-07 权限子评审已通过方案 A；F5/T01 须把 allowlist、负向测试和依赖规则拆入下一实施 Sprint | F5/T01 |
| UI 尚未实现 | Chrome 95、四态和 source-contract 尚不能执行 | 已转入 Sprint-87 F5/T01、F6/T01，编码完成后集中验证一次 | F4/T01、F5/T01 |
