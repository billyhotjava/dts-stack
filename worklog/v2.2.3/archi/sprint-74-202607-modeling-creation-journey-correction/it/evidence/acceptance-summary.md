# Sprint-74 验收证据汇总

**验收日期**：2026-07-27  
**环境**：v2.2.3 本机 compose，真实 Spring Security、PostgreSQL 17、dbt 1.11.3、Chromium 95.0.4638.0  
**结论**：PASS

## 1. Journey 结果

| ID | 结果 | 证据 |
|---|---|---|
| IT-01 | PASS | Chrome95 打开创建抽屉时无默认模型类型，未完成最小身份时“保存草稿”禁用；`chrome95/it-01-02-create-purpose-cards-chromium95.png` |
| IT-02 | PASS | 四类卡均显示“适合/不适合/例子”；服务端经典映射为 DIMENSION/FACT→DWD、SUMMARY→DWS、APPLICATION→ADS |
| IT-03 | PASS | DIMENSION `a737847a-355a-429f-809c-c3bb4644c75e` 在无实现依赖下达到 DESIGNED |
| IT-04 | PASS | FACT `be1494e3-6aca-414b-b891-1d298caaecf8` 的粒度、KEY、TIME 与时间语义闭合并达到 DESIGNED |
| IT-05 | PASS | 后端固定返回四级 gate；前端只投影当前 gate，未来发布要求不计入当前任务；31 个 stage-gate 测试通过 |
| IT-06 | PASS | 三个 `DESIGNER_GENERATED` 实现把目标名、装载、分区与保留写入 `ModelImplementation.settings` |
| IT-07 | PASS | APPLICATION `42fe5aa7-7d89-46fd-88b9-4baae9b314a4` 使用 `DBT_MANAGED`，真实 dbt compile 和 lifecycle import 均通过；`chrome95/it-06-07-data-implementation-chromium95.png` |
| IT-08 | PASS | 当前样本有真实 COMPILE/PASSED，但尚未通过发布治理；“发布结果”正确显示编译时间线和“无真实物理资产”空态，未伪造 table/view，且没有 dbt 编辑入口；`chrome95/it-08-release-result-chromium95.png` |
| IT-09 | PASS | 为避免改写用户模型，使用隔离 FACT 样本 `2a711ffc-5d6b-4247-bc89-87a0714181f3` 验证 preview、显式确认、追加 DIMENSION r3、历史 FACT r2 可读、重放幂等、stale ETag=409 |
| IT-10 | PASS | 验收计划使用 `KEY_AND_MEASURE/BLOCKING`；治理缺口只进入 RELEASE_READY，DESIGNED 与 IMPLEMENTATION_READY 不被污染 |
| IT-11 | PASS | 兼容 dry-run 返回 total=10、eligible=1、conflict=2、skipped=7；未批量修改用户数据；无 apply 记录的 rollback 保留兼容读取且删除数为 0 |
| IT-12 | PASS | Chrome95 三条真实旅程 3/3 通过，无 console/page/request failure；390px 窄屏无水平溢出；`chrome95/it-12-release-result-chromium95-narrow.png` |

## 2. 集中验证

| 验证 | 结果 |
|---|---|
| 后端 focused 单元/资源契约 | 202 tests，0 failure/error |
| PostgreSQL Testcontainers | 18 tests，0 failure/error；包含 clean schema Liquibase、ModelSpec repository、implementation settings 与计划策略 |
| 前端建模 source-contract | 143 tests，0 failure |
| TypeScript | `pnpm exec tsc --noEmit` PASS |
| production build | `pnpm build` PASS；仅保留既有 browserslist/chunk-size warning |
| Chrome95 real E2E | 3 tests，0 failure |
| dbt | `dbt compile --select ads_s74_finance_dashboard` PASS；2 个 lifecycle artifacts |
| API P95 | 模型详情 16.562ms；stage-gates 24.094ms；各 100 次、HTTP 200=100% |

## 3. 安全说明

- 未改写用户“财务项目模型”；纠错使用隔离模型证明同一生产代码路径。
- 未对 compatibility dry-run 的 eligible 用户对象执行批量 apply。
- 发布治理仍真实阻断样本发布，所以验收以“编译证据可见、物理资产不伪造”作为本 Sprint 的正确结果；发布审批和物理登记继续归 Sprint-69 控制面。
- Cookie、token、密码与数据库凭据均未写入证据文件。
