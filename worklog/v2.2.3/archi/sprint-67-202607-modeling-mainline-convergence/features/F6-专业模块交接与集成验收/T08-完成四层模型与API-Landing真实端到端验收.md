# T08：完成四层模型与 API Landing 真实端到端验收

**优先级**：P0
**状态**：READY
**依赖**：F3-T08/T09/T10、F6-T02/T03/T06

## 目标

用真实 Spring Security、dts-platform、dts-ingestion、PostgreSQL 和 Chrome 95 证明业务维度、逻辑模型、实现和物理资产四层闭环，不以 mock API 或页面截图替代后端事实。

## 验收旅程

1. 无连接登记业务维度，创建 DIMENSION 逻辑草稿，并通过受控生成器物化日期维度；
2. 数据库连接测试、元数据同步、规划确认、模型实现、目标资产和血缘闭环；
3. API 采集任务试跑、Landing 表登记、规划确认、模型实现、目标资产和血缘闭环；
4. FACT → SUMMARY → APPLICATION 使用锁定模型 revision，漂移后阻塞并可修复；
5. 存量 DIMENSION 迁移、旧深链、旧 API、回滚和计数对账；
6. 轻量新建和三阶段详情在 Chrome 95 桌面/390px、刷新、失败和只读场景通过。
7. 普通模式生成 ephemeral STG 而不创建虚假物理表；转换 dbt 高级模式后保留逻辑模型并登记真实物化 STG。

## 一次性验证批次

- [ ] 一次后端完整契约/集成测试；
- [ ] 一次前端 production build；
- [ ] 一次真实 Chrome 95 + API + PostgreSQL E2E；
- [ ] 一次迁移 dry-run/execute/reconcile/rollback；
- [ ] 一次 GitNexus `detect_changes` 范围审计；
- [ ] Go/No-Go 明确代码、部署、迁移、浏览器和外部运行边界。

## 证据

证据写入 `it/evidence/` 的 `backend-contract/`、`api/`、`migration/`、`runtime/`、`frontend/`、`chrome95/`、`build/` 和 `gitnexus/`。每条结论必须链接真实文件和记录 ID。

## 完成标准

- [ ] 四条真实 Journey 全部通过；
- [ ] 零孤儿引用、零静默 revision 漂移、零跨部门权限扩大；
- [ ] API Landing checkpoint 连续且可重试；
- [ ] mock 结果只作为布局回归；
- [ ] 本 Task 关闭后才能重新进行 Sprint-67 最终 Go/No-Go。
