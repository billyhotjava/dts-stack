# T02：建立 ReleaseCandidate 及 Entry 持久化契约

**优先级**：P0
**状态**：IN_PROGRESS
**依赖**：T01

## 目标

建立候选头、候选条目、版本锁和审计字段的 PostgreSQL 真值，并提供可安全回滚的 Liquibase 变更。

## 技术设计

- 候选头保存 plan、environment、status、version、created/submitted/approved/published actor 与时间。
- 条目只保存模型稳定引用、顺序、当前条目状态和选择原因。
- 约束同一候选内模型唯一；索引覆盖 `tenant/plan/status` 与 `candidate/modelSpec`。
- 不用大 JSON 保存 ModelSpec、artifact、质量结果或发布明细。

## 影响范围

- 新增 `20260724_03_model_release_candidate.xml`
- 修改 `config/liquibase/master.xml`
- 新增 candidate entity/repository
- 新增 Liquibase 与 repository 集成测试

## 实施步骤

1. 先写 PostgreSQL repository 测试，覆盖唯一键、租户边界、乐观锁和删除限制。
2. 编写可逆 changeset、索引、外键和约束。
3. 实现最小 entity/repository，编写 upgrade/rollback 与并发 IT 源码，运行验证留到 F6。

## 完成标准

- [ ] 空库升级、现有库升级和单 changeset rollback 均通过。
- [ ] 并发版本字段、唯一约束和租户查询有自动化证据。
- [x] **UI 契约验收**：候选范围面板刷新后保持条目顺序、版本锁和唯一性；空候选、历史候选和版本冲突均有稳定 view-model fixture。

## 当前证据

- ReleaseCandidate/Entry 契约、JDBC repository、Liquibase upgrade/rollback 保护和 PostgreSQL IT 场景已实现，并通过 Java、数据库及契约 review。
- 静态 XML 校验与 `git diff --check` 已通过。
- 按 Sprint-69 统一测试窗口，本 Task 只完成测试源码、XML 静态校验和专项审查，不启动 Maven/PostgreSQL；数据库与并发用例待 F6 统一验证。
- 不修改并行 Sprint-67 文件；最终整体测试前保持 IN_PROGRESS。
- `_03` 会在三个既有 canonical 表上建立支持复合外键的唯一约束；F6 发布前必须按真实数据量评估扫描与写锁时间，并安排维护窗口或形成并发建索引的独立迁移方案。该运维风险未验证前不得直接在大表生产库执行。
