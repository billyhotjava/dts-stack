# 非功能预算（Gate G1）

**依据**：`domain-profile.md` 本地画像、DTS Chrome 95/权限/审计不变量，以及本 Sprint 契约链。
**说明**：阈值必须由自动化适应度函数证明；客户规模尚未取得的项目标记为“待校准”，不得以本地空库判定 PASS。
**2026-08-17 修订**：大屏依赖、迁移批次、迁移吞吐移交后续阶段；新增退役 S0 的观测覆盖适应度函数；分页默认值按前端既有 table 约定校正（UI 10 条，非 API 的 20）。状态取值统一为 `PASS|GAP|BLOCKED|PENDING|N/A|待校准`；严重度写入说明，不另造状态词。

| 维度 | 预算 | 可执行适应度函数 | 责任 Task | 当前状态 |
|---|---|---|---|---|
| 数据集列表 | API：0-based，服务端默认 20、最大 100，只返回 PUBLISHED。**UI：默认每页 10 条**，切换条数重新拉取并重置第 1 页（沿用既有 table 约定） | 150 条 mixed-status IT，逐页断言 total/状态/无重复；前端分页 contract test 断言默认值 10 | F1/T01、F1/T02 | GAP |
| 数据集契约 | 获取 P95 ≤500ms（本地基线）；checksum 稳定；底层 SQL不出公开 DTO | MockMvc schema test + snapshot + 500 次基准 | F1/T01 | 待校准 |
| 契约缓存 | key=`datasetId:version:checksum`；TTL ≤5m；发布/归档主动失效 | fake clock IT，断言 stale 永不被 silently served | F1/T01、F3/T01 | GAP |
| Analysis 规范 | 派生指标 ≤20；filter ≤50；orderBy ≤10；limit 默认 5000、最大 10000 | 参数化 validation test，边界+1 返回 400/422 | F2/T01 | GAP |
| 保存幂等 | 同一 idempotency key 只产生一个实体/revision | 并发 IT（20 请求）+ 唯一性断言 | F2/T01 | GAP |
| 查询行数 | 交互查询最大 10,000；超出 `truncated=true`；导出走独立 export 权限/预算 | gateway IT + 10,001 行 fixture | F3/T01 | GAP |
| 查询时延 | 列表/预览 P95 ≤2s；交互查询 P95 ≤5s；超过 30s 返回 504 | Testcontainers 延迟注入 + Playwright performance | F3/T01、F6/T01 | 待校准 |
| 并发 | 每用户 3、每部门 20、全局 100；超限 429 且带 retryAfter | 受控并发 IT 断言 permit 释放和公平性 | F3/T02 | GAP |
| 缓存 | 仅相同 actor-policy-context + checksum + query hash 命中；含敏感字段默认不跨用户缓存 | 权限矩阵 IT，断言无跨受众结果泄漏 | F3/T01、F3/T02 | GAP |
| SQL 安全 | 只读；单 statement；禁止 DDL/DML/注释逃逸；参数化；不得绕过 `QueryExecutionFacade` | 现有安全回归 + 静态架构测试禁止 direct `runNative`；现状仅部分具备 | F3/T01 | GAP |
| 认证 | 除显式 public endpoint 外无认证 401；无授权 403；不得 fallback anonymous（P0 发布硬门禁） | MockMvc 全 API prefix 负向测试 | F3/T02 | GAP |
| RLS/脱敏 | 所有查询含 platform policy context；日志/缓存不得出现未脱敏值 | policy fixture + SQL/result/audit 断言 | F3/T02 | GAP |
| 审计 | 保存、校验、发布、查询、导出、共享均含 actor/correlationId/assetKey/revisionId/queryId/outcome | 集成测试查询审计分类，不允许“未分类” | F2～F4、F6 | GAP |
| Revision | `(model,model_id,version_no)` 唯一；每资产最多一个 PUBLISHED revision | 并发 publish IT + DB 约束 | F4/T01 | GAP |
| 发布原子性 | revision、实体指针、受众登记失败时不出现半发布；跨服务使用 outbox/补偿 | 故障点参数化 IT + reconcile | F4/T01、F4/T02 | GAP |
| 受众可见性 | 列表和直链一致；过期后 ≤60s 不可见；导出另验 export | fake clock + 三角色 API/E2E | F4/T02 | GAP |
| 看板依赖 | 卡片 ≤50；参数 ≤20；校验一次批量加载依赖，额外查询 ≤4 | 查询计数 IT + 51/21 边界 | F4/T01 | GAP |
| **退役 S0 分母与观测覆盖** | 32 条静态路由、动态菜单和后端旧写 surface 完整；每项有 `source/window/value/status`，UNKNOWN 有原因且不得记 0 | 静态 inventory 对账 + observation coverage schema 检查（IT-11） | F0/T02、F0/T03 | GAP |
| **退役 S0 零变更** | 本 Sprint 新增重定向=0、flag 由开改关=0、迁移 apply=0、删除=0 | diff 静态检查 + 冻结路由回归（IT-10） | F0/T02、F2/T02 | GAP |
| **冻结路由回归** | `bi/card/*`、`bi/virtual-datasets/*` 共 4 条在组件拆分前后行为一致 | 拆分前置 RED 回归测试 | F2/T02 | GAP |
| 兼容期 | 旧读旧写本 Sprint **全部保持开启**；关闭属退役 S1/S2 | release gate 断言 legacy write flag 仍为 on | F6/T02 | PENDING |
| 前端首屏 | 每个列表首屏 API ≤3；禁止 N+1；本地 P95 ≤2s | Playwright Network 计数和 performance entries | F1/T02、F2/T02、F4/T02 | 待校准 |
| 浏览器 | Chrome 95；不新增第三方依赖；loading/empty/error/success 四态 | legacy build + Chrome95 focused E2E + package diff | F1/T02～F6/T01 | GAP |
| 可访问性 | 键盘完成创建/校验/发布；dialog 焦点回收；表单错误与字段关联 | Playwright keyboard journey + axe 如现有工具可用 | F2/T02、F4/T02 | GAP |
| 可用性 | Platform contract 暂时不可用：新查询 502/503 且可诊断；已发布页面元数据可降级读，禁止绕过治理 | platform fault injection + UI error state | F3/T01、F6/T01 | GAP |
| 可回滚 | `DTS_ANALYTICS_GOVERNED_BI_ENABLED` 可在一轮发布内切回（`LEGACY_CARD_WRITE` 本 Sprint 只观测不切换） | 部署演练 + API/UI smoke + 数据指针对账 | F6/T02 | PENDING |
| 租户 | N/A：当前产品未形成多租户模型；保留既有兼容字段，不新增 tenant selector | source-contract test | 全部 | N/A |

