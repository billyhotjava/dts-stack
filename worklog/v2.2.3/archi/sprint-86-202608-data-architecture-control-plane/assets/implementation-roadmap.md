# F5 兼容迁移、发布安全与下一实施 Sprint 路线图

**状态**：APPROVED

**形成日期**：2026-08-09

**评审入口**：IT-06

**评审人**：xiezm（已登记，兼任产品决策、架构/交付 owner）

## 1. ADR-86-10 已批准结论

所有架构字典、模型关系、资产语义、指标上下文和页面路由改造均采用：

```text
Expand → 双写/兼容读 → dry-run/分批回填 → consumer 切换
  → 观测/回滚演练 → Contract（另批审批）
```

Sprint-87 只允许 Expand、迁移、切换和观测；删列、删表、删除兼容路由不与首轮切换同批上线。Contract 阶段必须单独审批。

## 2. 实施前 G0 门禁

| 门禁 | 当前状态 | 进入 READY 的证据 |
|---|---|---|
| 客户/生产画像 | BLOCKED_INPUT | 域、资产、SOURCE/DIM、指标、计划、候选、典型批量、增长率和脏数据比例归档 |
| GitNexus 当前索引 | BLOCKED_TOOLING | 重新 analyze 成功；对每个拟改 symbol upstream impact；前端另用 scoped `rg` 交叉验证 |
| 登录/菜单/E2E | BLOCKED_INPUT | 明确账号、七/新入口菜单点击可达、失败关闭且证据脱敏 |
| 迁移样本/备份 | BLOCKED_INPUT | dry-run 样本、备份位置、批次回滚和数据漂移校验 |
| Chrome 95 | PENDING | 目标环境 smoke 基线、console/network 采集方式 |
| Sprint-86 ADR/IT | PASS | IT-01～07 已形成明确架构结论，异议关闭或具名转移 |

任一受影响切片在相应 G0 未通过前保持 `BLOCKED`，不通过“先编码再看数据”发现契约。

## 3. 实施波次

| 波次 | 竖切片 | UI → API → Service → Data | 回滚锚点 |
|---:|---|---|---|
| 0 | 基线与探查 | 只读探查/菜单点击 → 当前 API → 当前服务 → 画像与备份 | 当前 HEAD、镜像 digest、数据库备份、dry-run 报告 |
| 1 | 架构字典 command/read boundary | 旧/新维护入口 → 既有 `/api/catalog/domains*` 等兼容 API → 唯一 application command service + 方案 A guard → 现有表 | 关闭新导航；旧 URL 继续委托同一 service；不回滚数据 |
| 2 | 模型关系、DAG 与批量候选 | 模型 Table/预检 → 扩展既有 release-candidate APIs → candidate/application/materialization services → revision/dependency/attempt/observation 扩展 | 关闭批量 UI；旧单模型路径继续；新表/列保留不读 |
| 3 | 资产来源、状态与统计 | 资产台账/状态 → assets-v2/domain stats → asset command/projection services → Producer/Evidence/五轴/统计投影 | 切回旧读；保留新 ledger；停止增量 consumer 后可重建 |
| 4 | 指标业务上下文 | 指标 Table/编辑器 → 兼容 `/api/governance/indicators*` → 现有 indicator service/version/publish → 新 ID/sourceRefs + migration issues | 逐 consumer 切回旧字段；回填批次可逆；旧列不删 |
| 5 | IA 与路由收敛 | 菜单点击/兼容深链 → 原 API → 原 service → 无新增业务表 | 关闭新菜单；全部旧 URL redirect/adapter 仍可用 |
| 6 | 集中验证与观测 | 真实菜单旅程 → API/审计 → 服务健康 → schema/数据/统计 | 按切片关闭开关；恢复前一镜像；执行批次回滚 |

原则：波次 1 必须先于新菜单；波次 2/3 的新证据链就绪后才能在模型 Table 展示“已物化”；波次 3 统计投影就绪后才能扩大资产纳管；波次 4 因 `GovIndicatorDefinition` HIGH 风险独立上线。

## 4. 兼容变更清单

### 4.1 架构字典

- 保留现有表和 API；Controller/旧 adapter 改为委托唯一 command service。
- 新 consumer read port 返回稳定 ID、code、name、parent、status、version；消费者禁止 Repository 写入。
- 数据集市/主题域 `tenant_id` 保留并由服务端写平台 scope；API/UI 不接受 tenant 输入。
- 方案 A actor allowlist、对象 guard、成功/拒绝审计同时落地；UI 隐藏按钮不作为验收。

### 4.2 ModelSpec 与候选

- Expand：head/revision/import/export/candidate snapshot 增加稳定过程/主题域/依赖引用。
- 迁移：旧 `business_activity_ref` 与业务矩阵字符串 ID dry-run，不能唯一解析的记录进入 issue ledger。
- 扩展既有 release-candidate create/rematerialize/materializations/workspace 契约，不新增平行候选表或 `/bulk-materialize` 控制面。
- 依赖/attempt 数据追加；旧 observation 不改写。

### 4.3 资产与统计

