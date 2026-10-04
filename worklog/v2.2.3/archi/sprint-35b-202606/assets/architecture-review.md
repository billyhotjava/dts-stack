# dts-metrics 架构评审底稿（2026-06-13）

基于对 `source/dts-metrics`（后端 3622 行主代码 / 3294 行测试）与 `source/dts-metrics-webapp`（2934 行）及 Sprint-35 设计文档的静态阅读。结论：**方向对、值得继续、不必推倒；但落地已背离自己的契约。**

## 保住的好决策（不要动）

- 服务切分干净：metrics = 指标语义事实源；platform = 控制面（IAM/资产/RLS/审批/dbt 发布）。`PlatformContractClient` 严格走 service-auth + internal API，不直读 platform 表、不碰 dbt 文件系统。
- DWS/ADS 默认入口 + DWD 受控高级建模，是有产品判断力的分层决策。
- fail-closed：platform 不可用拒绝生成 artifact（503 `platform_contract_unavailable`）。
- 候选 artifact 模式：生成 `candidate://` 引用 + dbt SQL/schema.yml，绝不直写生产 dbt 目录。
- SQL 注入三层防御：黑名单正则 + 函数白名单编译器 + 标识符强制 quote + 字面量转义（`MetricModelLifecycleService` L446-520）。

## 6 个缺陷（按严重度）

### 🔴 #1 被指定为"事实源"的服务零持久化 —— 最严重
- 全模块无 `@Entity`/`@Repository`/datasource（postgresql 仅 test scope，且重复声明两次）。
- model state / version / rollback 在 `MetricModelLifecycleService` L25-27 的三个 `ConcurrentHashMap`；graph draft 在 `MetricGraphDraftService` L89 的 `ConcurrentHashMap`。
- 后果：重启即丢；多实例时 publish 在 Pod A、rollback 路由到 Pod B → "model not found"；当前架构上只能单实例；"事实源"名不副实。

### 🟠 #2 新旧两条 artifact 链路安全强度不对等
| 链路 | 入口 | 权限 | RLS/masking | 审计 |
|------|------|:--:|:--:|:--:|
| metric-pack（老，Sprint-31b） | `MetricArtifactGenerationService` | ✅ requirePreviewPermission | ✅ 注入 WHERE | ✅ recordPolicyInjection |
| graph lifecycle（新，Sprint-35 主路径） | `MetricModelLifecycleService` | ❌ | ❌ | ❌ |
- 新链路 `generateArtifacts` 的 policySource 硬编码 `"platform-policy-required"`、predicateHash 对空列表求哈希（L41-42），是占位符。主路径走了安全更薄的管子。

### 🟠 #3 发布无一致性/saga
- `publish()`（L120-156）= submitDbtRelease（远程）→ appendModelVersion（本地内存），线性无补偿。
- BI Dataset register / lineage register / audit-events 三个 internal 端点契约定义了、主代码零调用点（仅老 pack 链路调 recordPolicyInjection）。
- 后果：metrics 标 PUBLISHED 的模型，platform/BI/血缘侧可能没注册 → 闭环断裂 + 跨服务状态分叉。

### 🟡 #4 领域模型全是 `Map<String,Object>`
- graph/artifacts/model state/validation 全是无类型 Map，靠 text()/firstText()/object()/maps() 手写强转 + 满屏 `@SuppressWarnings("unchecked")`。契约纸上精确、代码零编译期保障；违反项目 record-DTO 规范；放大其他缺陷修复成本。

### 🟡 #5 platform 同步依赖零韧性
- `RestClientConfiguration` 只 `builder.build()`，无超时/重试/熔断。platform 一次抖动让每个请求挂到默认超时。fail-closed 有意（对），无界等待不对。

### 🟡 #6 API 契约漂移
- 契约文档说走 `/internal/metrics/visual-assets` + `/asset-contracts/batch`；实际 `MetricVisualAssetResource` L70 调通用 `/catalog/assets-v2`，且无逐资产 checkPermission，permissionDecision 富化不完整。

## 修复顺序（驱动 Sprint-35b 的 Feature 划分）

1. 持久化（#1）→ F1
2. 安全链路统一 + 审计收口（#2,#3审计）→ F2
3. 跨服务一致性（#3）→ F3
4. RestClient 超时 + 契约对齐（#5,#6，低成本先做）→ F4
5. 领域类型化（#4）→ F5
6. IT 准入与验收（全部）→ F6

**在 F1/F2/F3 完成前不应按"接近可发布"推进 Sprint-35 F5 验收。**