## 风险预算与处置

| GAP | 影响 | G1 处置 |
|---|---|---|
| 本地 BI 数据近乎为空 | 时延和并发不可判定 | F0/T02 只建当前能力基线；F0/T03 取得目标分布，超出时重新审批预算 |
| 旧路由调用量 UNKNOWN | 退役阶段无法推进 | S0 登记来源与观测起点；连续 14 天可读前不得进入 S1，且不得把 UNKNOWN 当 0 |
| 安全配置仍 permitAll | 任何功能验收都不能代表授权正确 | F3/T02 是发布硬门禁，未关闭不得进入 pilot |
| Platform/Analytics 跨服务发布 | 可能半发布或受众登记漂移 | 使用幂等 registration + outbox/reconcile，不做分布式事务假设 |
| Chrome 95 缺失 | UI 兼容性未知 | 所有用户可见 Feature 保持 DRAFT/BLOCKED，F6 集中验证一次 |
| 超大 owner 文件 | 变更易产生回归 | 先抽取 seam；禁止 `SemanticCardEditorPage.tsx`（1137）、`analyticsApi.ts`（2483）继续增长；`ScreenResource.java`（3117）本 Sprint 不动 |
| 共享编辑器组件误伤 VDS | `virtual-datasets` 行为静默变化 | 拆分前置回归测试；VDS 处置属 S1（开放问题 Q5） |

DoD 时逐行回填实测值、命令和证据路径；“代码看起来支持”不等于适应度函数通过。
