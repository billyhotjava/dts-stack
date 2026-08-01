# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` + 当前实现保护上限  
**状态**：DRAFT/GAP；客户样本未画像前不得标记 PASS。

| 维度 | 第一版预算 | 可执行适应度函数 | 归属 Task | 状态 |
|---|---|---|---|---|
| 批量上限 | 单次 apply 最多 200 个 dbt uniqueId | 第 201 个返回 4xx；无部分写入 | F1/T01、F3/T03 | EXISTING/VERIFY |
| 图复杂度 | ≤500 节点、≤10000 边、深度≤128 | 边界值通过，超限稳定返回业务错误码 | F1/T02、F6/T01 | EXISTING/VERIFY |
| SQL/宏大小 | 单 SQL≤2 MiB、总 SQL≤16 MiB、宏≤4 MiB；首期不放宽 | 边界/超限 fixture contract test | F1/T02、F5/T01 | EXISTING/VERIFY |
| 数据预览 | limit 1..500，默认不超过 100；禁止无界查询 | 501 返回 4xx；生成 SQL 必含 server-side limit | F2/T04、F6/T03 | GAP |
| 并发 | 同 tenant+plan 同一 package checksum 只允许一个有效 preview/apply；implementation commit 使用 ETag/CAS | 并发 IT 断言单一 winner，其他 409/幂等 replay | F1/T04、F3/T04 | GAP |
| 幂等 | inspect 无副作用；apply/retry/commit 重放不产生重复修订和 artifact | 相同 idempotencyKey 重放摘要、行数和 checksum 不变 | F2/T03、F3/T04 | PARTIAL |
| 事务 | 单模型 ModelSpec + Implementation + artifact + audit 同事务；整包可部分成功但逐项结果不可丢 | 故障注入验证已成功项耐久、失败项可 retry | F3/T04～T05 | PARTIAL |
| 超时 | inspect/preview/validate/物理预览必须有显式 server/client timeout；具体秒数由 FX-01～03 P95 后冻结 | 静态契约断言 timeout 非空；延时 fixture 返回 408/504/业务超时 | F0/T02、F5/T04 | GAP |
| 临时存储 | 仅受控临时目录/tmpfs；失败、取消、超时后无残留；启动校验 fail-closed | fixture 后检查临时目录为空；不可写/非受控目录启动失败 | F5/T01 | GAP |
| 供应链 | inspect/preview 不下载 packages、不访问 Git、不执行模型 SQL | 网络隔离 fixture + 调用桩断言 0 外联/0 build | F5/T01 | GAP |
| 租户 | 所有 project/model/import run/draft/candidate 查询强制 server-side tenant | 双租户 IT；越权 403/404 且不泄露 checksum | F5/T03 | GAP |
| 审计 | 100% 重要动作进入分类正确的公共审计；SQL/ZIP/secret 正文为 0 | PostgreSQL IT + dts-admin ingest 断言动作、stage、脱敏 | F5/T02 | GAP |
| 漂移 | base/current/incoming 任一 checksum/ETag 不一致不得静默覆盖 | 三方冲突 IT 必须返回 CONFLICT，当前修订不变 | F1/T04、F3/T05 | GAP |
| 兼容 | Chrome 95；dbt/manifest/adapter 版本以真实 fixture 矩阵为准 | Chrome95 E2E + fixture 参数化测试 | F6/T01～T04 | GAP |

## 未达标项处置

- 性能秒数、支持版本和 adapter 范围依赖 F0/T02 的真实样本画像。
- 任何 `GAP` 行未绑定并通过可执行检查前，对应实现 Task 不得进入 DONE。
- 不通过放宽 ZIP/SQL/图限制来迁就未知客户包；先画像，再单独评审容量变化。
