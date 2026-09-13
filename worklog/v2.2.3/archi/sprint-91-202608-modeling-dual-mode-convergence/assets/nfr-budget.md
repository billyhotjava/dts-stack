# 非功能预算 (Gate G1)

**依据**: `assets/domain-profile.md` + 既有 dbt draft/lifecycle 契约
**适用范围**: 双模式 UI、代码预览、ownership transition、统一依赖快照、可视化转换、单表/批量物化、dbt 草稿编辑与发布回归

| 维度 | 预算 | 适应度函数（可执行） | 归属 Task | 状态 |
|---|---|---|---|---|
| 数据/文件上限 | 保持 128 文件、单文件 2 MiB、总计 16 MiB；不得前端放宽 | `DbtImplementationDraftContractTest` 断言 129 文件/超限字节返回 4xx；前端边界测试显示服务端原因 | F4/T01、F4/T02 | PASS |
| **前端体积** | 建模页**首屏**不含 `monaco-editor`；代码模式 chunk 走懒加载。首屏 JS ≤ 300 kB gz（项目 App page 预算） | 构建后比对建模路由 chunk 组成，断言 monaco 只在懒加载 chunk；浏览器在 `view=visual` 下不请求该 chunk；对照 build 产物体积表 | F4/T01、F5/T02 | PASS_WITH_GAPS —— build 已分出独立 DbtCodeEditor/Monaco chunk；真实 network 待验 |
| **制品类型守恒** | 接管落地类型集合**等于** `{SQL,SCHEMA,CONFIG}`；CONFIG 是含 `dbt_project.yml` + 3 个生成文件的 4 文件 bundle | 接管后查 `modeling_dbt_artifact` 断言类型集合；立即 `compile`；恢复 CONFIG 并断言 4 文件、含 `stg_*.sql` | F0/T02、F2/T02、F5/T01 | PASS_WITH_GAPS —— service/compiler 自动化通过；真实库探针待验 |
| **物化身份稳定** | 接管前后 `dbtUniqueId` 与 `ExecutionPlan.selector` 逐字符不变；transition 使用严格解码且不接受这两个字段 | 接管前后比对 selector；携带未知字段的请求返回 400 | F2/T02 | PASS（设计层，ADR-91-08） |
| 请求次数 | 打开模型最多 1 次 lifecycle + BUSINESS/TECHNICAL 各 1 次表示请求；已加载后的本地视图切换不重复请求 | Vitest mock 断言切换 10 次请求计数不增长 | F1/T01、F1/T02 | PASS_WITH_GAPS —— URL/视图 source contract 通过；真实请求计数待 browser IT |
| 并发 | 所有转换与草稿写入必须携带 model/implementation ETag；旧 ETag 不得覆盖 | 并发 IT：两个客户端从同一基线提交，恰好一个成功，另一个 409/412 | F2/T02、F4/T02 | PASS_WITH_GAPS —— 双 ETag/CAS 与 409/412 写锁已测；真实双客户端待验 |
| 幂等 | 同一 transition/draft commit `idempotencyKey` 重放返回同一 receipt，不新增修订 | 集成测试比较两次响应 transitionId/revision/checksum，并断言表行数只增 1 | F2/T02 | PASS_WITH_GAPS —— service replay/conflict 已测；真实表行数待验 |
| 事务一致性 | ownership 转换成功后 model 与 current implementation ownership 一致；失败后两者均不变 | 事务 IT 注入 artifact/command receipt 写失败；查询 mismatch count 始终为 0 | F2/T02 | PASS_WITH_GAPS —— 单事务 seam 与专用 CAS 已测；真实失败注入待验 |
| 查询效率 | 本 Sprint 不增加列表或全表查询；所有预览/转换按 `modelSpecId + revision` 定位 | Repository/IT 断言请求不调用 `findAll`；查询使用现有主键/唯一键 | F2/T01、F2/T02 | PASS |
| 依赖计划容量 | 单次请求目标 ≤64、去重后闭包节点 ≤256、最大依赖深度 ≤32；超限返回稳定 4xx，不截断执行 | 64/256/32 边界与超限契约测试；随机 DAG 验证拓扑、去重和 cycle path | F6/T01、F8/T01 | GAP |
| 依赖查询效率 | 256 节点闭包使用有界批量查询，不允许按节点逐个读 model/implementation/relation | repository spy 断言查询次数为常数或按固定批次增长；禁止 `N+1` | F6/T01、F8/T01 | GAP |
| 计划延迟 | 256 节点、4 层典型闭包的本地集成基线 p95 ≤2s；线上 SLO 待 F0 实测校准 | 固定数据集连续执行并记录 p50/p95/max；若环境噪声不适合 SLO，仍以 2s 作为回归门禁 | F8/T01 | GAP |
| 计划可重放 | 相同 immutable pins/环境/策略得到同一 plan checksum；任一依赖、实现或 relation observation 漂移必须改变结果或阻断旧计划 | 顺序打乱/幂等测试 + preview 后漂移 candidate create 409/412/stale 测试 | F6/T01、F8/T01、F8/T03 | GAP |
| source 围栏一致 | DESIGNER_GENERATED 与 DBT_MANAGED 均校验 dependency snapshot 中的 ODS binding resolved version/可用性 | 两种 ownership 对相同 stale/unavailable binding 返回等价 blocker；`GENERATED` 不得跳过 | F6/T03、F8/T03 | GAP |
| 批量执行隔离 | 一个 batch correlation；每个 BUILD 模型独立 entry/attempt/observation；单节点失败不得伪造其他节点成功 | 混合成功/失败 IT，断言状态、重试与审计按节点隔离且批次可追溯 | F8/T03 | GAP |
| 审计 | 每次成功/拒绝的 ownership transition 均有分类、actor、correlation/transition id 与版本摘要 | IT 查询审计记录，断言分类非“未分类”且敏感文件内容不入 payload | F2/T02 | GAP —— 成功路径严格审计与 runtime catalog 已实现；拒绝事件持久审计/真实查询未闭合 |
| 权限 | 维护/发布动作继续走现有 command guard；所级管理员可执行其已授权 duty，部门账号受所属范围限制；状态必须由显式命令推进 | MockMvc/浏览器断言越界 403；`xiezm` 自服务链逐步记录 actor/duty；断言无 UI 自动审批/自动发布 | F0/T01、F2/T03、F5/T01 | PASS_WITH_GAPS —— endpoint/UI fail-closed 已实现；真实所级/部门路径待验 |
| 失败模式 | 投影不可信（动态依赖、字段/依赖不可信、pin 不匹配）只返回 reasons，不提供伪编辑入口 | representation IT 断言 `BLOCKED` + reasons；前端断言组件树无写控件、无回切按钮 | F3/T02 | PASS |
| 兼容性 | Chrome 95；旧 `open=advanced` 仍进入 code view；旧 convert endpoint **原样保留不被触碰** | legacy build + Chrome95 smoke + route source-contract + REST compatibility test；git diff 断言 `convertToDesignerGenerated` 未改动 | F1/T02、F5/T02 | PASS_WITH_GAPS —— legacy build/source contract 通过；真实 Chrome 95 待验 |
| Monaco worker | 不引入 `MonacoEnvironment`/`getWorker`，沿用主线程 | source-contract 断言无新增 worker 配置；Chrome 95 smoke 无 worker 加载错误 | F4/T01、F5/T02 | PASS_WITH_GAPS —— source/build 通过；浏览器 smoke 待验 |
| 发布一致性 | 发布候选钉住最新 model/implementation/dependency/plan checksum，任一 ownership/revision/checksum 漂移必须转 STALE | 手工/可视化/ZIP/接管路径 release IT；旧候选转换后返回稳定 stale code | F8/T03、F5/T01 | BLOCKED —— F6～F8 未实现；既有 BUILT 取消语义冲突及真实链路 IT 待处理 |
| 外部网络延迟 | 本 Sprint 不承诺新的线上 SLO；内部 plan preview 仍受上面的 2s 回归预算约束 | 记录浏览器 network 基线；F0 取得现场数据后只允许收紧或通过 ADR 调整 | F8/T01、F5/T02 | GAP |
| 租户 | 继续复用现有 tenant-scoped repository；不新增跨租户查询 | 跨租户 IT 以相同 model UUID 请求返回不可见/404，不返回预览内容 | F2/T01、F2/T02 | PASS |

