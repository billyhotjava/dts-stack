# 页面能力矩阵

判定：`REAL` 真实闭环；`PARTIAL` 有实现但缺关键条件；`FAKE` 有控件但无业务语义；`MISSING` 必需能力不存在；`RETIRE` 从客户主流程退出。

| 页面/区域 | 用户意图 | 当前实现 | 目标 owner / 契约 | 当前判定 | Sprint 处理 |
|---|---|---|---|---|---|
| 数据集成流程入口 | 打开任务设计 | `/explore/etl/orchestration` | 保持路由，菜单/标题改“数据集成流程” | REAL（入口） | F1，不新增页面 |
| 任务选择 | 选择真实接入任务 | 只靠 `?taskId=` | task list + design GET | MISSING | F1/T01 |
| 新建 DAG | 新建业务任务 | 只重置画布 | 复用现有接入任务创建流程 | FAKE | 改为“新建接入任务” |
| 节点库/自由画布 | 自由配置流程 | React Flow + graphDsl | 无执行 owner | REAL（编辑）/ 非执行 | RETIRE 客户主流程；旧 DSL 只读导出 |
| 数据源配置 | 选择源与对象 | 画布自由文本字段 | typed design.source + 现有 connector schema | PARTIAL | F1/T01 |
| 目标与字段映射 | 选择目标、writeMode、mapping | 画布自由文本/任意配置 | typed design.destination/mappings | PARTIAL | F1/T01 |
| 目标资产身份 | 明确本次写入形成/更新哪个资产 | 运行期接入事件可观察资产，设计页无稳定 asset ref | `CatalogAssetType.DATASET + CatalogAssetKey`；design 返回 datasetId | PARTIAL | F1/T01、F3/T03 |
| 调度配置 | 手动或 Cron | 开始节点只存模式 | typed design.schedule | PARTIAL | F1/T01 |
| 接入后质量验证 | 数据提交后验证当前批次 | 校验节点存规则 JSON；后端已有 `dataset:<uuid>` post-commit workflow | 目标 dataset + `postIngestionQuality`；质量域 owner | PARTIAL / 文案失真 | F0/T02、F1/T01、F3/T03 |
| 自动拓扑 | 看设计和依赖 | 当前图独立于执行配置 | topology GET，从 draft/revision 生成 | MISSING | F1/T02 |
| 保存草稿 | 保存配置 | localStorage + 完整任务 PUT | design PUT + If-Match | PARTIAL / 高风险 | F1/T01 |
| 服务端校验 | 判断可否发布 | 无统一报告 | design/validate | MISSING | F1/T02 |
| 提交发布 | 激活可运行版本 | 无页面接线 | 既有 admit + expected plan checksum | MISSING（页面） | F2 |
| 启用/暂停 | 管理当前任务调度 | 按钮固定 disabled | task schedule enable/pause | FAKE | F2/T02 |
| 运行实例 | 看当前任务运行 | 枚举全部 Airflow DAG | task executions list | PARTIAL / 边界过宽 | F3/T01 |
| 立即运行 | 运行 ACTIVE 版本 | 按 dagId 通用触发 | task execute + idempotency | PARTIAL | F3/T02 |
| 重试 | 按原输入恢复 | 无消费者 retryRunId | task retry + sourceExecutionId | FAKE 语义 | F3/T02 |
| 取消 | 停止运行实例 | 无 | task execution cancel | MISSING | F3/T02 |
| 日志 | 定位失败 | 仅 dbt 条件且硬编码 task | task execution logs | PARTIAL | F3/T02 |
| 资产质量回链 | 从 execution 查看正式质量与目标资产 | execution 有质量 ID，页面未贯通；资产页只展示 latest run | execution↔workflow/run↔asset；当前证据新鲜度 | PARTIAL | F3/T01、F3/T03 |
| 可信可用标识 | 判断当前数据能否可信消费 | 无当前 execution 绑定；存在沿用旧通过结果风险 | `CURRENT/PASSED + ELIGIBLE` 派生，只读展示 | MISSING | F3/T03 |
| 回填/告警 | 处理当前任务运维 | 跳转但上下文断开 | taskId 深链；无能力时明确为运维入口 | PARTIAL | F3/F4 |
| 审计/历史 | 追溯版本与操作 | 动作基础存在，设计摘要不足 | existing strict audit + revision history | PARTIAL | F4/T01 |

## 路由与信息架构

- 不新增菜单或平行工作台。
- 兼容路由保持不变，客户可见菜单、面包屑和页头统一为“数据集成流程”。
- 业务深链只使用 `taskId` 与 `executionId`；dagId 只在有权限的诊断信息中出现。
- 多任务 DAG 若未来通过需求门槛，应另做 capability 评审，不能重新把自由画布直接接成执行入口。
