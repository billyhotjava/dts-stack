# dbt 可视化往返建模运维手册（Gate G4）

**负责人**：数据平台运维 + 数据建模维护者  
**风险等级**：高  
**当前状态**：DESIGN_READY；指标/告警和故障演练完成后转 PASS

## 1. 正常状态

- artifact-rich inspect 无外联、无 SQL/dbt 执行，单包受 500 节点/10000 边/深度 128 和 SQL/宏容量限制。
- apply/retry 的汇总满足 `selected=created+updated+skipped+failed+blocked`，重放不新增修订。
- runtime 未认证时发布/物化稳定返回 `DBT_RUNTIME_NOT_CERTIFIED`；认证后一个 candidate/attempt 对应一个确定 DagRun。
- `latestPublishedRef` 可领先 `servingRef`；物化失败/stale 时旧 serving 保持可消费。
- physical preview 仅显式加载，默认 100/最大 500，`private,no-store`，审计/日志不含样例值。

## 2. 健康检查

| 检查项 | 命令/端点 | 期望 |
|---|---|---|
| platform | 容器 health + `/management/health` | healthy / `UP` |
| Airflow control-plane | `/health`、`/api/v1/health` | HTTP 200 |
| runtime certification | 认证 profile 查询/诊断 | digest、Core、adapter、PostgreSQL 版本精确匹配 |
| import run | import run 查询 API | terminal 状态与逐项恒等式一致，无永久 `RUNNING` |
| Catalog serving | 稳定 asset key 的双指针查询 | serving 只指向成功且 evidence 当前的 candidate |
| 审计 | 以 correlationId 查询 platform outbox 与 dts-admin | 动作已分类、两端可关联、正文脱敏 |

## 3. 告警规则

| 告警 | 条件与阈值 | 级别 | 含义与首个动作 |
|---|---|---|---|
| ImportStuck | run/attempt `RUNNING` >10 分钟 | P1 | 可能 worker/事务卡住；先按 correlationId 查逐项与锁，不重复整包 apply |
| ImportFailureBurst | 15 分钟内 ≥5 个 run 且 FAILED/PARTIAL >20% | P2 | 输入兼容或服务异常；按 errorCode/stage 聚合，暂停受影响 fixture/profile |
| RuntimeUncertifiedDispatch | 任一未认证 dispatch 尝试或 digest 漂移 | P1 | 认证门禁/配置异常；立即关闭物化并确认 Airflow submit=0 |
| ServingCasConflict | 同 asset 5 分钟内 CAS 冲突 ≥3 | P1 | 乱序/重复 callback；冻结该模型切换，保留旧 serving |
| PhysicalPreviewSecurityBlock | identifier/evidence mismatch 在 5 分钟内 ≥3 或任一越租户 | P1 | 潜在篡改；阻断预览并核对 actor/tenant/correlationId，禁止回显 relation |
| PreviewTimeoutRate | 5 分钟内 ≥20 请求且 timeout >5% | P2 | 数据源慢或查询失控；关闭样例入口，保留结构视图 |
| AuditDeliveryLag | outbox 最老未投递 >5 分钟或未分类动作 >0 | P1 | 合规留痕不完整；暂停写操作开闸，先恢复 admin ingest/动作字典 |

当前环境缺少统一 Alertmanager 是已登记平台缺口；实现 Task 必须至少产出可抓取指标、结构化日志和可执行查询，部署接入告警系统后才可将 G4 标 PASS。

## 4. 关键日志与审计字段

`correlationId`、tenant、actor、planId、modelSpecId/modelRevision、implementationRevision/checksum、import runId/attemptId、candidateId/candidateVersion、materialization attempt、pipelineRunId、observationAttempt、relationEvidenceId/evidenceChecksum、assetType/assetKey、stage、result、errorCode、retryable、requested/returned count。

禁止记录：SQL/Jinja/compiled SQL、ZIP/文件正文、token/密码/连接串、样例行、未授权列名、原始密级策略正文。客户端 IP 统一由 `IpAddressUtils.resolveClientIp` 解析；审计动作必须先登记 dts-admin 字典。

## 5. 故障处置

| 场景 | 症状 | 立即动作 | 恢复 | 可回滚 |
|---|---|---|---|---:|
| ZIP 异常/恶意输入 | inspect 稳定安全错误码 | 不解压到宿主机、不放宽上限；核对临时目录清理 | 修复包后重新 inspect | 是，无业务写入 |
| import 部分失败 | PARTIAL/FAILED 行存在 | 保留成功项，按行查看 recoveryAction | 仅 retryable 失败项重试；不可重试项重新上传/映射 | 前向修订 |
| Airflow/dbt 不可用 | dispatch/reconcile 超时或 UNKNOWN | 停止新 dispatch，保持旧 serving | 恢复 control-plane 后先 reconcile，再决定重试 | 是 |
| runtime 漂移 | digest/version 与 profile 不同 | 切 `NOT_CERTIFIED`，确认 submit=0 | 新 candidateProfile 重新跑完整 RT-01/F0-T05 | 是 |
| callback 乱序/stale | FAILED_STALE/CAS 冲突 | 不写 observation、不切 serving | 创建新 attempt；旧 callback 幂等忽略 | 是 |
| 预览分类/脱敏不可用 | 423、0 行 | 关闭样例读取，保留安全结构 | 恢复策略服务后由用户重新显式加载 | 是 |
| Catalog 同步失败 | SYNC_PENDING/FAILED | 不重跑 dbt、不回退发布事实 | 重放 outbox 投影 | 是 |

## 6. 容量与留存

- apply 每次最多 200 uniqueId；preview limit 1..500；不提供 offset/cursor/导出。
- ZIP/SQL/宏/图预算以 `assets/nfr-budget.md` 为准，禁止线上临时放宽。
- import attempts、candidate、relation evidence 和审计为治理证据，只增不原地覆盖；按平台留存策略归档，删除须独立审批。
- 临时 ZIP/解析目录在成功、失败、取消、超时后均须为空；不可写/非受控目录启动 fail-closed。

## 7. 禁止操作

- 不直接修改 candidate/serving 指针、数据库状态或审计行“修好”流程。
- 不对 UNKNOWN/超时自动无限重试，不重复触发非幂等 build。
- 不从 manifest/客户端 relation 名拼 SQL，不使用旧 dbt preview 绕过 evidence/密级/脱敏。
- 不把客户 ZIP、凭据、target 产物或样例行复制到工单/日志/Git。
- 不在同一发布中既 expand 又删除旧 schema/API。
