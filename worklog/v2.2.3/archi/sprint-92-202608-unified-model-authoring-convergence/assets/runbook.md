# 运维手册（Gate G4）

**功能**：统一模型创作草稿  
**负责人**：平台数据建模维护组（评审人 xiezm）  
**风险等级**：高  
**状态**：PENDING_DEPLOYMENT_VERIFICATION

## 1. 正常状态

用户在同一模型工作台创建/续编 authoring draft，跨 visual/code 保存并校验；commit 原子产生 model/implementation/dependency pins。正常时：

- 同一模型最多存在符合既有 actor/幂等规则的活动草稿；
- 无超过 5 分钟的 `COMMITTING`；
- commit 成功后 model 与 implementation pins 可被候选读取；
- 旧 API 兼容调用不会改变 visual editability；
- 日志和审计不包含 SQL/YAML 正文。

## 2. 健康检查

| 检查项 | 命令/端点 | 期望结果 |
|---|---|---|
| 平台进程 | `/management/health` | `UP` |
| authoring context | 受保护 `GET .../{id}/authoring-context` | 200；pins 与当前模型一致 |
| 活动草稿 | 查询 draft 状态聚合 | 无长期 `COMMITTING`，过期非 COMMITTED 可清理 |
| receipt 一致性 | 按 draftId 对账 implementation/revision/checksum | COMMITTED 全部有 implementation receipt |
| candidate 兼容 | 由 UI 创建候选并查看 pins | 与 authoring commit 回执一致 |

## 3. 告警

| 告警 | 条件与阈值 | 级别 | 含义 | 首个处置 |
|---|---|---|---|---|
| AuthoringCommit5xxHigh | 5 分钟内 5xx ≥5 或比例 ≥2% | P1 | 用户无法提交模型或存在事务故障 | 停止前端放量，查 correlationId，见场景 F1 |
| AuthoringCommitStuck | `COMMITTING` 超过 5 分钟的草稿数 >0 | P1 | commit 可能卡住/回执不完整 | 禁止手工改状态，按 draftId 对账，见 F2 |
| AuthoringConflictSpike | 10 分钟内 409/412 比例 ≥20% 且请求数 ≥20 | P2 | ETag/pins 刷新或客户端重放异常 | 查 modelSpecId/draftId/actor 分布，见 F3 |
| ProjectionFailureSpike | 10 分钟内新增 `NONE` ≥10 且较前一窗口翻倍 | P2 | parser/adapter 回归或新复杂 bundle | 降级 raw node，禁止自动改写，见 F4 |
| UnclassifiedAudit | 任一 authoring 写审计落“未分类” | P1 | 合规字典遗漏 | 禁止发布，补审计资源字典 |

当前平台缺少完整 Alertmanager 时，以上阈值先作为日志/SQL 巡检规则执行，闭合后接入统一告警面。

## 4. 关键日志与审计字段

| 字段 | 用途 |
|---|---|
| `correlationId` | 串联 HTTP、service、dbt 和审计 |
| `modelSpecId`、`draftId`、`planId` | 定位模型与草稿 |
| `actorId` | 定位操作者；日志按现有脱敏规则 |
| `baseModelRevision/checksum` | 判断陈旧写入 |
| `baseImplementationRevision/checksum` | 判断实现漂移 |
| `draftEtag`、`validatedChecksum`、`dependencyChecksum` | 判断 CAS 与验证证据 |
| `provenance`、`projectionCoverage` | 判断来源和降级路径 |
| `idempotencyKey`/payload hash | 判断重放冲突 |

**禁止记录**：SQL/YAML/ZIP 正文、token、密码、密钥、multipart 临时路径、密级字段值。

## 5. 故障处置

| 场景 | 症状 | 立即动作 | 恢复动作 | 可回滚 |
|---|---|---|---|---|
| F1 PostgreSQL/事务失败 | commit 5xx，可能无回执 | 用 correlationId 查询事务日志和 draft；禁止前端伪造成功 | 确认无半提交后以同 idempotencyKey 重试；若有不一致走前向修复 | 是，代码可回滚；历史 revision 不删 |
| F2 草稿卡在 COMMITTING | 超过 5 分钟 | 暂停该 draft 重试，核对 implementation/artifact/receipt | 有完整 receipt 则幂等收敛 COMMITTED；无写入则恢复 VALIDATED；部分写入必须事务/人工修复 | 视写入事实 |
| F3 冲突激增 | 409/412 多 | 不放宽 ETag；检查前端是否使用旧 pins | 刷新 context，创建/重放新草稿；保留用户未保存内容 | 是 |
| F4 projection 失败 | visual 显示 NONE/PARTIAL | 立即降级 raw node，不运行 rewrite | 在 code view 修复或补 parser adapter golden case | 是 |
| F5 dts-dbt 不可用 | validate/commit 失败，save 正常 | 保留草稿与 ETag，禁止发布/物化 | 恢复依赖后重新 validate；旧 validated checksum 不自动复用 | 是 |
| F6 重复执行 | 相同 idempotencyKey 重放 | 比较 payload hash | 相同 payload 返回旧 receipt；不同 payload 返回冲突 | 不需回滚 |

## 6. 容量与保留

- 单草稿硬上限：128 文件、单文件 2 MiB、总计 16 MiB。
- 过期策略继续使用既有 `expiresAt`；过期活动草稿可按既有 cleanup 删除，COMMITTED receipt 保留。
- projection summary 只保存结构和 checksum，不复制文件正文。
- 达到活动草稿数/存储量实测 70% 时先分析过期清理和索引，不提高硬上限掩盖问题。

## 7. 禁止操作

- 禁止直接 UPDATE draft 状态、ModelSpec status、implementation checksum 或 candidate pins。
- 禁止 TRUNCATE/DELETE 草稿或 artifact 表来“解决卡顿”。
- 禁止绕过 ETag/validated checksum/dependency checksum 强制 commit。
- 禁止在 projection 失败时重写整包或丢弃未知文件。
- 禁止把审计日志当业务回滚工具；使用幂等命令和显式生命周期动作。