- Expand：ProducerRef、RegistrationEvidence、五轴状态及统计 projection；全部使用既有 CatalogAssetKey。
- SOURCE/DIM dry-run 后按批次回填，原 warehouseLayer 保留为兼容证据。
- 先建立增量统计并完成一次全量 reconciliation，再打开“所有稳定真实关系”纳管。
- fallback 扫描触顶必须返回 approximate/`≥5000`，不得假装精确。

### 4.4 指标

- Expand 新业务上下文与 sourceRefs；旧 `category/domain/datasetId/isDerived` 保留。
- mapper、版本、模板、派生、发布、dbt 和查询逐项双读/双写；每一 consumer 有独立回归。
- HIGH 风险迁移分批打开；回填 issue 未关闭的指标可查看但不得发布新版本。

### 4.5 路由与菜单

- 菜单事实源只改 `dts-admin` seed/changelog；前端不硬编码新菜单。
- 先部署 `/data-architecture` 目标解析和旧路由 adapter，再切菜单。
- `/governance/subjects` 的 5 处活引用和 3 条 E2E 全部迁移/兼容后，进入 14 天且至少一个完整发布周期的观测。
- Contract 前旧 URL 必须保持参数无损跳转；帮助中心、repairRoute、工作台入口同步更新。

## 5. Dry-run、批次与回滚

每个数据迁移必须提供三个命令/模式：`preview`、`apply --batch-id`、`rollback --batch-id`，并生成：

- 基线总数、可自动迁移数、歧义数、缺失数；
- 每条 before/after、reasonCode、checksum；
- 执行人、开始/结束、批次、correlationId；
- apply 前后的漂移校验；
- rollback 可恢复的原值和不可逆风险（首轮不得包含不可逆删除）。

自动回填只允许唯一匹配；“取第一条”“按中文模糊猜测”“按表名前缀推断业务事实”均禁止。

## 6. Contract 门禁

旧字段/路由只有同时满足以下条件才可另批进入 Contract：

1. 至少连续 14 天且跨一个完整发布周期无 legacy-only 写入；
2. 所有新读路径无 fallback，或豁免项具名批准；
3. migration issue 为 0，或每条有 owner/理由/处置日期；
4. 新旧计数、revision、asset key、indicator version 对账无漂移；
5. 旧 URL 命中量为 0，或所有外部消费者已确认迁移；
6. 回滚演练、备份恢复、Chrome 95、真实菜单 E2E 和审计查询通过；
7. `gitnexus_detect_changes()` 与单一 primary review 证明无平行 owner。

Contract 操作另立 Task/变更单；不得在 Sprint-87 首轮提交顺手删除。

## 7. 发布与观测信号

| 信号 | 阈值/判定 | 处置 |
|---|---|---|
| migration unresolved ratio | >0 阻断对应 consumer 切换 | 停止批次、修复映射或具名豁免 |
| asset stats projection lag | >5m 警告；>10m STALE | 降级计数、停止扩大纳管、触发 reconciliation |
| candidate active conflict | 任一非预期并发冲突 | 停止新候选，检查幂等/锁 |
| materialization no heartbeat | 10m | 标 STALE，禁止无限 loading；允许取消/诊断 |
| legacy-only writes | 任一 | 取消 Contract 倒计时并定位入口 |
| unauthorized architecture writes | 任一成功 | 安全事件；关闭新写入口并回滚 |
| route compatibility error | 参数丢失或循环跳转任一 | 关闭新菜单，恢复旧入口 |

审计动作须在 `dts-admin` 审计资源字典登记；失败/拒绝和成功都能按 actor、对象、批次、correlationId 查询。

## 8. 下一实施 Sprint 切片

下一 Sprint 命名为 **Sprint-87：平台数据架构与跨域契约实施**，目录
`worklog/v2.2.3/sprint-87-202608-data-architecture-implementation/`。建议 Feature：

| Feature | 目标 | 初始状态 |
|---|---|---|
| F0 | 交付基线、客户画像、索引与备份 | BLOCKED_INPUT |
| F1 | 架构字典 command/read boundary 与权限审计 | BLOCKED（依赖 F0） |
| F2 | 模型关系、DAG、批量候选和二次物化 | BLOCKED（依赖 F0/F1） |
| F3 | 资产来源/状态、DIM/SOURCE 与统计投影 | BLOCKED（依赖 F0/F1） |
| F4 | 指标业务上下文兼容迁移 | BLOCKED（依赖 F0/F1；HIGH risk） |
| F5 | 数据架构入口、模型 Table 与兼容路由 | BLOCKED（依赖 F1～F4） |
| F6 | 集中验证、部署、观测与 Contract 准入 | BLOCKED（依赖 F1～F5） |

## 9. IT-06 通过清单

- [x] 接受 ADR-86-10 的迁移顺序和 Contract 单独审批原则。
- [x] 接受六个实施波次及 HIGH 风险指标独立上线。
- [x] 接受 14 天 + 一个发布周期的兼容观测门禁。
- [x] 接受客户画像、GitNexus、登录、备份、Chrome 95 作为 Sprint-87 G0 blockers。
- [x] 接受 Sprint-87 在 G0 未齐前保持 BLOCKED，不以 Sprint-86 DONE 冒充可编码。
- [x] 确认 Sprint-86 只可标 Architecture DONE，不是功能、部署或真实 E2E 交付。
