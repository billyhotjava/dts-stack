# 按钮与组件矩阵

| 控件/组件 | 当前行为 | 目标前置条件 | 成功反馈 | 错误/阻断反馈 | 自动化检查 | 状态 |
|---|---|---|---|---|---|---|
| 任务选择器 | 不存在 | task read 权限 | 名称、状态、revision、plan checksum | 403/404/空任务 | 选择/深链/越权 | MISSING |
| 新建接入任务 | “新建 DAG”仅清画布 | 复用现有任务创建契约 | 创建后 URL 带 taskId | 无权限、创建失败 | 不产生孤立本地图 | REPLACE |
| 数据源步骤 | 自由文本节点表单 | typed connector schema | 已选数据源/对象摘要 | 连接/对象/密级错误 | connector 矩阵 | REPLACE |
| 目标与映射步骤 | 自由文本/任意 config | typed destination/mappings | 映射数量与校验状态 | 类型/必填/writeMode 错误 | mapping 边界 | REPLACE |
| 目标资产卡片 | 不存在 | destination 可解析为 canonical dataset | 资产名称、datasetId、解析来源 | 未解析/无权限/跨部门 | 同目标稳定解析 | MISSING |
| 调度步骤 | 只存 manual/cron 模式 | schedule schema | Cron/时区摘要 | 无效表达式/时区 | manual/cron | REPLACE |
| 接入后质量验证 | 原始规则 JSON | 目标 asset 已解析且有已发布、启用规则 | 已开启、规则数量、提交后执行 | 目标不一致/无规则/无权限 | 同资产绑定/post-commit | REPLACE |
| 自动拓扑 | 可自由拖拽画布 | taskId + design/revision | 显示一致 plan checksum | 投影失败/版本冲突 | 只读/确定性 | REPLACE |
| 保存草稿 | localStorage + 完整 PUT | taskId、write、If-Match | 新 checksum/保存时间 | 409 保留输入；422 定位字段 | 双会话/断网 | PARTIAL |
| 重置本次修改 | 清全局画布 | 当前 task 未保存输入 | 恢复服务端 design | 二次确认 | 不影响其他任务 | REPLACE |
| 校验 | 不存在 | 已保存 plan checksum | 错误列表/步骤高亮/可发布 | 422/403/correlationId | 连接/引用/权限/密级 | MISSING |
| 提交发布 | 不存在 | VALID、checksum 未变、write 权限 | revision/DAG/ACTIVE 状态 | 409/发布补偿 | 发布一致性 | MISSING |
| 启用 | 固定 disabled | ACTIVE revision + owned DAG | 已启用 | 409/外部失败 | 幂等 | FAKE |
| 暂停 | 固定 disabled | 已启用 owned DAG | 已暂停 | 补偿/重试提示 | 只作用于本任务 | FAKE |
| 立即运行 | 按任意 dagId | ACTIVE revision/plan checksum | executionId/PREPARING | 准入/并发/时间窗失败 | 双击幂等/越权 | PARTIAL |
| 重试 | 传无消费者字段 | 可重试失败实例 | 新 execution + 原实例关联 | 非法状态 409 | 冻结输入 | FAKE |
| 取消 | 不存在 | PREPARING/RUNNING | CANCEL_REQUESTED → 终态 | 外部超时待补偿 | 重复取消 | MISSING |
| 查看日志 | dbt 条件显示 | task+execution 权限 | 分页脱敏日志/correlationId | 尚未生成/过期 | 所有 connector | PARTIAL |
| 查看目标资产 | 不存在 | execution 有 destination.assetRef | 打开同一 dataset 资产详情 | 资产不可见/已删除/未解析 | 深链与越权 | MISSING |
| 当前质量证据 | 不存在 | execution 成功并配置质量验证 | workflow/run、触发/证据/结果三类状态 | 重试等待/耗尽/证据过期 | 新批次使旧 PASS 失效 | MISSING |
| 可信可用徽标 | 不存在 | `CURRENT/PASSED + ELIGIBLE` | 只读显示“可信可用” | 条件不满足时显示具体原因 | 派生矩阵 | MISSING |
| 刷新实例 | 手工刷新 | taskId | 保持筛选/选中项 | 失败不清旧数据 | 终态停止轮询 | PARTIAL |
| 导出历史 DSL | 不存在 | legacy graphDsl 存在 | 下载脱敏 JSON | 明确不可发布 | 无凭据/不可导入 | MISSING |

## 稳定测试标识

- `integration-flow-task-select`
- `integration-flow-source-step`
- `integration-flow-destination-step`
- `integration-flow-target-asset`
- `integration-flow-schedule-step`
- `integration-flow-quality-step`
- `integration-flow-quality-evidence`
- `integration-flow-asset-link`
- `integration-flow-topology`
- `integration-flow-save-draft`
- `integration-flow-validate`
- `integration-flow-admit`
- `integration-flow-schedule-enable`
- `integration-flow-schedule-pause`
- `integration-flow-run-now`
- `integration-flow-run-retry`
- `integration-flow-run-cancel`
- `integration-flow-run-log`
- `integration-flow-dirty-guard`

组件状态必须来自 task design/revision/execution 契约；不能用前端常量宣称后端不存在的能力。

## 客户文案收敛

| 旧文案 | 新文案 | 使用边界 |
|---|---|---|
| 质量策略 | 接入后质量验证 | 配置步骤与执行详情；强调触发时点而非内部实现 |
| 已验证 | 已通过质量验证 | 仅正式 quality run 为 `PASSED` 时使用 |
| 可信 | 可信可用 | 仅 `CURRENT/PASSED + ELIGIBLE` 派生条件成立时使用 |
| 质量门禁 | 接入后质量验证 | 当前 Sprint 采用 post-commit，不暗示发布前同步阻断 |

API 枚举、`qualityPolicyRef`、JSON 字段、规则 code 与数据库值保持不变；只收敛客户可见文案。
