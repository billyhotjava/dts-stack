# Sprint-83 集成验收计划

当前目录只定义未来验收口径，不包含完成证据。编码全部结束后集中执行，避免反复 E2E。

| ID | 旅程 | 必须证明 |
|---|---|---|
| IT-01 | 表示读模型契约 | 同一 model/implementation revision 下 logical/dbt/runtime provenance 与 checksum 可复现 |
| IT-02 | 高级模式三视图 | SQL、逻辑表/字段、依赖图共享同一上下文；DBT_MANAGED 技术结构只读 |
| IT-03 | SQL checkpoint | 草稿校验无副作用；commit 创建新 Implementation Revision；CAS 冲突不覆盖 |
| IT-04 | 外部 dbt ZIP 导入 | inspect→mapping→preview→apply→ModelSpec DRAFT→工作台深链 |
| IT-05 | 重复/漂移/部分失败 | SKIP/UPDATE/CONFLICT/BLOCKED、retry、服务重启恢复和前向撤销 |
| IT-06 | 发布/物化 | StageGate→ReleaseCandidate→DbtExecutionGateway→Airflow/dbt→relation evidence |
| IT-07 | 安全审计 | 恶意 ZIP、跨租户、权限、密级、脱敏、中央审计分类与 outbox 重试 |
| IT-08 | 旧入口退役 | 建模 UI 不调用 `/etl/dbt/run` 或共享 dbt 文件写；旧深链受控收敛 |

最终证据必须包含：测试命令及退出码、API 请求/响应摘要、PostgreSQL 行与 checksum、Airflow/dbt run 标识、Chrome95 四态截图、dts-admin 审计记录。占位说明不能作为 PASS。