## 未达标项处置

**首版「当前无设计层 GAP」的结论已作废**（2026-08-13 架构复核）。源码实现后仍有以下验收 GAP：

| GAP | 关闭路径 | 未关闭前的约束 |
|---|---|---|
| 真实库事务与制品 | F0/T01 建样本后执行 transition、失败注入、artifact/compile 查询 | 不宣称原子转换已在 PostgreSQL 现场验证 |
| 前端 runtime 体积 | F5/T02 在 `view=visual` 采集 network | 不宣称 Monaco 在真实首屏零请求 |
| 审计拒绝事件 | 明确拒绝审计的事务边界并补可执行 IT | 不把成功审计等同于全失败路径可追溯 |
| Chrome 95 | 登录态目标浏览器完成 console/network/screenshot smoke | 不以 legacy build 代替真实浏览器通过 |
| 统一发布 | 先裁决 BUILT 取消语义，再跑手工/可视化/ZIP/接管的显式命令发布和物理 ONLINE | F5 保持 BLOCKED，Sprint 不得 DONE |
| 依赖闭包/计划 | F6/T01 与 F8/T01 完成容量、批量查询、checksum 和延迟适应度函数 | 不开放单表/批量物化新交互，不用前端计算依赖兜底 |
| source 围栏与二次物化 | F8/T03 统一两种 ownership 的 source fence，并完成身份/历史数量对账 | 不以一次维度表成功推断全链或二次物化安全 |

登录、Chrome 95 和真实数据样本属于 `it/baseline.md` 的交付基线 GAP，由 F0/T01 关闭；未关闭前相关 UI/发布 Task 保持 IN_PROGRESS 或 BLOCKED。
