# Sprint-89 非功能预算

本预算先以当前本地画像为下限；生产规模输入到达后只允许收紧实现或调整明确阈值，不得删除 fitness function。

| 属性 | 预算/不变量 | 可执行 fitness function | 状态 |
|---|---|---|---|
| 身份稳定 | 同一 `(source_id, namespace, object)` 连续同步、消失、重现时 dataset/table/column ID 不变 | 新增 `CatalogSyncStableIdentityIT`：sync v1 → missing → v2，断言三层 ID 相等且 routine path 无 DELETE | PLANNED |
| 幂等 | 同一快照执行 2 次不得新增重复 dataset/table/column 或重复 drift ticket | 同一 IT 比较前后 count 与唯一键；断言第二次 `added/removed/changed=0` | PLANNED |
| 失效安全 | `STALE/MISSING/BREAKING/UNKNOWN` 不得被解析为 CURRENT | `SourceReferenceResolverAdapterTest` + `WarehousePlanSourceInventoryContractTest` 参数化覆盖 | PLANNED |
| 兼容变化 | 新增 nullable/未引用字段不得阻断既有模型；破坏性变化必须阻断编译/发布/物化 | `MetadataModelingDriftPolicyTest` + `ModelSpecRepositoryIT` + gate tests | PLANNED |
| 查询边界 | 来源盘点必须分页，单页 `1..200`，禁止无界返回；单请求无 N+1 | Resource contract test 校验 size；集成测试统计 SQL 次数或 datasource proxy 阈值 | PLANNED |
| 延迟 | 本地 1,000 来源、100,000 字段基准下，来源盘点 P95 ≤ 500 ms；单表 2,000 字段 fingerprint P95 ≤ 100 ms | 固定夹具性能测试，预热后 20 次记录 P95；证据写入 IT，不在共享 CI 用单次 wall-clock 断言 | BLOCKED_PRODUCTION_CALIBRATION |
| 事务/并发 | 单次同步按 source+namespace 串行；重复触发不产生交叉 purge 或部分提交 | 并发集成测试双线程触发同一 source，断言唯一键、run status 与最终快照 | PLANNED |
| 超时/重试 | 本 Sprint 不增加远程调用；数据库操作沿用事务超时，不新增无限重试 | 静态契约测试反断言无新增 retry loop；运行日志检查 bounded attempt | PLANNED |
| 多租户 | WarehousePlan/ModelSpec 所有读写继续带 `tenant_id`；跨租户访问返回 404/403 | `WarehousePlanSourceInventoryResourceTest` 跨租户用例 | PLANNED |
| 权限 | 来源列表、diff、重确认均复用目录读权限与部门边界；无权限不泄露对象是否存在 | resolver/resource 测试覆盖 FORBIDDEN 与不存在的等价外部响应 | PLANNED |
| 审计 | 重确认、排除、显式 purge 均有 actor、tenant、binding/asset key、old/new version、correlation id | 审计注册表契约测试 + API 集成断言；例行读取不写审计 outbox | PLANNED |
| 兼容 | 旧 `locator_json.assetId` 继续可读；无不可逆 schema Contract；Chrome 95 可用 | 旧快照 fixture、Liquibase dry-run（若有迁移）、`pnpm build`、Chrome 95 IT | PLANNED |
| 可回滚 | 首轮优先无迁移；若发现必须迁移，只允许 Expand，旧读路径保留到验证后 | `git diff`/GitNexus change detection、migration dry-run、回滚 rehearsal | PLANNED |
| 可观测 | 同步与来源解析输出 runId/sourceType/result/reasonCode，不输出凭据或业务数据 | 日志契约测试；runbook 中提供查询和告警阈值 | PLANNED |

## 失败模式预算

| 失败模式 | 系统行为 | 稳定错误/状态 |
|---|---|---|
| 数据源暂时不可达 | 保留最后快照，不删除 ID；sync run 失败 | `PROVIDER_ERROR` |
| 表在完整快照中消失 | dataset harvest=STALE，table/column 保留 | `MISSING` |
| 来源重现 | 原 ID 重新激活并生成新 fingerprint | `AVAILABLE` + drift event |
| 权限被收回 | 不返回 schema/diff | `FORBIDDEN` |
| 兼容变化未重确认 | 已引用字段可运行，UI 显示提醒 | `COMPATIBLE_DRIFT` |
| 破坏性变化 | 编译、发布、物化 fail closed | `BREAKING_DRIFT` |
| 状态无法判定 | 禁止发布，可预览差异 | `REVIEW_REQUIRED` / `UNKNOWN` |

## 集中验证命令（实施完成后一次执行）

具体测试类可随 RED 阶段新增，但验证批次固定为：

1. `source/mvnw` 运行 dts-platform 本 Sprint 相关单元/集成测试集合。
2. `source/dts-platform-webapp` 运行来源盘点相关 Vitest/contract tests 与 `pnpm build`。
3. 运行一次授权账号 Playwright/Chrome 95 纵向旅程；失败后只重跑失败步骤。
4. 代码提交前运行 GitNexus change detection，核对仅影响预期 symbols/flows。
