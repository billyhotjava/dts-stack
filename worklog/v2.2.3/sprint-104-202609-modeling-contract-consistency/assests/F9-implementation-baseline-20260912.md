# F9 实施基线与统一验证入口

基线提交：`2e91f12f5b3709f9892dff4d88d4689b1dabfc02`；实施分支 `v2.2.3`。2026-09-12 用户授权完成全部 Task 编码后统一测试。本记录中的实现说明不等于测试、构建、部署或业务验收通过。

## T01 冻结决定

- 当前组织目录 4 个节点均无 `dept_code`；根节点 `1151`，部门节点 `1152/1153/1154`。有部门码时使用该码，无部门码时使用已验证的非根组织节点 ID 字符串。不得使用名称、后缀、祖先关系或任意数字猜测匹配。上线前用只读预检脚本重新确认映射。
- 测试环境目前 1 个部门公共层，部门 `1153`、DRAFT；模型 FACT 3、SUMMARY 1、ADS 0；门户会话 19，缺部门 0。没有把这组证据推广到客户现场。
- 稳定用户 ID 来自成功认证的 Keycloak 用户主体；新增 `portal_sessions.directory_user_id`，旧会话必须重新登录。全产品现有 `sub/username` 语义保留，仅建模请求及用户任务的受控执行上下文使用稳定 ID。
- 当前身份由 dts-admin 严格目录入口读取：账号启用状态、有效 realm 角色、组织归属、人员密级。稳定身份不按用户名回退。账号/角色实时读取 Keycloak；复用当前人员档案作为部门/人员生命周期的优先事实，只有确实无档案时沿用 Keycloak 属性，档案查询故障返回 503，不使用旧快照放行。平台单次 HTTP 截止 2 秒，不重试、不缓存放行结果。目录故障 503，角色失效 403，旧会话/失效账号 401。
- 默认公共层由 `modeling-context:dept:{department}:v1` 和租户/部门事务锁唯一化。读取不创建，首次保存和初始化同事务；已有异常生命周期不释放固定键。旧部门计划迁移必须经过唯一性及归属预检。
- ADS 归属使用稳定创建人 ID。USER/ROLE 只共享本部门 EDITOR；负责人、本部门领导及院级保留管理权。离职、调部门、角色撤销均按当前目录事实拒绝。转换出 ADS 撤销有效授权，转换回来保留原负责人，不复活授权。
- 已读取的测试环境没有需保留的跨部门引用白名单；客户现场白名单/历史降密级 Q5 均为 **NOT_RUN**。不进行历史数据恢复。

## 入口与权威事实

| 范围 | 实现入口 | 权威事实 / 防线 |
|---|---|---|
| 身份与建模准入 | ModelingIdentityFilter / ModelingDirectoryResource | 稳定 ID；启用状态、有效角色、当前部门；菜单另取交集 |
| 部门公共层 | ModelingContextInitializationService / WarehousePlanApplicationService | 统一固定键、事务锁、只读查询、归属不可变 |
| 模型与 ADS | ModelSpecAccessService / ModelSpecPlanWriteAccessPort | 单次批量范围、模型行锁、当前 ACL；部门过滤先于分页计数 |
| 定义 / 加工 / 草稿 | ModelSpecApplicationService / ModelLifecycleService / ModelAuthoringDraftService / DbtImplementationDraftService | 每条写入口及幂等重放重新校验对象编辑权 |
| 直接模型引用 | ModelSpecApplicationService / ModelImplementationInputPolicy / ModelImplementationDependencyReadAdapter | 不可见 404；可见跨部门 422；实际依赖闭包同样校验 |
| 目录表别名 | ModelingSourceScopeGuard | catalog_asset_producer_ref 的 MODELING 生产者、目录标准资产键；生产者不可解析时拒绝 |
| 连接表别名 | ModelingSourceScopeGuard | 标准资产键与 modeling_physical_relation_observation 的受治理物理关系 |
| dbt 节点别名 | ModelingSourceScopeGuard | modeling_model_implementation 的 project_key/dbt_unique_id 与模型归属 |
| SQL / dbt 文件 | ModelingSqlReadSetGuard / QualitySqlScopeValidator | 复用 SQL AST 安全遍历，静态 dbt 引用另由现有依赖校验；无法完整确认的 SQL 拒绝执行 |
| 导入 | ModelSpecImportPreviewService + 同一模型写入口 | 来源绑定和模型引用共用上述边界 |
| 批量物化 / 发布 | ModelMaterializationPlanService / ModelReleaseCandidateService / CandidatePublicationCommitService | 对实际 BUILD / WRITE / EXECUTE 集合全量授权；已发布只读上游不要求 EDITOR |
| 用户后台任务 | ModelingExecutionAuthorization | 使用候选不可变命令版本的发起人，或运行行封存 initiator_id；启动和重试重新解析目录与范围 |
| 系统定时运行 | PlanOperationalRunService | 内部认证入口已部署绑定；版本/范围 checksum 固定，仅运行已有范围，不授予定义或发布权 |
| 资产密级 | CatalogClassificationEditService / CatalogAssetPortalService | advisory lock → 数据行 / snapshot；保留 CAS；只升不降；失败审计独立事务 |
| 前端 | 现有 ModelingWorkbenchPage / ModelAccessDrawer | 部门选择、负责人、编辑共享、服务端权限；禁用跨部门入口；中文提示 |
| 相似模型 | ModelingAccessResource.similar / confirmSimilarModel | 同部门同类型、非空业务过程/来源/粒度；最多 10 条；普通失败可跳过，401/403/404 不忽略 |

## T07 大屏消费链只读结论

当前 `ScreenPermissionService` 优先使用平台 asset_grant，analytics_screen_access 为迁移期只读回退；不能继续把它描述成单一本地 ACL 来源。OWNER/MANAGER 的基础管理权不绕过人员密级；仅 VIEWER 的 level_override 参与越级查看，并携带审计标记。`AnalyticsConsumerClassificationService.requireCurrentScreen` 对未解析上游和失效密级证据拒绝消费。

F9 没有修改 analytics 大屏 ACL、密级或越级逻辑。正式 S12 仍需以真实登录账号验证消费与越级链，源代码核对不替代页面验收。

## 统一验证计划

1. 开发目录完成一次集中静态检查与 review，校验 GitNexus 变更范围，只提交 F9 文件。
2. commit/push 后在 `/data/dts-stack` 确認分支、工作区、`git pull --ff-only` 和一致 SHA；执行 Maven 定向权限/事务/来源/身份回归及前端 Vitest、正式 Chrome 95 目标构建。
3. 数据库测试使用隔离 PostgreSQL 容器，覆盖并发授权、撤销等待、整批拒绝与失败审计；只读预检脚本不修改现有业务数据。
4. 按验收矩阵记录源码测试、正式交付包、部署、真实页面四类证据。任何未执行项保留 NOT_RUN，不据此把 F9 或 T09 标记 DONE。
5. 回退保留稳定身份、负责人及审计扩展；旧二进制无法满足 F9 授权契约时先关闭建模入口，恢复兼容版本后再开放。不得回滚密级、移交模型归属或删除现有数据。

验证结果见 [统一测试记录](../it/F9-统一测试记录-20260912.md) 与 [验收报告](F9-acceptance-20260912.md)。
