# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` + 当前实现保护上限  
**状态**：DRAFT/GAP；工程适应度函数与客户兼容声明分别判定。客户样本未画像前客户声明不得标记 PASS，但不阻断已具工程证据的通用切片进入 READY。

| 维度 | 第一版预算 | 可执行适应度函数 | 归属 Task | 状态 |
|---|---|---|---|---|
| 批量上限 | 单次 apply 最多 200 个 dbt uniqueId | 第 201 个返回 4xx；无部分写入 | F1/T01、F3/T03 | EXISTING/VERIFY |
| 图复杂度 | ≤500 节点、≤10000 边、深度≤128 | 边界值通过，超限稳定返回业务错误码 | F1/T02、F6/T01 | EXISTING/VERIFY |
| SQL/宏大小 | 单 SQL≤2 MiB、总 SQL≤16 MiB、宏≤4 MiB；首期不放宽 | 边界/超限 fixture contract test | F1/T02、F5/T01 | EXISTING/VERIFY |
| 数据预览 | 用户显式加载；默认 100、limit 1..500；单次有限查询，不分页遍历/导出；样例 `private,no-store`，历史 revision 无样例行 | 缺省断言100；20/50/100/500通过、501为4xx；SQL含 server-side limit；无 offset/cursor/export；缓存/日志/审计样例值为0 | F2/T04、F5/T03、F6/T03 | GAP |
| 预览标识符安全 | relation 只从固定 observation evidence 解析；全部 pin 一致；规范 identifier 白名单 + adapter 专属引用；非法 identifier 在数据库调用前拒绝 | 双引号/分号/注释/点号/超长/Unicode 等恶意 fixture 返回 `PHYSICAL_PREVIEW_IDENTIFIER_INVALID` 且目标数据库查询计数=0；合法保留字经 adapter 引用成功；PostgreSQL database 不进 SQL；任一 pin 篡改返回 EVIDENCE_MISMATCH、0 行、0 查询 | F2/T04、F5/T03、F6/T04 | GAP |
| Catalog serving | latestPublishedRef 与 servingRef 独立；只有成功 candidate + relation/quality evidence 可 CAS 切换 serving；失败/stale/乱序保留旧 serving | r1 serving→r2 published/materializing/failed/succeeded/乱序矩阵；失败仍消费r1，成功单 winner切r2，build-only/stale不切换 | F4/T03～T04、F6/T03 | GAP |
| 并发 | 同 tenant+plan 同一 package checksum 只允许一个有效 preview/apply；implementation commit 使用 ETag/CAS | 并发 IT 断言单一 winner，其他 409/幂等 replay | F2/T03、F3/T04 | GAP |
| 幂等 | inspect 无副作用；apply/retry/commit 重放不产生重复修订和 artifact | 相同 idempotencyKey 重放摘要、行数和 checksum 不变 | F2/T03、F3/T04 | PARTIAL |
| 事务 | BLOCKED 项不能选择；单模型 ModelSpec + Implementation + artifact + audit 同事务；PARTIAL 只表示已选合格项启动后出现逐项成功/失败混合，结果不可丢 | 选择含 BLOCKED 闭包时 apply 前拒绝；故障注入后已成功项耐久、可重试失败项可 retry，失败项不推进 accepted base | F3/T03～T06 | PARTIAL |
| 失败可解释性 | 100% FAILED/BLOCKED 项具有稳定 code/stage/category/message/retryable/recoveryAction/correlationId；SQL/ZIP/secret/stack 正文为 0 | 契约测试校验必填率、summary 恒等式、UI 行级呈现和日志/审计脱敏 | F3/T04～T06、F5/T02 | GAP |
| 超时 | inspect/preview/validate/物理预览必须有显式 server/client timeout；具体秒数由 FX-01～03 P95 后冻结 | 静态契约断言 timeout 非空；延时 fixture 返回 408/504/业务超时 | F0/T02、F5/T04 | GAP |
| 临时存储 | 仅受控临时目录/tmpfs；失败、取消、超时后无残留；启动校验 fail-closed | fixture 后检查临时目录为空；不可写/非受控目录启动失败 | F5/T01 | GAP |
| 供应链 | inspect/preview 不下载 packages、不访问 Git、不执行模型 SQL | 网络隔离 fixture + 调用桩断言 0 外联/0 build | F5/T01 | GAP |
| 租户 | 所有 project/model/import run/draft/candidate 查询强制 server-side tenant | 双租户 IT；越权 403/404 且不泄露 checksum | F5/T03 | GAP |
| 审计 | 100% 重要动作进入分类正确的公共审计；SQL/ZIP/secret 正文为 0 | PostgreSQL IT + dts-admin ingest 断言动作、stage、脱敏 | F5/T02 | GAP |
| 漂移 | accepted base/current Implementation/incoming 技术 checksum 按三方矩阵处理；ModelSpec ETag 变化只触发映射重验，任何变化均不得静默覆盖 | 参数化 IT 断言技术双边分歧为 CONFLICT、业务语义保留、映射失效为 BLOCKED_REMAP，当前修订不变 | F1/T05、F3/T05 | GAP |
| 兼容 | Chrome 95；dbt 拆分 INSPECT/IMPORT/MATERIALIZATION 三轴；只有精确 Core+adapter package+数据源+image digest 可标记 CERTIFIED；未认证 fail-closed | H83-01/RT-01 固定依赖锁、`pip check`、`dbt --version`、digest 与真实 PostgreSQL parse/compile/build/run/relation evidence；FX 参数化三轴；客户包单独标记 CUSTOMER-VALIDATION | H83-01、F0/T02、F0/T05、F6/T01～T04 | GAP/NOT_CERTIFIED |

## 未达标项处置

- 工程超时初始预算由 RT-01/FX 的实测 P95 冻结；客户性能秒数和兼容分布只由 CUSTOMER-VALIDATION 画像确认。当前镜像 Core 与标签不一致，首个 PostgreSQL certification profile 必须先通过 H83-01 的精确依赖锁和真实运行验证。
- 任何 `GAP` 行未绑定并通过可执行检查前，对应实现 Task 不得进入 DONE。
- 不通过放宽 ZIP/SQL/图限制来迁就未知客户包；先画像，再单独评审容量变化。
