# T03：执行旧 BI 清理与数仓重建发布门禁

**优先级**：P0
**状态**：BLOCKED
**依赖**：T01、T02、F0/T04；R1 Expand 稳定并完成旧镜像回切演练

## 可测试目标

在独立 Contract 发布窗口停止 legacy 写入、冻结 Card/Dashboard cutoff、备份并恢复验证 Analytics 数据库，清理不再需要的 Metabase 仿造数据与入口；如需重建 DWD/DWS/ADS，由数据建模发布流程单独执行和验收。全过程不得影响大屏历史链。

## 发布契约

| 阶段 | 操作 | 失败处理 |
|---|---|---|
| Freeze | 关闭 legacy write，记录 Card/Dashboard max id 与 screen durable set | cutoff 后出现新 legacy 行则重做 |
| Backup | 完整 `pg_dump` 并在隔离库恢复 | 未恢复验证不得 dry-run/apply |
| Preflight | cleanup dry-run + screen/template 全历史引用扫描 | 任一 Card 引用立即阻断 |
| Apply | 显式 `backup_confirmed=true, apply=true`，只删 cutoff 以内旧 BI 行 | 对账异常立即停服并恢复完整备份 |
| Route/schema contract | 数据清理稳定后删除 legacy handler/page/schema | 不与 Expand 或数据 DELETE 同一不可回切步骤 |
| Warehouse rebuild | ModelSpec/dbt 有选择 build/test/materialize DWD/DWS/ADS | ODS/source/ModelSpec/dbt project 永久保留 |

## RED → GREEN

1. RED：未完成 backup restore、screen reference=0、cutoff freeze 中任一项。
2. GREEN dry-run：目标、保留计数、引用判定与 ROLLBACK 可审计。
3. GREEN apply：legacy target 清零；大屏及版本/权限/模板/审计/素材/menu bindings 完全一致。
4. GREEN route contract：旧入口不可再创建产物，canonical Analysis/Dashboard 旅程通过。
5. GREEN warehouse：仅在确需时重建目标关系，粒度键、行数、契约和下游运行通过。

## Definition of Done

- [ ] R1 与本 Contract 发布为两个独立可回切窗口。
- [ ] cutoff、备份文件 hash、恢复命令、dry-run/apply 输出已归档。
- [ ] screen durable set 升级前后逐项一致，无 Card 引用残留。
- [ ] legacy Card/Dashboard/Collection/Model/Trash/Pulse/Subscription/VDS/MBQL 入口和数据按批准范围清理。
- [ ] DWD/DWS/ADS 若重建，由数据建模 release 留证；未跨域删除治理事实源。
- [ ] 回滚演练证明可恢复完整数据库与 R1 镜像。
