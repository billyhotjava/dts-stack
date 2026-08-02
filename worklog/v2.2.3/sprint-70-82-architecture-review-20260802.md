# Sprint 70–82 重构复核（校正版）

日期：2026-08-02

范围：Sprint 70–82 的当前源码与已有验收记录。Sprint-83 尚在完善，不纳入结论、依赖或整改计划。

## 总体判断

重构方向正确，旧建模运行面物理退役、canonical 建模链、耐久审计消息、pairwise service token 和统一资产身份均已形成真实工程约束。当前主要风险不是“又造了一套平行后端”，而是 canonical 能力尚未形成可交付的前端闭环，旧 ETL 兼容面仍保留了控制动作和失败吞没行为，真实环境终验债尚未统一收口。

## 已核验成立

- 旧 semantic/vNext/SqlModel/BusinessObject 运行面已物理删除，未保留 410/tombstone 双入口。
- canonical 建模主链已收敛为 WarehousePlan → ModelSpec v2 → StageGate → Lifecycle → Candidate → Materialization → Gateway。
- 审计 outbox、死信、payload 脱敏、pairwise token 与服务端精确路径白名单均已落地。
- dbt profile 明文凭据已出库；旧建模路由为真实重定向；新建模页面均低于 800 行。

## 校正后的问题清单

### P0-A：旧 ETL/Airflow 兼容面需立即硬化

原 review 将其描述为“鉴权更弱且可成功执行的旁路”不够准确：

- 旧 DAG 回调只带 X-DTS-Service、不带 token，按当前过滤器实际会被拒绝；
- 真正风险是回调失败被 || true 吞掉，同时 sync_models 使用 all_done，造成执行与资产同步状态不一致；
- /api/etl/airflow/jobs/{dagId}/trigger、dbt 文件写入和 Git 变更此前只要求登录，没有动作级维护者权限。

结论：这是“可达的旧控制面 + 被掩盖的失败”，不是一条已经绕过 pairwise token 成功回写的链路。优先级仍为 P0。

### P0-B：建模域尚未形成可交付闭环

src/pages/data-modeling/ 未消费 canonical API 属实，但新 UI 的失败关闭是有意的安全边界，不应表述为新的平行实现或隐性旁路。交付事实仍然是：后端能力存在、前端仅有静态交互壳，客户无法完成真实建模。

整改目标应是最小闭环接线与真实环境验收，而不是继续扩大设计面。首个闭环至少覆盖规划、ModelSpec CRUD、stage gate，并明确区分“源码存在、自动化测试通过、已部署、浏览器人工验收”四种状态。

### P0-C：验收债成立，但不能表述为“完全没有真实测试”

Sprint 76/77/79/81/82 的终验欠条仍未全部关闭；同时部分数据库集成测试、容器验证和浏览器验证已经执行。准确结论是：局部真实验证存在，但“部署 → 登录 → 建模 → 物化 → 资产登记 → 审计落账”的统一目标环境证据尚未形成。

下一个新增功能批次前应安排稳定化验收批次，逐张关闭欠条或转为带负责人、环境和复现证据的缺陷。

### P1：模块物理边界仍需演进

service/modeling 平铺目录与多个超大类属实。该项是维护性风险，不应与当前 P0 接线和旧兼容面硬化并行做大规模拆包；应按自然子域逐批迁移，并用现有 ArchUnit 规则守住单向依赖。

### P1：Candidate 发布权限问题已闭合

原 review 的“Candidate 发布链未消费 AccessChecker.canPerform”已过时。当前发布准入通过 CandidatePublicationAdmissionService 进入 fail-closed 权限适配器并调用 AccessChecker.canPerform。不得以该旧结论重复立项。

仍需单独审计的是物化启动动作的 source/target 权限覆盖，不能把这一窄问题扩大为整个发布链未授权。

### P1/P2：安全与维护债继续保留

- TLS keystore 历史泄露处置、Keycloak 暴力破解保护、Alertmanager 与单点部署仍是安全/可用性门槛。
- 审计动作字典双份维护应改为单一来源构建期生成。
- 存量千行页面应建立渐进拆分账本；800 行门禁不能只约束新页面。
- 工作区文件数量属于瞬时快照，不作为架构结论；未归档改动应按实际 diff 归属 Sprint/工作项后再提交。

## 执行顺序

1. 硬化旧 ETL/Airflow 兼容面：pairwise token、失败显性化、精确服务白名单、写/触发动作权限。
2. 接通 canonical 最小建模闭环，并在目标环境完成浏览器验收。
3. 插入稳定化验收批次，统一关闭 Sprint 76/77/79/81/82 的终验债。
4. 再处理建模包拆分、TLS/告警/高可用和存量大页面等 P1/P2。

## 第一批优化状态

本批仅处理第 1 项，不改 canonical 发布/物化主链：

- 旧 DAG 回调改用 DTS_AIRFLOW_TO_PLATFORM_TOKEN，缺失即失败；
- 回调携带 X-DTS-Service-Token，移除 || true；
- sync_models 仅在 dbt 成功后执行；
- Airflow 只获准 POST /api/etl/dbt/models/sync 精确路径；
- ETL 配置、source refresh、DAG 生成、带 DAG 写副作用的状态/运行查询、Airflow trigger、dbt 文件写入和 Git commit/revert 统一要求 INFRA_MAINTAINERS；
- dbt sync 仅允许维护者或精确的 service:dts-airflow 服务身份。

状态：代码已完成，30 个聚焦回归测试通过；尚未部署，真实 Airflow/dbt/Platform 联动验收待目标环境执行。
