# Sprint-69 Task/UI 验收矩阵

## 1. 验收分层

| 层级 | 适用 Task | UI 完成含义 | 可关闭证据 |
|---|---|---|---|
| UI 契约 | F1-F4 | API/状态/错误/allowedActions 足以稳定驱动页面，不让前端猜状态 | contract fixture、view-model test、source-contract test |
| UI 实现 | F5 | 页面、信息架构、交互、响应式和恢复路径已实现 | component/helper test、production build、mock-API Chrome 95 |
| UI 真实验收 | F6 | 页面在真实认证、API、PostgreSQL、dbt 和 Chrome 95 中形成闭环 | 真实 E2E、截图/trace、API/DB 对账 |

F1-F4 的 UI 契约验收不要求提前实现 F5 页面，因此不会产生循环依赖；F5 必须消费已经冻结的契约，F6 再关闭最终真实 UI 门禁。

## 2. Task 映射

| Task | UI 页面/区域 | 客户可见结果 | 必测状态 | 本 Task UI 证据 |
|---|---|---|---|---|
| F1-T01 | 交付工作台阶段头、状态条、主动作区 | “可构建、质量未通过、待审核、已批准、已发布”不会混成一个完成状态 | READY、QUALITY_PASSED、REVIEW_PENDING、APPROVED、PUBLISHED | 状态文案与 allowedActions contract fixture |
| F1-T02 | 候选范围面板 | 刷新后候选范围、顺序和版本锁稳定，无重复模型 | empty、loaded、version conflict | repository fixture 到 candidate view-model 映射测试 |
| F1-T03 | 候选编辑、STALE 提示、并发冲突对话框 | 漂移后明确提示原因并禁用后续动作；并发覆盖需要人工选择 | STALE、409、duplicate request | command response/ProblemDetail 与 UI 分支契约测试 |
| F1-T04 | 工作台首屏 | 一次加载得到范围、首要阻塞、下一步和 ETag | loading、empty、error、forbidden、ready | 聚合响应快照与前端 adapter test |
| F2-T01 | 构建证据面板 | 静态文件生成与真实 dbt run 分栏显示，不用一个“成功”混淆 | artifact only、run running、run failed、run passed | evidence view-model fixture |
| F2-T02 | Build Evidence Drawer | 可查看 selector、target、invocation、revision、checksum；不匹配时显示阻塞 | target mismatch、node missing、stale run、passed | run-verification 错误码到 UI 文案映射测试 |
| F2-T03 | 实现版本卡、STALE Banner | 展示 implementation owner/path/checksum，漂移时给出重新锁定入口 | single owner、multi-owner、checksum drift | implementation summary fixture |
| F2-T04 | 构建失败详情与修复动作 | 直接定位模型、文件、行列和日志，并仅重试失败条目 | retryable、non-retryable、partial failure | diagnostics contract 与修复 URL 测试 |
| F3-T01 | 质量证据抽屉 | 按运行和规则查看阈值、实际值、严重度、样本摘要 | running、partial、failed、passed | quality response fixture 与敏感字段断言 |
| F3-T02 | 默认规则包预览 | 按模型类型解释“为什么检查这条规则”，可查看版本与来源 | FACT、DIMENSION、SUMMARY、APPLICATION | rule-pack view-model snapshot |
| F3-T03 | 质量运行进度与结果列表 | 规则结果完成后才出现通过结论，缺失/篡改结果显示阻塞 | missing rule、duplicate、tampered、passed | ingestion response 与 UI 状态映射测试 |
| F3-T04 | 质量门禁 Banner、失败筛选、修复入口 | 显示过期/漂移原因、失败规则和精确重跑动作 | expired、drifted、warning、blocking failure | blocker/remediation route contract test |
| F4-T01 | 审核发布面板 | build/quality 通过后只出现“提交审核”，批准、拒绝、发布分步出现 | QUALITY_PASSED、REVIEW_PENDING、APPROVED | allowedActions 时序测试 |
| F4-T02 | 审批按钮与无权限说明 | 同一提交人看不到可执行批准动作；无权限用户看到正式申请说明 | submitter、reviewer、publisher、forbidden | capability matrix 与 403 UI 分支测试 |
| F4-T03 | 发布步骤台账 | Catalog、BI、Lineage 逐步显示状态；PARTIAL 只对失败步骤提供重试 | publishing、partial、retrying、published | registration-step view-model test |
| F4-T04 | Stage 6 状态、发布时线、回滚对话框 | 发布后完成，回滚后即时降级；历史发布与回滚均可查看 | published、rollback running、rolled back、rollback failed | projection/timeline fixture |
| F5-T01 | 交付工作台全部区域 | 桌面和 390px 下状态、主动作、阻塞文案一致且无仅靠颜色表达 | loading、empty、error、stale、forbidden | view-model/component snapshots |
| F5-T02 | 候选范围与工作台壳 | 页面内完成创建、选模、移除、锁定和冲突恢复 | first use、editing、locked、409 | component test、mock-API Chrome 95 |
| F5-T03 | 构建与质量详情 | 不查后台即可定位首个修复点；大日志/样本按需加载 | build failed、quality failed、expired evidence | component test、键盘与响应式验收 |
| F5-T04 | 审核、发布、重试、回滚 | 所有高风险动作有权限提示、影响摘要、确认和失败恢复 | approve、reject、partial retry、rollback、timeout unknown | component test、mock-API Chrome 95 |
| F5-T05 | SQL/dbt 页面及返回链 | 页面只编辑/执行，候选上下文固定；保存漂移后回工作台更新候选 | deep link、refresh、back、多标签、stale | navigation test、Chrome 95 回归 |
| F6-T01 | 全部命令型 UI | 400/403/404/409/422/500 均有稳定页内反馈和恢复动作 | invalid、forbidden、not found、conflict、gate blocked | API 错误码与 UI 分支覆盖报告 |
| F6-T02 | 工作台刷新与多角色切换 | API、数据库和页面状态一致，不能靠预置终态或前端缓存完成 | full lifecycle、partial retry、rollback | 真实 API/DB/UI 对账截图与日志 |
| F6-T03 | Chrome 95 桌面与 390px 主旅程 | 用户从候选创建走到发布，并完成失败修复和精确重试 | happy path、dbt failure、quality failure、409 | Playwright trace、截图、真实 runId |
| F6-T04 | 发布后页面与回滚后页面 | 部署制品显示正确版本；升级、回滚、重启后页面状态不漂移 | deployed、restarted、rolled back、re-upgraded | 六层 Go/No-Go 与回滚前后截图 |

## 3. UI 硬门禁

- 任一 Task 的 UI 验收项没有对应自动化或人工证据时，该 Task 不得标记 DONE。
- 只有 source-contract、mock 数据或静态截图时，最多关闭 F1-F5 对应任务，不能关闭 F6。
- 页面不能自行推导 lifecycle 状态；未识别状态必须 fail closed 并显示“状态暂不可用”。
- 后端成功但 UI 无入口、无结果、无错误恢复或刷新后丢失，均视为 Task 未完成。
- Chrome 95、390px、键盘操作、非颜色状态表达和真实权限属于 Sprint DONE 门禁。

