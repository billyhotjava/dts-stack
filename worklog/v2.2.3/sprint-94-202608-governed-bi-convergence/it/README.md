# Sprint-94 集中集成与验收计划

**当前状态**：BLOCKED
**策略**：编码前关闭 G0；实现期间运行聚焦单元/契约测试；全部编码结束后只运行一次集中构建和一条三角色纵向 E2E，失败时仅做针对性重跑。证据未生成前不得写 PASS。

## 1. 验收环境与角色

| Actor | 责任 | 必需权限 |
|---|---|---|
| A1 数据集/分析维护者 | 选择已发布数据集，创建/编辑/保存分析草稿 | dataset read、analysis write；不得自行替代独立发布者 |
| A2 独立发布者/运营者 | 校验、发布分析和看板，配置受众 | 对目标资产 write；平台登记权限 |
| A3 授权消费者 | 查看/交互已发布看板 | read；导出旅程另具 export |
| A4 非授权消费者 | 负向验证 | 无目标资产 read/export |

必须记录真实账号标识（可脱敏）、部门/角色/密级、Chrome 版本、构建 commit、容器 image ID 和数据样本 ID。

## 2. 集成测试清单

| ID | 场景 | 前置 | 操作 | 断言与证据 | 责任 |
|---|---|---|---|---|---|
| IT-01 | Published projection | DS-PUBLISHED/STALE/DENIED | 分页/筛选/详情 | 仅 PUBLISHED；无 raw SQL；checksum/total 正确；越权过滤 | F1/T01 |
| IT-02 | 数据集归档/契约漂移 | 已保存分析钉定 v1 | 发布 v2、归档/变更策略 | v1 不静默漂移；新执行 409 或按策略保留；审计可查 | F1/T01 |
| IT-03 | 从数据集创建分析 | A1 + DS-PUBLISHED | `/bi/data` 点击创建 | URL 和 POST 含 id/version/checksum；生成一个 DRAFT；刷新不丢 | F1/T02、F2/T02 |
| IT-04 | AnalysisQuerySpec 生命周期 | 合法/非法/并发 fixture | create/update/copy/archive/version | 边界码正确；幂等；旧 Card 可读；新写无 MBQL/raw SQL | F2/T01 |
| IT-05 | 编辑器四态与校验 | A1 | 配置维度/指标/筛选/图表，预览、保存、校验 | 四态、键盘、409/422 定位、保存≠发布 | F2/T02 |
| IT-06 | 统一网关 | 三类入口相同 query | semantic/analysis/dashboard 执行（screen 属 S3） | queryId/audit 均来自 gateway；结果一致；无 direct adapter path；`ScreenWarmupService` 归属已裁定 | F3/T01 |
| IT-07 | 安全与预算 | A1/A3/A4、敏感样本、慢查询 | 未登录/越权/RLS/导出/并发/超时 | 401/403/422/429/504；缓存不跨 policy；无敏感日志 | F3/T02 |
| IT-08 | 分析/看板发布状态机 | A1 保存，A2 发布 | validate/publish/concurrent publish/rollback | 单一 PUBLISHED revision；依赖快照固定；失败不半发布 | F4/T01 |
| IT-09 | 受众登记与消费 | A2/A3/A4 | 发布看板，设置部门/角色/密级/有效期 | registration 幂等；A3 列表+直链可见，A4 均不可见；过期生效 | F4/T02 |
| IT-10 | **退役 S0 零变更** | 本 Sprint 全量 diff | 检查重定向/flag/删除/迁移 apply | 四项均为零；`bi/virtual-datasets*` 行为零回归 | F0/T02、F2/T02 |
| IT-11 | **旧资产分母与观测覆盖** | `assets/route-inventory.md` + 后端旧写 surface 清单 | 对账静态分母、source/retention/window/value/status | 每项有实测值或明确 UNKNOWN 原因与观测起点；未知不得记 0 | F0/T02、F0/T03 |
| IT-12 | 灰度与回滚 | `DTS_ANALYTICS_GOVERNED_BI_ENABLED` + rollback image | shadow→pilot→default→rollback | 新旧读兼容；容器/HTTP/DB/审计一致 | F6/T02 |

**顺延项**：大屏复用与兼容迁移 preview/apply/rollback 已移交 `../assets/deferred-scope-handoff.md`。本 Sprint 的 IT-12 只验证 governed BI 开关，`DTS_ANALYTICS_LEGACY_CARD_WRITE_ENABLED` 的关闭演练属 S1/S2。

## 3. 唯一浏览器纵向旅程

使用 Chrome 95：

1. A1 登录，从 `/bi/data` 过滤并打开 DS-PUBLISHED 契约，点击“创建分析”。
2. 断言落地 URL 为 canonical 入口 `/bi/questions/new?datasetId=&version=&checksum=`（ADR-94-13）；在编辑器配置项目、月份、进度指标和筛选；预览与已知数据人工核算一致；保存为 DRAFT。
3. A3 此时在列表与直链均不可消费该草稿。
4. A2 从 `/bi/questions/{id}/edit` 打开草稿执行校验；修复一个故意构造的非法字段 blocker 后发布 Analysis v1。
5. A2 新建/编辑 Dashboard，添加两个 PUBLISHED Analysis revision，配置参数和受众后校验发布。
6. A3 从 `/bi/dashboards` 打开、筛选、刷新和导出；A4 列表不可见、直链 403、导出 403。
7. 平台发布数据集 v2，确认已发布 Analysis/Dashboard 仍使用 v1 checksum；编辑新草稿时提示可升级而不自动漂移。
8. 触发慢查询/并发上限/Platform contract 不可用，确认 429/504/502 UI、correlationId、审计和恢复。
9. 回访冻结路由：`/bi/card/new`、`/bi/virtual-datasets/new` 仍可正常打开且行为与改造前一致（S0 零变更）。
10. 检查 console 无 error，Network 无未解释 4xx/5xx，无 SQL/敏感值泄露；键盘可完成主要操作。

