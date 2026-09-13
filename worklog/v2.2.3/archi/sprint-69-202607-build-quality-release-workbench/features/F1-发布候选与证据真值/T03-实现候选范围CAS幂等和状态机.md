# T03：实现候选范围 CAS 幂等和状态机

**优先级**：P0
**状态**：IN_PROGRESS
**依赖**：T02

## 目标

实现候选创建、改范围、锁定、失效和状态迁移，保证重复提交无副作用，并发写入不能覆盖新版本。

## 技术设计

- 所有写命令要求强 ETag 或 `expectedVersion`。
- 创建与状态迁移支持租户内幂等键；相同键不同 payload 返回冲突。
- 锁定后模型 revision/checksum 漂移自动标记 STALE。
- 非法迁移返回稳定业务错误，不用静默 no-op。

## 影响范围

- 新增 `ModelReleaseCandidateService.java`
- 扩展 candidate repository 与 contract
- 新增 service/state-machine 测试

## 实施步骤

1. 先写重复命令、版本冲突、漂移和非法迁移失败测试。
2. 实现纯状态机，再接 repository 事务和审计。
3. 完成 T03 实现、测试源码、静态检查和专项审查；运行验证留到 F6 单一整体测试窗口。

## 完成标准

- [ ] 重复请求、并发请求、跨租户请求和漂移场景全部可预测。
- [ ] 任一状态变更同时产生 actor、时间、原因和前后状态审计。
- [x] **UI 契约验收**：STALE 必须返回可展示的漂移原因并禁用后续动作；409 必须提供当前版本，使页面能够保留本地选择并提示刷新或重放。

## 实现与验证证据

- 已实现候选创建、DRAFT 范围替换、强版本 CAS、租户级幂等回放、异 payload 冲突、漂移转 STALE、非法迁移、终态 replacement 和职责分离。
- header、entries 与命令事件同事务写入；候选聚合读取使用单条 CTE + `LEFT JOIN`，避免 PostgreSQL `READ COMMITTED` 下旧 header 与新 entries 的撕裂读。
- canonical 范围只接受同计划的 ModelSpec v2 当前 revision/checksum/mode；PostgreSQL IT 源码包含 legacy、revision mismatch 和 checksum mismatch 负向 fixture。
- Java、数据库和通用代码静态复审均为 Approved；`xmllint` 与 `git diff --check` 通过。
- 按用户指定的统一测试窗口，本 Task 不启动 Maven/数据库测试；运行验证延后到全部功能实现后的 F6 整体测试，因此暂不标记 DONE。
