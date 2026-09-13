# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md`、既有 dbt draft contract、DTS Chrome 95/审计/权限不变量  
**适用范围**：authoring context、组合草稿、projection、统一 save/validate/commit、兼容 adapter

| 维度 | 预算 | 可执行适应度函数 | 归属 Task | 当前状态 |
|---|---|---|---|---|
| 文件容量 | ≤128 文件；单文件 ≤2 MiB；总计 ≤16 MiB | 复用/扩展 draft contract tests：129 文件、2 MiB+1、16 MiB+1 均 422 | F1/T02 | PINNED |
| 草稿读取 | 31 个模型历史下限、128 文件上限时，context P95 ≤1.5s，打开含正文草稿 P95 ≤3s | Testcontainers 数据集 + 50 次请求，输出 P95 | F5/T02 | GAP（待当前画像） |
| 保存延迟 | 16 MiB 内 save P95 ≤5s；不得在请求线程运行 dbt build | service IT 计时；静态调用链断言 save 不触发执行器 | F1/T02 | PINNED |
| 查询效率 | context 单请求不得逐文件/逐字段 N+1；draft 文件一次有界读取 | datasource-proxy 查询计数断言；上限在 RED 阶段固定 | F1/T02 | READY_FOR_TEST |
| 并发 | 所有写使用 draft ETag + model/implementation pins；过期写 409/412，不覆盖 | 两 actor 并发 IT；最终只有一个 commit receipt | F4/T01 | PINNED |
| 幂等 | create/fork/commit 同一 idempotencyKey+payload 返回同一结果；异 payload 409 | service/resource idempotency IT | F1/T03、F4/T01 | PINNED |
| 事务 | commit 的 ModelSpec、implementation、artifact、receipt、audit 要么全成要么全回滚 | PostgreSQL 失败注入 IT，逐写点抛错后断言无半提交 | F4/T01 | GAP |
| 投影安全 | unmanaged path 内容 checksum 零变化；歧义转换一律 raw node | golden bundle tests + before/after checksum | F2/T02、F2/T03 | PINNED |
| 依赖一致性 | visual/code commit 的 dependency checksum 必须一致；missing/undeclared/cycle fail closed | dependency resolver IT | F4/T01 | PINNED |
| 权限 | 技术正文仅 `CATALOG_MAINTAINERS`；越权写 403，读取不得泄露文件正文 | MockMvc 角色矩阵 + 浏览器受限账号 | F4/T02 | PINNED |
| 审计 | fork/save/validate/commit/兼容调用均归类；SQL/YAML 正文不得出现 | 审计 catalog test + payload key negative assertion | F4/T02 | PINNED |
| 兼容 | Chrome 95；visual 首屏不加载 Monaco；旧 REST 在兼容期仍有稳定响应 | legacy build、bundle analysis、Chrome95 smoke、旧路由 IT | F3/T01、F5/T02 | GAP |
| 失败恢复 | dts-dbt 不可用时 save 可用，validate/commit 显示可重试且无状态漂移 | 故障注入 IT | F4/T01、F5/T02 | PINNED |
| 草稿保留 | 继续沿用既有 expiresAt；过期草稿不复活，COMMITTED receipt 不清理 | repository cleanup IT | F1/T01 | PINNED |

## 未达标项处置

| 缺口 | 影响 | 处置 | Task |
|---|---|---|---|
| 当前模型/draft 数据量未刷新 | 延迟与迁移批次只能按历史下限 | F0 只读画像后校准 fixture，不降低既有硬边界 | F0/T01 |
| PostgreSQL 原子失败注入未执行 | 不可证明一个 commit 无半状态 | 在 F4 完成后运行 focused Testcontainers IT | F4/T01 |
| Chrome 95 未执行 | UI DoD 不可关闭 | 所有实现完成后集中执行 | F5/T02 |

