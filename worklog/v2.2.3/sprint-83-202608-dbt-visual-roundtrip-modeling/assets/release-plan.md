# 发布安全计划（Gate G3）

**变更类型**：API + schema expand/migrate + 能力替换 + dbt runtime  
**风险等级**：高  
**当前状态**：READY_FOR_IMPLEMENTATION；回滚演练未完成前保持 GAP

## 1. 发布不变量

1. ModelSpec/Implementation、ReleaseCandidate/Materialization、Catalog 投影分别保持唯一事实 owner；不建平行台账。
2. `PUBLISHED` 表示治理发布，`servingRef` 表示物理可消费，两者解耦；发布 r2 时允许 `latestPublishedRef=r2, servingRef=r1`。
3. 新 revision 必须构建到版本化 shadow relation；relation/质量证据通过后才原子切换 serving indirection。失败或 stale 的 shadow relation 隔离/清理，不得污染 r1。
4. H83-01 只产生 runtime candidate；F0/T05 是唯一认证登记 owner。默认 `NOT_CERTIFIED`，不能按 tag 或 adapter 顶层版本推断认证。
5. S5 Contract 删除与本轮 Expand/Migrate 分离；caller 不为 0 时禁止物理删除旧路径。
6. import attempt/item/summary 只有一套 canonical 状态代数；legacy `SUCCEEDED/REPLAYED` 只允许在迁移前存在，`BeginDisposition.REPLAY` 永远只是请求级幂等响应元数据。

## 2. Expand / Migrate / Contract

| 阶段 | 内容 | 本次是否包含 | 回滚边界 |
|---|---|---:|---|
| Expand | 新增表示读模型、runtime certification、Catalog 双指针/版本 CAS、import 结果/审计所需可空结构与索引；`inspectionProof` 复用平台托管签名能力和既有 import run，不新增 inspect 表或独立 secret | 是 | 旧代码忽略新表/列；回滚代码不删新数据 |
| Migrate | 以稳定 `CatalogAssetType + CatalogAssetKey` 初始化逻辑模型投影；仅从已验证发布/物化证据生成指针；对存量 import 先执行 item `REPLAYED→SKIPPED`、attempt `SUCCEEDED→SUCCESS`，再从 item 明细重算 canonical summary | 是 | 必须先 dry-run；异常恒等式立即停止并形成修复清单。import 状态迁移是 forward-only，完成后不得回滚到只识别 legacy 状态的 reader |
| Contract | 删除旧 dbt preview/files/DAG/parser 旁路与旧表/列 | 独立 S5 发布 | 仅 caller=0、兼容窗口、备份和回滚演练全部通过后执行 |

新增唯一约束/索引前先查询冲突；大表索引采用 PostgreSQL 非阻塞策略。任何 `NOT NULL` 均先完成回填与空值断言。Liquibase rollback 只撤销尚未被新版本写入的 expand 对象；有新事实写入后使用前向修复，禁止盲删审计、ModelSpec、Implementation、candidate 或 evidence。

## 3. 兼容与消费方

| 消费方 | 兼容策略 |
|---|---|
| 建模工作台/逆向向导 | 后端新增契约先发布；前端只在 API 可用后启用，不新增菜单/模型清单 |
| Sprint-81 StageGate/Lifecycle | 复用现有接口；优先外围适配和回归测试，避免修改 HIGH 风险核心符号 |
| Catalog/资产门户 | 复用稳定资产键；双指针走窄读端口，禁止把逻辑版本塞入物理 dataset tags |
| Airflow/dbt | 只经 `DbtExecutionGateway`；未认证 profile 在生成文件/提交 DAG 前 fail-closed |
| dts-admin 审计 | 审计动作字典先于 platform 新事件部署，避免“未分类” |
| 旧 `/api/etl/dbt/preview` | S3 先封闭普通用户/建模角色旁路；S5 caller=0 后物理删除或迁入同一安全服务 |

## 4. 分级开关与部署顺序

默认关闭 source-only、physical-preview candidate 和 runtime materialization；artifact-rich inspect/import 可独立开放。部署顺序：

1. 备份当前 schema、镜像 digest、runtime `NOT_CERTIFIED` 状态和 legacy caller 清单。
2. 构建 H83 候选但不切流；归档 image/base digest 与锁文件。
3. 先部署 dts-admin 审计字典 migration，再部署 dts-platform expand migration；确认 `inspectionProof` 使用的平台托管 signer 可用，但不创建新 secret/inspect 表。
4. 关闭 import 新入口，备份并 dry-run 存量 attempt/item/summary；任何恒等式异常先修复，不猜测迁移。
5. 在维护窗口执行 `REPLAYED→SKIPPED`、`SUCCEEDED→SUCCESS` 和 summary 重算，部署只输出 canonical 状态的新 dts-platform；断言 legacy 行/JSON/审计投影为 0 后才重新开放 import。
6. 部署 dts-platform-webapp，先开放 representation + artifact-rich ZIP；确认旧客户端/旧 serving 仍正常。
7. F0/T05 认证通过后才开放 S3 materialization；先单租户/单计划灰度。
8. 观察审计、失败率、stale、serving CAS 后扩大范围。
9. S5 在独立发布中完成 Contract 删除。

## 5. 回滚

- **前端**：回滚 webapp digest；新后端保持向后兼容。
- **platform/admin**：canonical import 状态迁移前可回滚服务 digest并关闭新入口；迁移后只允许关闭 import 入口并以前向修复恢复，禁止启动只识别 `SUCCEEDED/REPLAYED` 的旧 import reader。始终保留 expand schema、导入结果和审计，避免数据丢失。
- **runtime**：先切回 `NOT_CERTIFIED`，停止新 dispatch，再恢复旧 image digest；旧 digest 也不得自动获得认证。
- **serving**：CAS 恢复最近一次仍有有效 evidence 的 servingRef；不得把失败/stale candidate 设为 serving。
- **import**：已成功项不物理撤销；按资格创建前向修订。retry 只重跑失败且 retryable 的项。
- **不可逆项**：外部 Catalog 已同步状态和已创建物理 relation 只能通过补偿/前向修复处理；开闸前必须有 correlationId 和 evidence checksum。

## 6. 演练门槛

- [ ] clean-db expand migration + 旧版本启动。
- [ ] populated-db 状态 dry-run 与前向迁移：legacy item/attempt/summary 全部归一，恒等式异常可停止且有审计清单。
- [ ] canonical 状态迁移后关闭 import 入口并演练前向修复；证明不会误启只识别 legacy 状态的旧 import reader，旧 serving 仍可消费。
- [ ] r2 shadow 构建失败/stale，r1 数据和 servingRef 不变。
- [ ] runtime digest 漂移后 `DBT_RUNTIME_NOT_CERTIFIED` 且 0 Airflow submit。
- [ ] 旧 preview 旁路对普通建模用户返回 403/404 或统一安全结果。
- [ ] S5 caller=0 与物理删除 rollback rehearsal。

演练证据统一写入 `it/`；未全部通过时 G3 只能为 GAP，不能发布。
