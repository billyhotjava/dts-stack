# 缺口登记表

| ID | 优先级 | 缺口 | 直接风险 | 完成判据 | 归属 |
|---|---|---|---|---|---|
| DIF-001 | P0 | 未证明真实流程需要可编辑 DAG | 按已有 UI 继续过度建设 | 全量活跃任务和客户流程分类，得到 PASS/NOT_JUSTIFIED | F0/T01 |
| DIF-002 | P0 | 页面没有可靠 taskId 选择入口 | 本地状态无法成为业务任务 | 任务选择/创建/深链均有明确 taskId | F1/T01 |
| DIF-003 | P0 | graphDsl 与任务配置形成双事实源 | 所见非所运行 | 任务 design 为唯一事实；拓扑从其单向生成 | F1/F2 |
| DIF-004 | P0 | 全局 localStorage 与 null 加载存在串任务风险 | 把 A 任务状态保存到 B | 按 taskId/base checksum 隔离或移除；切换测试 | F1/T01 |
| DIF-005 | P0 | 保存完整任务 DTO，无条件更新 | 丢失更新、覆盖治理/状态字段 | design PUT + If-Match + 409 | F1/T01 |
| DIF-006 | P0 | 画布配置自由文本且默认字段不一致 | 引用漂移、connector 不可执行 | 复用 typed source/destination/mapping schema | F1/T01 |
| DIF-007 | P0 | 无统一服务端设计校验 | 非法、越权配置进入发布 | 连接/引用/mapping/Cron/权限/密级校验 | F1/T02 |
| DIF-008 | P0 | 无 draft/active/history 拓扑投影 | 无法可视化证明版本一致 | deterministic topology + plan checksum | F1/T02、F2/T01 |
| DIF-009 | P0 | graphDsl 没有执行消费者 | 补编译器会增加第二条执行链 | 不建 DSL compiler；legacy 只读/导出 | F1/F4 |
| DIF-010 | P0 | revision/DAG/execution 缺 plan identity | 发布内容与运行版本无法交叉证明 | plan checksum 贯通五层对象 | F2/T01 |
| DIF-011 | P0 | 发布不校验所见配置版本 | 并发修改后发布错误内容 | admit expected plan checksum，冲突失败 | F2/T02 |
| DIF-012 | P0 | 启用/暂停是假控件 | 无法管理任务生命周期 | task-scoped 幂等命令 + owned DAG | F2/T02 |
| DIF-013 | P0 | 运行页直接枚举/触发通用 DAG | 权限、归属和版本不可证明 | task-scoped query/commands | F3/T01 |
| DIF-014 | P0 | 重跑只有无人消费 retryRunId | “重试”是无关联新跑 | sourceExecutionId + 冻结输入 + 状态门禁 | F3/T02 |
| DIF-015 | P0 | 无取消状态机 | 长任务不能受控停止 | CANCEL_REQUESTED/CANCELLED/补偿 | F3/T02 |
| DIF-016 | P0 | 普通 ingestion DAG 日志不可用 | 失败无法定位 | executionId 分页日志覆盖所有 connector | F3/T02 |
| DIF-017 | P0 | 旧 DSL 没有兼容/退役策略 | 数据丢失或重新冒充执行事实 | 可读/导出/不可发布；不自动删除 | F4/T01 |
| DIF-018 | P0 | 设计变化摘要与敏感字段脱敏不足 | 审计缺失或正文泄露 | 记录摘要/checksum；日志/toString 脱敏 | F4/T01 |
| DIF-019 | P0 | 登录态、金丝雀、Chrome 95 基线缺失 | 无法证明客户可用 | 三角色、三配置样本、Chrome 95 | F0/F4 |
| DIF-020 | P0 | 31 个 ingestion DAG 未匹配当前标识 | 重复调度、误删、错误接管 | 逐项分类，默认 KEEP，有审批才变更 | F0/F4 |
| DIF-021 | P1 | 88 次执行中 81 次失败 | 验收样本不稳定、新旧故障难分 | 抽样根因并选连续成功金丝雀 | F0/F4 |
| DIF-022 | P1 | 通用 DAG 列表外部 N+1 | DAG 增长后延迟与 Airflow 压力 | task 分页，无逐 DAG latest 请求 | F3/T01 |
| DIF-023 | P1 | 外部 run 关联尽力写且去重不足 | 重复记录或丢关联 | 强唯一身份 + 补偿扫描 | F4/T01 |
| DIF-024 | P0 | `qualityPolicyRef` 已确认是 `dataset:<uuid>`，但设计页未保证与目标资产相同 | 跨资产触发或向用户虚构独立“策略” | design 返回 canonical assetRef；服务端校验同一 dataset/权限/有效规则 | F0/T02、F1/T01 |
| DIF-025 | P2 | ELT 转换制品契约缺失 | 内联代码与版本漂移 | 另立 capability：仅引用已发布制品 | 暂缓 |
| DIF-026 | P2 | 多作业依赖、循环、事件、回滚无 owner | 通用 workflow 范围失控 | 需求门槛通过后另立架构 Sprint | 暂缓 |
| DIF-027 | P0 | 设计时目标端未稳定解析为 canonical 数据资产 | 接入、质量与资产详情使用不同身份 | `CatalogAssetType.DATASET + CatalogAssetKey` 唯一；assetRef 随 revision/execution 冻结 | F1/T01、F2/T01、F3/T03 |
| DIF-028 | P0 | 资产质量投影读取数据集 latest run，未证明属于当前 ingestion execution | 旧批次 `PASSED` 冒充当前可信 | 新 execution 后证据进入 PENDING；仅同 execution 的 CURRENT/PASSED 可派生可信 | F3/T03 |
| DIF-029 | P1 | execution、quality workflow/run 与资产详情缺少双向定位 | 失败后无法判断触发、执行还是投影问题 | execution/asset 页面均可按权限查看关联 ID、状态、时间与 correlationId | F3/T01、F3/T03 |
| DIF-030 | P0 | post-ingestion trusted trigger 的机器身份与防伪造验收未进入本 Sprint | 普通用户或伪造 Header 绕过质量启动授权、任意触发资产质量 | 仅已认证 dts-ingestion 身份可调用；资产取自冻结 execution；用户/伪造 Header/重放测试 fail closed | F0/T02、F3/T03、F4/T02 |

## 暂缓项规则

- P2 项不得通过重新显示旧画布控件提前进入产品。
- 任意转换能力必须先定义制品身份、输入输出数据集、版本、血缘、资源、安全和恢复语义。
- 多作业 DAG 必须引用已发布任务/制品 ID，不允许节点复制各领域配置或执行器。
