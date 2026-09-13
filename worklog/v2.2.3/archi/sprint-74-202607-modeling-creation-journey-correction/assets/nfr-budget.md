# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` + DTS Chrome95、审计、CAS 和兼容性不变量  
**适用范围**：模型创建、阶段门禁、纠错、实现配置和发布结果页面

| 维度 | 预算 | 适应度函数（可执行） | 归属 Task | 状态 |
|---|---|---|---|---|
| 数据量级 | 模型列表继续复用分页；单模型字段最多 500；同根因 blocker 去重 | `ModelSpecContract` 保持 500 上限；stage-gate 31 项验证去重和固定投影；未引入列表全量读取 | F5/T01 | PASS |
| 查询效率 | 详情与门禁不随字段数逐字段发 SQL | 详情/门禁各 100 次真实探针，P95 分别 16.562ms/24.094ms；实现保持批量 JSONB 读取，无字段循环 repository 调用 | F5/T01 | PASS |
| 延迟 | 详情/门禁 P95 ≤ 1s；纠错 preview 交互响应 ≤ 2s（本地验收环境） | 100 次真实详情与门禁 smoke；真实 preview 人机流程 | F5/T01 | PASS，见 `it/evidence/performance.md` |
| 并发 | ModelSpec、ModelImplementation、policy、reclassify 均 CAS；冲突返回 409/412 且不丢输入 | repository CAS IT；真实 stale ETag 改型返回 409 且未新增 command | F4/T01、F5/T01 | PASS |
| 幂等性 | create、reclassify、implementation save 重放不产生重复 revision/implementation | service/repository tests；真实改型重放返回相同 r3，ledger 精确 1 条 | F1/T02、F4/T01 | PASS |
| 批量上限 | 单模型 500 字段；单次纠错只处理一个模型 | contract 的 `MODEL_SPEC_FIELD_LIMIT_EXCEEDED`；reclassify API path 固定单 model id | F2/T01 | PASS |
| 事务边界 | reclassify 新 revision、head、command ledger 同事务；失败全部回滚 | repository Testcontainers + duplicate variant/stale ETag 真实失败路径均无部分写入 | F4/T01 | PASS |
| 审计 | 模型改型登记 actor、before/after type、result revision/checksum 和幂等键 | append-only command ledger 外键到结果 revision；真实查询为 1 条 | F4/T01、F4/T02 | PASS |
| 密级/权限 | 真实认证；密级只在 RELEASE_READY 阻断且 fail closed | 真实 portal session；release gate 保持 BLOCKED，DESIGNED/IMPLEMENTATION_READY 不被污染 | F4/T02 | PASS |
| 兼容性 | Chrome 95；旧 v2 JSON/深链/API 可读；新增字段向后兼容 | production legacy build + Chrome95 3/3 + snapshot/compatibility tests | F4/T03、F5/T01 | PASS |
| 失败模式 | 策略/来源/实现/发布服务不可用时不伪装空态，不放行下一阶段 | stage projection/source-contract + Chrome95 未发布结果空态 | F2/T03、F3/T03 | PASS |
| 可访问性 | 阶段按钮、类型卡、错误修复入口使用语义化交互；不用颜色单独表达必填状态 | DOM role/text 断言与 Chrome95 smoke；卡片包含显式文字上下文 | F1/T02、F5/T01 | PASS |
| 外部超时 | 本 Sprint 不新增外部 HTTP client | 静态 diff 断言无新增 client；若新增则本行重开 | F5/T01 | N/A（当前契约无新出站调用） |

## 验收结论

本 Sprint 的 blocking NFR 全部通过。列表 10k 仍由既有分页契约承接，本次没有修改列表查询拓扑；若未来改为跨模型聚合或逐字段表结构，需要重新打开数据量级与查询次数预算。
