# T03：验收可视化选择 dbt 与高级实现的发布物化旅程

**优先级**：P1
**状态**：E2E_PENDING
**依赖**：F0/T05、F2/T03～T04、F4/T01～T04、F5/T02～T03

## 目标

证明普通可视化模型无需接触 SQL 即可选择/确认 dbt 实现和物化策略，经 canonical 发布/物化得到固定物理表预览；同时证明 DBT_MANAGED 的高级技术维护仍复用同一模型上下文和实施修订，不产生独立入口。

## 验收路径

1. 打开 DESIGNER_GENERATED 模型的普通业务可视化，确认页面不显示 SQL/Jinja、macro、project path、compiled SQL 或完整 dbt DAG。
2. 在现有“发布与物化”流程选择/确认系统生成的 dbt 实现与受支持物化策略，形成固定 Implementation Revision。
3. 运行 StageGate，创建/审核 ReleaseCandidate；DbtExecutionGateway → Airflow/dbt build → relation probe。
4. 验证 PUBLISHED 只推进 latestPublishedRef；成功 relation evidence 后 servingRef 才切换。显式点击加载默认 100 行脱敏样例，验证 500/501、no-store、无导出和审计零样例值。
5. 对 DBT_MANAGED fixture，在同一模型详情显式进入高级 dbt 实现，修改并 validate/commit 新 Implementation Revision；普通可视化仍不展示 SQL，旧 revision 可复现。
6. 技术维护者预览成功未发布 candidate，页面标记“候选结果，非正式资产”；普通用户被拒绝。历史 revision 只显示固化结构，不提供样例行。
7. 让 r2 物化 FAILED/FAILED_STALE 并发送乱序 callback，确认 latestPublished 可为 r2、serving 仍为 r1；r2 成功后才 CAS 切换。

## 证据

- [ ] UI/API/DB/Airflow/dbt/relation/audit 全链 correlationId。
- [ ] shared projectDir 未成为事实源；无直接 `/etl/dbt/run`。
- [ ] 无新增菜单、独立模型清单或平行 SQL 页面。
- [ ] Catalog 双指针、serving/candidate/history 预览、安全与审计证据符合 D08/D12。

## Definition of Done

- [ ] 逻辑、技术、物理三类事实的 revision/provenance 可解释且一致，但技术 SQL 不泄露到普通可视化。
