# T01：完成集中验证、发布观测与 Contract 判定

**优先级**：P0

**状态**：PARTIAL_LOCAL_VERIFY_PASS（E2E / DEPLOYMENT / OBSERVABILITY 未执行）

**证据**：`../../assets/implementation-evidence-20260810.md`

**依赖**：F0～F5 DONE、ADR-86-10

## 验证范围

- 后端：聚焦单元/集成测试、权限负向测试、migration preview/apply/rollback、索引与容量 fitness function。
- 前端：typecheck/build、source-contract、Chrome 95、四态、批量与二次物化。
- E2E：使用真实账号点击真实菜单入口；直接 URL 仅作深链兼容补充，不替代菜单验收。
- 发布：健康检查、审计查询、关键状态机、失败/卡顿/取消/重试和统计新鲜度观测。
- 回滚：兼容开关、旧读路径、批次数据回滚和应用镜像回滚分别演练。

## DoR

- [ ] F0～F5 DONE；无未解释的 HIGH/CRITICAL 影响或迁移 issue。
- [ ] 发布窗口、回滚责任人、测试账号、Chrome 95 和审计读取权限就绪。

## DoD

- [ ] 所有 Sprint-87 IT 证据集中执行并归档，失败只做针对性修复和重跑。
- [ ] 真实用户菜单旅程覆盖架构字典、模型批量/二次物化、资产归域、指标上下文和质量入口。
- [ ] 关键告警、日志字段、correlation id、容量假设和失败处置写入运行手册。
- [ ] 至少连续 14 天且跨一个发布周期无兼容回退后，才提交独立 Contract 评审。
- [ ] 未满足 Contract 条件时明确 NO-GO，旧字段/API/路由继续保留。
