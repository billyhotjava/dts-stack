# 集成与验收计划

本目录包含实施、构建和部署的真实证据。2026-08-28 已完成数据集成闭环的代码、聚焦测试、镜像构建和部署；2026-08-29 根据范围裁决退役独立编排入口，源码与本地 Chrome 证据见 IT-09，但该增量尚未部署。Chrome 95、三角色隔离会话和安全业务金丝雀仍是现场验收门禁，因此 Sprint 不标记为客户验收 DONE。

## 当前判定

| 层次 | 判定 | 说明 |
|---|---|---|
| 功能源码 | PASS | design/topology/revision/schedule/runtime/quality-asset projection 已实现 |
| 聚焦验证 | PASS | 既有 ingestion 38、platform 27；本次增量 backend 5、frontend source 5、component 3，类型/格式/legacy build 通过 |
| 数据库与部署 | PASS | 既有 3 个 changeSet、3 个 nullable UUID 列、2 个唯一索引；本次仅定向替换 ingestion/webapp，健康与路由通过 |
| 本地 Chrome | PASS_WITH_ENV_NOTE | Chrome 150 新增长期 E2E 1/1 通过；默认列表、task/revision 过滤、桌面/窄屏证据及现场缺口见 IT-07 |
| 独立编排入口退役 | SOURCE_VERIFIED_DEPLOY_PENDING | 数据集成唯一业务入口、旧地址兼容跳转、菜单软删除迁移和本地 Chrome 152 双视口已通过；未部署，见 IT-09 |
| Chrome 95 / 三角色 / 业务金丝雀 | ENVIRONMENT_GAP | 不用本地 Chrome 150 或自动化契约冒充现场通过 |

## 单一集中验收旅程

1. 只读角色打开带 taskId 的“数据集成流程”，可查看表单和 ACTIVE 拓扑，保存/发布/运行由服务端拒绝。
2. 编辑角色选择金丝雀任务，依次修改数据源、目标资产与 mapping、调度和“接入后质量验证”，确认目标 dataset 与已绑定规则摘要后保存草稿。
3. 两个会话基于同一 plan checksum 修改；后保存会话收到 409，本地输入保留且可恢复。
4. 分别提交连接失败、失效对象、非法 mapping、错误 Cron、目标资产未解析、跨资产质量引用、无有效规则、越权/高密级引用，服务端返回稳定错误并定位步骤字段。
5. 合法 design 校验通过；核对 design、DRAFT topology、validation 使用同一 checksum。
6. 提交发布；核对 revision、ACTIVE topology、DAG metadata 和数据库 snapshot 使用同一 plan checksum。
7. 启用、暂停并重复点击，确认命令幂等且只作用于当前任务 owned DAG。
8. 执行角色发起第一批运行；确认 execution 绑定 task/revision/plan checksum/datasetId，跨部门访问被拒绝。
9. 接入成功后先看到“待质量验证”，再核对唯一 workflow/run；从 execution 深链目标资产和质量详情，接入状态与质量状态分别展示。
10. 第一批质量通过且消费资格为 ELIGIBLE 时显示“可信可用”；发起第二批后，第一批证据立即为 STALE，新证据完成前不再显示可信。
11. 受控制造一次正式质量失败和一次触发耗尽；确认 ingestion 仍为 SUCCESS、资产仍存在、消费资格/原因更新且不沿用旧 PASS。
12. 查看状态和所有 connector 日志；对失败实例重试并核对 sourceExecutionId；取消可取消实例并收敛终态。
13. 核查成功/失败审计、变更摘要、质量触发关联、脱敏和 correlationId。
14. 验证 legacy graphDsl 可导出但不可发布；执行 migration dry-run、DAG/质量证据对账和回滚演练。
15. 用 Chrome 95 重跑同一旅程，记录 console、network、截图和制品版本。

## 自动化层次

- 单元：design 规范化、plan checksum、validation、topology projection、execution/quality evidence 状态机、可信派生、脱敏。
- 服务集成：design GET/PUT、409/422/403、admit checksum、质量 post-commit 触发、幂等、补偿、当前证据、唯一约束。
- 前端组件：task 切换、步骤表单、目标资产卡、脏数据保护、投影只读、字段错误定位、双时间线、可信派生、按钮状态。
- 契约：平台代理与 ingestion 的 DTO、错误码、Header、权限和审计透传。
- E2E：上述单一金丝雀旅程；完整实现后集中运行一次。

## 证据文件

- `IT-01-design.md`：任务选择、表单、保存、冲突。
- `IT-02-projection.md`：DRAFT/ACTIVE 投影和 checksum。
- `IT-03-release.md`：校验、准入、调度、失败补偿。
- `IT-04-runtime.md`：任务级查询、分页、权限、深链。
- `IT-05-recovery.md`：运行、重试、取消、日志。
- `IT-06-governance.md`：迁移、legacy DSL、审计、DAG 对账。
- `IT-07-chrome95.md`：目标浏览器单旅程与制品版本。
- `IT-08-asset-quality.md`：目标资产、post-commit 质量触发、当前证据切换、消费资格和双向深链。
- `IT-09-orchestration-retirement.md`：数据集成唯一入口、菜单迁移、兼容跳转、源码构建和本地 Chrome 双视口证据。
- `source/dts-platform-webapp/e2e/data-integration-mainline.spec.ts`：接入概览、连接管理、历史地址跳转、无独立编排入口和双视口只读回归。

上述文件只有在产生真实证据时创建；禁止提前放置 TODO 或占位截图。
