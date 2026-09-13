# Sprint-28: 服务间鉴权方案 B 中期落地

**时间**: 2026-05
**状态**: DONE(代码 + 文档闭环;真链路 E2E 留运维 IT)
**类型**: Architecture / Security(dts-platform + dts-ingestion + dts-analytics)
**目标**: 把 `dts.admin.*` 这一组承担"出站客户端配置"和"入站服务认证"双重语义的 properties bean 在概念与配置层面彻底拆开,改为每对调用独立 secret + filter 强校验,关闭"白名单即权限"越权面;通过完整的环境变量 fallback 实现零停机切换。

## 背景

来源于 `dts-platform-webapp` 新建数据连接后启动入湖任务出现 `获取平台数据源失败: 403` 的排查。定位结论:

1. **不是单次配置错误,而是设计债**。`DtsAdminProperties`(`dts.admin.*` prefix) 在 platform 同时承载两件互不相干的事:
   - **出站客户端**:platform → admin 的调用方配置(被 `AdminGatewayHeaders`、`AdminGatewayTransport`、各 `AdminXxxGateway` 使用)
   - **入站鉴权**:任何内部服务调 platform 时的白名单 + 共享 secret(被 `ServiceDependencyAuthenticationFilter` 与 `InfraDataSourceResource.runtimeDetail` 使用)
2. **凭据共用**:整个集群一把 `DTS_ADMIN_SERVICE_TOKEN`,任何一处泄露全网失守。
3. **白名单即权限**:`ServiceDependencyAuthenticationFilter` 只看 `X-DTS-Service` 是否在白名单就注入 `OP_ADMIN`。`runtime-detail` 端点额外补了 token 校验所以暂时安全,但其他仅依赖 `OP_ADMIN` 的端点存在隐蔽越权面。

参考资料:
- 排查记录: 与本 Sprint 同期的对话上下文
- 现有代码:
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/DtsAdminProperties.java`
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java`
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/infra/InfraDataSourceResource.java:138`
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/infra/PlatformInfraClient.java`
  - `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/PlatformInfraClient.java`

## 架构原则

- **概念分离**:出站(我作为客户端调谁)与入站(谁能调我)在 properties bean、env 命名、代码注入路径上完全独立。
- **每对独立 secret**:`trustedServices` 改为 `Map<service, token>`,失陷一对只影响一对,可独立轮换。
- **强校验**:仅靠 `X-DTS-Service` header 不再获得任何权限;必须 `X-DTS-Service-Token` 与对应条目相等(或 svc_token 表命中)。
- **零停机切换**:旧 env 名以 fallback 形式保留一个版本周期,部署文档显式列出迁移路径。
- **不动归属决策**:本 Sprint 不迁 svc_token 表归属(留给长期 ADR),不动 Keycloak `client_credentials` 集成。

## 范围

| 服务 | 角色 | 本 Sprint 改动 |
|------|------|----------------|
| dts-platform | 入站接收方 + 出站调 admin | properties 拆分、filter 强校验、InfraDataSourceResource 适配、审计 |
| dts-ingestion | 出站调 platform | 改注入 OutboundPlatformProperties,env 改名 + fallback |
| dts-analytics | 出站调 platform + 出站调 admin | 同上,且兼顾 Admin 出站头 |
| dts-admin | 不在本 Sprint 范围 | admin 自身的入站审计 token(`auditing.ingest.service-tokens`)语义本来就独立,不动 |
| dts-airflow-om | 不在本 Sprint 范围 | python 服务,白名单保留一席之地,未来再补 |

## Feature 列表

| Feature | 优先级 | 状态 | 目标 |
|---------|--------|------|------|
| F1-platform-properties-split | P0 | DONE | 拆 DtsAdminProperties 为 outbound/inbound 两个 bean,旧 bean 桥接保留兼容 |
| F2-platform-inbound-per-pair-secret | P0 | DONE | trustedServices 改为 Map,每对调用独立 secret |
| F3-platform-filter-strict-auth | P0 | DONE | filter 强校验,关闭"白名单即权限"越权面 |
| F4-ingestion-outbound-rename | P0 | DONE | ingestion 出站 properties 改名 + 旧 env fallback |
| F5-analytics-outbound-rename | P0 | DONE | analytics 出站 properties 改名(仅 platform 链路;admin 留 Sprint-29)+ 旧 env fallback |
| F6-auth-audit-logging | P1 | DONE | filter 拒绝路径 LOG.warn 结构化日志 + InfraDataSourceResource SERVICE_AUTH_DENIED 端点级审计 |
| F7-compat-matrix-and-it | P0 | DONE | 兼容矩阵 + 部署 runbook + smoke 脚本(真链路 E2E 留运维 IT) |

**统计**: READY=0, IN_PROGRESS=0, DONE=7, BLOCKED=0

## 非目标

- **不**把 svc_token 颁发能力迁到 dts-admin(留给长期 ADR)。
- **不**接入 Keycloak `client_credentials` 作为颁发可信源(长期路线)。
- **不**重写 svc_token 表结构或 `SvcTokenAuthService` 的内部逻辑。
- **不**强制下线 `DTS_ADMIN_SERVICE_TOKEN`(本 Sprint 仅作为 fallback,下线节奏由后续 sprint 决定)。
- **不**在本 Sprint 改 dts-admin 模块的代码。

## 验收标准

- [ ] platform 启动后 `DtsAdminProperties` 标注 `@Deprecated`,实际注入与 filter 全部走新两个 bean
- [ ] platform `application.yml` 默认提供 `dts.platform.inbound.service-auth.trusted-services` Map 配置,各服务可独立配置 token
- [ ] `ServiceDependencyAuthenticationFilter` 默认 `legacy-header-only-mode=false`,仅 token 校验通过才注入 OP_ADMIN
- [ ] dts-ingestion `PlatformInfraClient` 注入新 OutboundPlatformProperties,旧 env(`DTS_ADMIN_SERVICE_TOKEN`/`DTS_PLATFORM_SERVICE_TOKEN`)仍可工作
- [ ] dts-analytics 三个 client 全部走新 properties,旧 env 仍可工作
- [ ] filter 拒绝路径结构化日志可观测,审计事件 `SERVICE_AUTH_DENIED` 可在 audit 表中查到
- [ ] sprint-28/it/scripts/service-auth-smoke.sh 通过(包含 happy path、缺 token、错 token、未知 service 四类)
- [ ] 四服务联调:`dts-platform-webapp` 新建数据连接 → 新建入湖任务 → 运行 → 成功获取数据源详情(原 403 场景修复)
- [ ] `pnpm build`、`mvn -pl dts-platform,dts-ingestion,dts-analytics test` 全过

## 阶段产物

- `worklog/v2.2.3/sprint-28-202605/it/scripts/service-auth-smoke.sh`
- `worklog/v2.2.3/sprint-28-202605/it/evidence/<date>-local/` smoke 截图与日志
- `worklog/v2.2.3/sprint-28-202605/assets/env-migration-matrix.md` 旧→新 env 映射

## 风险与回滚

- **风险**:filter 强校验上线后,如果某个调用方 token 配置遗漏将立即 403。
- **缓解**:新增配置开关 `dts.platform.inbound.service-auth.legacy-header-only-mode`,临时开启可回退到 Sprint-28 之前行为(仅 dev/紧急回滚使用,production 严禁开启)。
- **回滚路径**:三个 PR 独立可回滚(F1+F2+F3 platform 侧 / F4 ingestion / F5 analytics),按依赖关系反向 revert。