大屏设计器复用已发布 Analysis 按 `../assets/deferred-scope-handoff.md` 顺延至退役 S3。

## 4. 构建与自动化顺序

```text
1. GitNexus fresh + per-symbol impact
2. Platform 聚焦单元/MockMvc/Testcontainers
3. Analytics 聚焦单元/MockMvc/Testcontainers
4. Webapp Node source-contract 与 Vitest 分 runner 执行
5. dts-platform-webapp pnpm build
6. 受影响 Java 模块构建
7. 退役 S0 零变更检查（IT-10）与旧资产分母对账（IT-11）
8. 一次 Chrome 95 三角色纵向 E2E
9. governed BI feature flag 灰度与 rollback 演练
10. gitnexus_detect_changes + diff/queue 状态复核
```

## 5. 证据目录约定

完成实施时在 `it/evidence/<YYYYMMDD-HHmm>/` 保存：

- `environment.md`：commit、image、浏览器、账号角色、样本 ID。
- `commands.md`：原样命令、exit code、测试数量；不含 secrets。
- `api/`：脱敏 request/response、correlationId、错误矩阵。
- `db/`：只含计数、ID、状态/checksum 对账，不导出业务敏感值。
- `browser/`：四页面关键截图、Network HAR 摘要、console 摘要、视频/trace（如启用）。
- `retirement/`：`route-inventory.md` 静态分母、后端旧写 surface、观测来源/窗口/status 和 S0 零变更 diff 证明。
- `release/`：feature flag、旧/新 image、健康检查与 rollback 时长。

禁止预建空 evidence 文件或以模板占位冒充验收。

## 6. 2026-08-17 实现与辅助验收记录

当前实现已完成聚焦编译、契约测试和现代浏览器 Mock API 辅助旅程，但这些结果不替代 Chrome 95、真实账号、真实数据库、审计与容器验收，因此 Sprint 状态仍为 `BLOCKED`，IT-08、IT-09、IT-12 不记 PASS。

| 证据 | 结果 | 边界 |
|---|---|---|
| Analytics `mvn -DskipTests compile` | PASS | 仅源码编译 |
| Analytics 聚焦测试 | PASS，24 tests | 覆盖发布状态机、统一查询网关、权限/预算、登记 outbox、feature flag 与观测指标 |
| Platform `ReportRegistrationServiceTest` | PASS，1 test | 覆盖受治理报表登记幂等服务，不代表真实跨服务调用 |
| Webapp source-contract | PASS，8 tests | 覆盖 Analysis/Dashboard 发布契约，不代表浏览器行为 |
| `pnpm build` | PASS | 含 TypeScript 与 legacy-browser production build；保留既有 Browserslist/chunk warning |
| Compose app/dev/legacy config | PASS | 仅静态配置解析，不代表容器已重建或运行健康 |
| Playwright 聚焦旅程，Chrome `150.0.7871.128` | PASS，1 test | Mock API；覆盖 Analysis 受众 blocker→发布只读、Dashboard 发布→登记 pending、登记失败重试、1366×768 与 768×900；console/page error/未解释 HTTP 4xx/5xx 为 0 |
| Chrome 95 | BLOCKED | 当前环境无 Chrome 95 executable，现代 Chrome 结果不可替代客户兼容门禁 |

Playwright 临时截图位于 `/tmp/dts-sprint94-playwright-results/sprint94-governed-bi-publi-0df5f-safely-retries-registration/`。旅程复核并修复了两个真实 UI 问题：已发布 Analysis 仍可写，以及 768px 下 Dashboard 标题与状态重叠。

尚需目标环境补齐：A1～A4 真实职责分离账号、隔离业务样本、迁移实际 apply/validate、真实 Platform 登记与消费者鉴权、审计/指标对账、Chrome 95、容器灰度和 rollback 演练及 48 小时观察窗。

## 7. 退出条件

- [ ] `baseline.md` P1～P10 的 GAP 均关闭或被正式裁剪出 Sprint。
- [ ] IT-01～IT-12 均有可重现证据；失败项有 owner 与阻断状态。
- [ ] 三角色纵向旅程一次通过，A4 负向结果与后端审计一致。
- [ ] Chrome 95、现代 Chrome（仅辅助）、API、数据库和容器结论分开记录。
- [ ] 退役 S0 零变更成立：无重定向、无 flag 关闭、无迁移 apply、无删除。
- [ ] 回滚后旧 published 资产仍可读，新增 Expand 数据未丢失。
- [ ] 不以 HTTP 200、容器 healthy、源码存在或 mock 单测代替真实用户验收。
