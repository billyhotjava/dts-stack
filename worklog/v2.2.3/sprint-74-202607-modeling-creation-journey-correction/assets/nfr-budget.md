# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` + DTS Chrome95、审计、CAS 和兼容性不变量  
**适用范围**：模型创建、阶段门禁、纠错、实现配置和发布结果页面

| 维度 | 预算 | 适应度函数（可执行） | 归属 Task | 状态 |
|---|---|---|---|---|
| 数据量级 | 模型列表 10k、单模型字段最多 500、单 gate blocker 最多 100 | Repository/IT 以 fixture 断言分页；501 字段返回 422；blocker 去重后不超限 | F5/T01 | GAP |
| 查询效率 | 详情首屏模型+门禁总查询数固定，不随字段数 N+1 增长 | 集成测试统计 SQL；500 字段和 100 引用时查询次数不增长 | F5/T01 | GAP |
| 延迟 | 创建/逻辑保存/门禁 P95 ≤ 1s；纠错 preview P95 ≤ 2s（本地验收环境） | 100 次 API smoke 输出 P95 并断言阈值 | F5/T01 | GAP |
| 并发 | ModelSpec、ModelImplementation、policy、reclassify 均 CAS；冲突返回 409/412 且不丢输入 | 并发 IT 双写同 revision，仅一个成功 | F4/T01、F5/T01 | GAP |
| 幂等性 | create、reclassify、implementation save 重放不产生重复 revision/implementation | 重复同 idempotencyKey 断言同响应与计数 | F1/T02、F4/T01 | GAP |
| 批量上限 | 单模型 500 字段；单次纠错只处理一个模型 | 边界测试 500/501 | F2/T01 | GAP |
| 事务边界 | reclassify 新 revision、head 更新、审计同事务；失败全部回滚 | 故障注入 IT 断言 revision/head/audit 计数不变 | F4/T01 | GAP |
| 审计 | 模型改型、计划分层策略、实现方式切换均登记动作和 before/after revision | IT 查询审计目录及事件分类，不得为“未分类” | F4/T01、F4/T02 | GAP |
| 密级/权限 | 未授权写返回 403；密级只在 RELEASE_READY 阻断且 fail closed | 只读角色调用写 API=403；传播不可用时 release gate=BLOCKED | F4/T02 | GAP |
| 兼容性 | Chrome 95；旧 v2 JSON/深链/API 可读；新增字段向后兼容 | legacy build + Chrome95 smoke + 旧 snapshot codec fixture | F4/T03、F5/T01 | GAP |
| 失败模式 | 策略/来源/实现/发布服务不可用时保留已加载数据，不伪装空态，不放行下一阶段 | 故障桩 + UI source-contract 四态断言 | F2/T03、F3/T03 | GAP |
| 可访问性 | 阶段按钮、类型卡、错误修复入口可键盘聚焦；不用颜色单独表达必填状态 | axe/键盘 smoke；DOM 文本断言 | F1/T02、F5/T01 | GAP |
| 外部超时 | 本 Sprint 不新增外部 HTTP client | 静态 diff 断言无新增 client；若新增则本行重开 | F5/T01 | N/A（当前契约无新出站调用） |

## 未达标项处置

当前所有可执行适应度函数均尚未实施，G1 为 GAP。架构复审通过后由 F5/T01 建立统一验证入口；在此之前任何 Feature 不得标记 DONE。

