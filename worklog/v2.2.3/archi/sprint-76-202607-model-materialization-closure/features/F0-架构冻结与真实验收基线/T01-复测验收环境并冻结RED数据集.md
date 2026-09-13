# T01：复测验收环境并冻结 RED 数据集

**优先级**：P0
**状态**：DONE（2026-07-27；证据复用 + 漂移触发复测）
**依赖**：无

## 目标

以同日已有认证、API、PostgreSQL、Airflow、dbt、构建和 Chrome95 证据证明验收路径可用，并冻结四类代表模型“尚未物化”的可重复 RED 状态。除非相关环境或代码发生漂移，不为关闭文档状态重复执行高成本基线。

## 技术设计（Contract-first）

- **输入契约**：Sprint-74 四类 ModelSpec/Implementation；当前 compose；Airflow 2.9.3/LocalExecutor；两个已注册 dbt DAG。
- **输出契约**：更新 `it/baseline.md`，每个 P1～P8 标记实测结果或被复用的同日证据；目标关系查询返回 0/4 存在；附录记录 DAG 共享目录/扫描周期/default timezone/max_active_runs/profile target/secret gap。
- **数据流**：引用账本 L16～L19；不得重建平行样本。只允许创建独立验收 candidate/run，不修改用户“财务项目模型”。
- **错误路径**：登录、DNS、migration、DAG、dbt target、Chrome95 任一失败即登记 F0 blocker；发现 tracked credential 必须登记 F2/T04 No-Go，不得在证据中复制 secret。
- **复用点**：Sprint-74 登录/Cookie harness、Chrome95、代表模型和 dbt 环境。
- **安全**：证据脱敏，禁止落 Cookie、token、密码和 profiles secret。

## 影响范围

- `it/baseline.md`
- `it/evidence/baseline-*`
- 无业务源码修改

## 验证（RED→GREEN）

- [x] 当前四个 relation 的系统表查询为不存在。
- [x] `pipeline_run=0`、`release_candidate=0` 作为初始 RED 被记录。
- [x] 真实登录、protected API、Chrome95 页面可达证据已由同日 Sprint-74 验收归档并复用。
- [x] DAG 注册、unpaused，dbt 命令和目标数据库连接可用。
- [x] Airflow executor/default timezone/DAG scan/shared mount/max_active_runs 可重复查询。
- [x] dbt target 数量和 tracked-secret RED 只记录结构/扫描结果，不记录 credential 值。
- [x] 当前后端聚焦测试与前端 Chrome95 production build 已在 Sprint-36/F3 依赖交付后执行并记录。

## Definition of Done

- [x] G0 实施入口结论可由证据复现。
- [x] blocker 全部显式关联 Task。
- [x] 无假数据、无手工补状态、无敏感信息。

## 复测触发条件

以下任一条件成立时才定点刷新对应探针，不整套重跑：

1. 认证、路由、Chrome95 兼容代码发生变化；
2. compose、Airflow、dbt profile/target、目标数据库或部署配置发生变化；
3. 相关既有证据超过本 Sprint 验收窗口或出现相互矛盾；
4. F6 进入真实端到端验收，需要生成本 Sprint 自身的最终证据。

F1 的纯后端契约/编译器修改只运行相关聚焦测试；不触发登录或 Chrome95。
