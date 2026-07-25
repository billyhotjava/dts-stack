# Sprint-70 集成验收

## 验收原则

- Task 阶段只运行 focused test；F1-F4 全部实现后统一运行后端组合测试、前端 production build 和 Chrome 95 真实验收。
- mock API 只能用于开发定位，不能关闭 Sprint。
- 所有写入必须通过真实 Spring Security、租户/计划权限和 PostgreSQL。
- 构建、质量和发布证据由 Sprint-69 工作台承接；本 Sprint 至少证明导入产物能进入其候选范围。

## Journey A：PJM 预算模型包成功导入

1. 从 `worklog/v2.2.3/s10/v4/pjm/dbt_model` 生成模型包。
2. 在当前计划确认 `public.ods_budget_v2` SourceBinding。
3. 从建模工作台点击“导入已有模型”。
4. 绑定项目管理业务分类和预算来源。
5. 预检应显示 STG 为技术节点，DWD/DWS/ADS 为普通 ModelSpec + dbt 实现。
6. 确认导入后，在模型中心看到 FACT、SUMMARY、APPLICATION。
7. 打开详情核对粒度、字段、依赖、implementation revision 和 artifact。

## Journey B：阻断与修复

- 缺少业务粒度时，预检返回结构化 BLOCKED，不创建任何记录。
- 来源未确认或版本过期时，提供来源盘点修复入口；返回后保留包与上下文。
- 存在依赖缺失或循环时，阻断相关候选并显示 dependency chain。
- 静态 VALUES 维度在没有安全生成策略时归为 DBT_BACKED 或 BLOCKED，不能错误选择日期生成器。

## Journey C：幂等、漂移与并发

- 同包、同计划、同 idempotency key 重放返回原结果。
- 同键异载荷返回冲突。
- preview 后来源版本、ModelSpec revision 或包 checksum 变化时，apply 返回 preview stale。
- 两个账号并发 claim 同一 dbt node 时只有一个成功，失败项不留下半成品。

## Journey D：UI 与导航

- 工作台和模型中心入口打开同一向导。
- 工作台入口锁定当前计划；模型中心无上下文时要求明确选计划。
- 预检、确认、结果三种状态刷新后可恢复。
- 结果页可跳转到每个模型，并可仅重试失败项。
- Chrome 95 下步骤条、矩阵、抽屉/弹窗、长文本和窄屏降级可用。

## 最终证据

- [ ] `evidence/backend/model-package-contract-tests.txt`
- [ ] `evidence/backend/import-preview-apply-tests.txt`
- [ ] `evidence/frontend/production-build.txt`
- [ ] `evidence/chrome95/journey-a-import.png`
- [ ] `evidence/chrome95/journey-b-blocked.png`
- [ ] `evidence/chrome95/journey-d-result.png`
- [ ] `evidence/runtime/postgresql-modelspec-evidence.md`
- [ ] `evidence/go-no-go.md`

## Go/No-Go

仅当以下条件全部满足才可标记 DONE：

- JSON package、preview、apply、幂等和冲突测试通过。
- PJM 主链的来源、四类模型、revision pin 和 artifact 证据一致。
- 工作台/模型中心共享入口在真实后端下通过 Chrome 95。
- 无旧 SQL 模型被误当成 canonical 真值，无 STG 依赖丢失。
- 失败候选无半写入，审计可追溯。
