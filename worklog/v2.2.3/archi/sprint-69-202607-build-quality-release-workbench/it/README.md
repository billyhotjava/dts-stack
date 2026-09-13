# Sprint-69 IT 与交付证据计划

**状态**：READY

## 0. 测试执行节奏

- Task 实施期间只编写测试源码、fixture，执行静态检查与专项审查，不启动运行测试。
- Feature 的 Task 全部完成后只做跨 Task 静态核对，不单独运行组合回归。
- F1-F5 全部功能和 F6 验收资产完成后，在 F6 单一窗口统一运行整体测试并保存一次完整证据。
- F1-F6 全部实现后，才统一执行本文件定义的真实 API/PostgreSQL/dbt/Chrome 95、production build、部署和回滚旅程。
- 无新代码或修复进入时不得机械重复相同全量命令；已有失败日志作为后续修复输入保留。

## 1. 验收旅程

### Journey A：创建候选并完成构建

1. 从数据建设工作台进入同一 `planId` 的“实现与验证”；
2. 创建 DEV 环境候选，只选择本次要发布的两个 ModelSpec；
3. 服务端锁定每个 `modelSpecId/revision/checksum`；
4. 执行真实 dbt compile/build，保存 SQL/SCHEMA/TEST/DOC 和 external run；
5. 另一个未加入候选的 DRAFT 模型不阻塞本次候选；
6. 修改候选内模型产生新 revision，旧候选变 STALE 并阻止继续。

### Journey B：质量失败与修复

1. 使用重复 grain、必填空值、无效码值和维度孤儿数据运行质量检查；
2. 页面显示规则、版本、阈值、实际值、BLOCKER/WARN 和失败样本引用；
3. BLOCKER 阻止提交审核，WARN 需要显式确认；
4. 返回精确模型或质量规则修复；
5. 修复并重新构建后产生新 QualityRun，旧 PASSED/FAILED 结果保留但不再作为当前证据。

### Journey C：职责分离审核与发布

1. 建模维护者提交 QUALITY_PASSED 候选；
2. 同一账号尝试批准，服务端拒绝且无新事件；
3. 审核者驳回并填写原因，候选返回可修复状态；
4. 重新提交后由另一审核者批准；
5. 发布者发布当前 APPROVED revision；
6. 工作台、模型详情和 StageProjection 读取同一 release 事实。

### Journey D：部分注册与幂等重试

1. Catalog、Lineage 成功，BI 注册故障；
2. release 状态为 PARTIAL，第六步不显示完成；
3. 页面只展示 BI 失败并允许重试；
4. 重试不重复 Catalog/Lineage，BI attemptCount 增加；
5. 三步成功后 release 变 PUBLISHED，StageProjection 第六步 COMPLETE。

### Journey E：并发、权限与回滚

1. 两个操作者持有相同 candidate ETag；
2. 首个 mutation 成功，第二个返回 409 并保留输入；
3. 只读、跨租户、跨部门和伪造 candidate/model/run 请求被拒绝；
4. 对已发布候选执行回滚，生成新事件并保留旧发布证据；
5. 回滚专业动作部分失败时显示 ROLLBACK_PARTIAL 并可重试；
6. 最终恢复目标版本并核对资产、BI、血缘和数据库计数。

### Journey F：真实浏览器与失败恢复

1. 使用真实登录、API、PostgreSQL 和最小 dbt 项目完成 A-D 主线；
2. dbt 不存在、pending、failed、selector 不匹配分别 fail closed；
3. 网络失败、局部 API 失败和刷新不会把 UNKNOWN 显示为完成；
4. 从失败 run 返回精确 candidate/model/revision；
5. Chrome 95 桌面与 390px 可完成候选、构建、质量、审核、发布、重试和回滚。

## 2. 自动化矩阵

| 层级 | 必测内容 | 证据目录 |
|---|---|---|
| Contract | candidate/entry、状态机、ETag、事件和 blocker code | `it/evidence/contracts/` |
| Backend Unit | 候选范围、漂移、dbt run 绑定、质量阈值、职责分离、幂等 | `it/evidence/backend/` |
| API/Security | 强 If-Match、角色、租户/部门、跨对象伪造、错误码 | `it/evidence/api-security/` |
| PostgreSQL | canonical claim→compile→quality→review→publish→retry→rollback | `it/evidence/postgresql/` |
| dbt Runtime | compile/build/test、selector/target、失败数据、日志和 target 表 | `it/evidence/dbt/` |
| Frontend | 视图模型、单一主动作、错误恢复、无自动批准发布 | `it/evidence/frontend/` |
| Chrome 95 | 真实主线、权限、并发、PARTIAL、STALE、390px | `it/evidence/chrome95/` |
| Build | dts-platform、dts-platform-webapp production build | `it/evidence/build/` |
| Scope | GitNexus impact/detect_changes、预期模块和流程 | `it/evidence/gitnexus/` |
| Release | 迁移、部署、回滚演练和 Go/No-Go | `it/evidence/release/` |

## 3. 必测负例

- 候选为空、重复模型、跨计划模型、ARCHIVED/legacy 模型；
- 客户端伪造 revision/checksum/implementationMode；
- 已开始构建后修改候选范围；
- artifact checksum 漂移或缺 SQL/SCHEMA/TEST；
- externalRunId 不存在、非 DBT、未终态、属于其他 selector/target/model；
- dbt aggregate run 只覆盖候选部分模型；
- 质量规则版本漂移、BLOCKER 失败、WARN 未确认、PASSED 超过新鲜度；
- submittedBy 与 approvedBy 相同；
- 未提交直接批准、未批准直接发布、旧 ETag 发布；
- Catalog/BI/Lineage 任一步失败和重复重试；
- PUBLISHED 之前 StageProjection 误报 COMPLETE；
- rollback 非当前 release、重复 rollback、专业动作部分失败；
- API 500、401/403/404/409/412/422、刷新和局部重试。

## 4. 模型类型质量样例

| 类型 | 必测样例 |
|---|---|
| DIMENSION | 维度键唯一、SCD2 区间不重叠、唯一 current、层级引用 |
| FACT | grain 唯一、业务时间非空、dimensionRefs 参照完整 |
| SUMMARY | 上游 revision 当前、分组键完整、聚合对账 |
| APPLICATION | 输出契约、上游 revision、行数/新鲜度/SLA |

DIMENSION 场景在 Sprint-67 四层接口稳定后执行；其他三类先完成真实链路。

## 5. 完成真实性

- README 只索引真实日志、JUnit/XML、JSON、SQL 对账、dbt artifacts 和截图。
- source-contract 只证明源代码约束，不等于按钮实际完成 API 状态迁移。
- mock 浏览器只证明 UI 恢复和 Chrome 95 兼容，不替代真实后端。
- 预置 PUBLISHED 模型或直接 SQL 更新状态不能作为 canonical publish E2E。
- F6-T04 必须分别给出代码、测试、迁移、部署、浏览器和回滚六层结论。
