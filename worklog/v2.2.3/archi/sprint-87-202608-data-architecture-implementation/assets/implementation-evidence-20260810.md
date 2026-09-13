# Sprint-87 本地实施证据（2026-08-10）

**判定**：`CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E`

本记录只证明源码、聚焦测试、静态检查和 Chrome 95 目标构建；不代表已部署、已执行生产迁移、已完成真实账号菜单 E2E 或 `REAL/DELIVERED`。

## 1. Feature 状态

| Feature | 本轮状态 | 已完成 | 未完成/外部输入 |
|---|---|---|---|
| F0 | `BLOCKED_INPUT` | 本地实施路径与验收边界已登记 | 生产画像、真实账号、备份、审计读取权限 |
| F1 | `CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E` | 唯一 command boundary、方案 A 权限 guard、稳定 read adapter、菜单与路由 owner | 真实环境授权审计验收 |
| F2 | `CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E` | 稳定模型上下文、revision-pinned DAG、批量候选、逐项物化状态、二次物化、模型 Table | 真实物化链路 E2E、生产迁移 dry-run |
| F3 | `CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E` | CatalogAssetKey 语义投影、来源/状态拆分、统计投影、批量归域、SOURCE/DIM 可回滚迁移 | 生产容量画像、真实资产旅程、既有统计测试债务见第 4 节 |
| F4 | `CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E` | 稳定业务分类/数据域/过程 ID、指标类型、版本固定来源引用、可回滚上下文迁移 | 全量生产消费者观测与独立发布 |
| F5 | `CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E` | “数据架构”唯一入口、旧深链兼容、模型搜索/筛选/分页/多选、客户语言 | Chrome 95 真实菜单点击 E2E |
| F6 | `PARTIAL_LOCAL_VERIFY_PASS` | 集中非 E2E 测试、静态检查、生产构建、变更影响检查 | 部署、E2E、回滚演练、运行观测、Contract 判定 |

## 2. 集中验证结果

| 层面 | 命令/范围 | 结果 |
|---|---|---|
| 后端 | 隔离源码副本执行 Sprint-87 聚焦 Maven suite，包含 PostgreSQL Testcontainers 与 Liquibase | `348` tests，`0` failures，`0` errors，`BUILD SUCCESS` |
| 前端类型 | `pnpm exec tsc --noEmit` | PASS |
| 前端 Vitest | 数据架构、资产批量归域、模型目录、指标投影和菜单迁移 | `22/22` PASS |
| 前端 Node contract | 菜单 owner/兼容路由与指标 upsert contract | `18/18` PASS |
| 前端静态检查 | 对本次变更的 34 个前端文件执行 Biome | `0` errors；保留 `4` 条 CSS specificity warnings |
| Chrome 95 目标构建 | `pnpm build`（`LEGACY_BROWSER_BUILD=1`） | PASS；10,584 modules transformed，约 2m29s |
| GitNexus | `gitnexus_detect_changes(scope=all)` | LOW；477 个已索引变更符号，0 个受影响流程 |

后端聚焦 suite 覆盖架构字典权限/依赖规则、模型目录与上下文迁移、候选/物化/二次物化、资产语义/统计/迁移、指标上下文/生命周期以及 Resource 契约。三组可回滚迁移均通过真实 PostgreSQL + Liquibase 集成测试。

## 3. 影响与边界

- GitNexus 对本轮收口检查的 `ModelWorkbenchCatalogList`、`PlanningPage`、`ModelingWorkbenchPage`、`DomainScopeNav` 均判定 `LOW`。
- `Sprint64GovernanceService.listProcesses` 的预编辑影响为 `MEDIUM`，已通过 Resource、业务过程 application service 和指标上下文测试覆盖。
- 新增未索引文件和已知前端索引假阴性不能由 `affected_processes=0` 单独证明无影响，因此仍以编译、聚焦测试和构建为主证据。
- 本轮没有提交、推送、部署、运行生产 Liquibase，也没有修改生产数据。

## 4. 已知非本轮阻断项

`CatalogAssetPortalStatsTest` 和 `CatalogAssetPortalTagFilterTest` 在本轮集中 suite 之前已有基线失败：前者为既有分页 fixture 数量预期偏差，后者为 Mockito 实例参数的 strict-stubbing 假阴性。本轮对这两个测试只同步了构造器新增依赖，`CatalogAssetPortalService` 的列表/统计实现没有随 F3 修改；因此未通过改期望值掩盖问题，也未把两项计入 348 项通过证据。后续应作为独立测试债务复核真实分页语义。

## 5. 明确未执行

- 真实账号从一级菜单逐项点击的 E2E；
- Chrome 95 客户环境的窄屏、四态、批量和二次物化旅程；
- 目标环境部署、健康检查、审计查询和告警验证；
- 生产迁移 preview/apply/rollback 与备份恢复演练；
- 14 天兼容观测窗口及独立 Contract GO/NO-GO。

在上述证据补齐前，Sprint-87 不得标记 `REAL/DELIVERED`，旧 API、字段和路由不得删除。
