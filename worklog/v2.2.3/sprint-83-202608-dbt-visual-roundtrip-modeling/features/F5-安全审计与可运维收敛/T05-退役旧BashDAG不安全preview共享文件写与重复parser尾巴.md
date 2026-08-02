# T05：退役旧 Bash DAG、不安全 preview、共享文件写与重复 parser 尾巴

**优先级**：P2  
**状态**：DRAFT  
**依赖**：T04、全部待退役调用方迁移完成

## 目标

在可观测性证明 caller=0、替代路径已验收且回滚证据完整后，物理删除建模侧旧执行、不安全预览和重复解析尾巴，不保留长期双写、隐藏开关、第二运行控制面或第二预览安全边界。

## Contract-first

- **具名风险**：`R-DBT-LEGACY-DAG` 同时覆盖旧 generator/ready 入口与仓库/部署目录内的遗留 DAG 实体；勘察确认后者使用 mutable/privileged BashOperator，sync 无 Token 且 `|| true`。只有运行记录为 0、源码与部署副本均删除才算关闭。
- **具名风险**：`R-DBT-LEGACY-PREVIEW` 指 `GET /api/etl/dbt/preview`；它依赖当前 manifest relation 并返回原始行，缺少 model/revision/candidate/evidence pin、表级密级与列策略，不得继续服务建模 UI。
- **退役清单**：旧按标签 DAG 生成/ready 调用及遗留 DAG 实体、旧 `/api/etl/dbt/preview`、建模消费者对 `/etl/dbt/files` 写、`bin/dts-deploy`/`bin/dts-dbt-import`/配套旧包工具、共享 dbt Git 控制面，以及经删除矩阵确认无 owner 消费者的重复 parser。
- **删除门禁**：GitNexus caller=0、路由/API consumer=0、Airflow 最近使用量=0、canonical 替代路径通过、迁移/回滚/审计证据完整、源契约测试固定旧路径不可达。
- **保留边界**：若通用文件 API 或 reader 仍有非建模消费者，只删除建模调用与 owner 身份，不误删通用能力；旧 preview 即使有非建模消费者也不能原样保留，必须先迁移到同一 evidence/classification/masking 服务并封闭原端点。每个临时保留项必须有 owner、原因和退出时间。

## 验证

- [ ] 维护员无法再通过建模/readiness 路径生成或触发旧按标签 DAG；Release/Plan 共享 factory 不受影响。
- [ ] 新旧建模 UI/API 对 `/api/etl/dbt/preview` consumer=0；原端点已删除或封闭，任何保留消费者已改走同一 evidence/classification/masking 服务。
- [ ] 建模 UI/API 对共享 dbt 文件写和旧脚本 consumer=0。
- [ ] parser 删除前后 normalized projection、diagnostics、lineage、asset sync 契约一致。
- [ ] 回滚演练可以恢复 canonical 服务，不恢复已退役平行控制面。

## Definition of Done

- [ ] 不留长期双写、隐藏 feature flag、410 tombstone、旧 Bash 执行面、旧不安全 preview 或第二 parser；所有删除均有 caller=0 和回滚证据。
