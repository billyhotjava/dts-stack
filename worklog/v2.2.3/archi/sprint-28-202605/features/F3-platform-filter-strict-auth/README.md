# F3: platform filter 强校验

**优先级**: P0
**状态**: DONE
**目标**: 关闭 `ServiceDependencyAuthenticationFilter` "仅靠 X-DTS-Service header 即注入 OP_ADMIN" 的越权面。要求 token 必须匹配,否则 principal 保持匿名,直接由后续 `@PreAuthorize` 拒绝。

**依赖**: F1, F2

## 背景

当前 filter 只做"白名单即权限":请求带 `X-DTS-Service: dts-ingestion` 即注入 `service:dts-ingestion` 主体并授予 `OP_ADMIN` 权限。`runtime-detail` 端点额外补了一层 token 校验所以暂时安全,但其他仅依赖 `OP_ADMIN` 的端点(数十个)只要内部网络可被伪造 header 即遭越权。

F3 要把"白名单"和"权限注入"解耦:**白名单 + token 校验通过 = 注入,任意一个失败 = 不注入**。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | filter doFilterInternal 已添加 `X-DTS-Service-Token` 强校验,缺一不可注入 |
| T02 | DONE | token 校验:resolveExpectedToken 优先,SvcTokenAuthService(动态 svc_token)兜底 |
| T03 | DONE | `legacy-header-only-mode` 开关已落地,SecurityConfiguration filter 工厂启动时检查 production profile + legacy=true 输出强 WARN |
| T04 | DONE | InfraDataSourceResource.runtimeDetail 保留双重校验(security in depth):filter 是注入 principal,resource 是端点级 ACL,两层独立失效模型;字段已切到新 bean(F1) |
| T05 | DONE | `ServiceDependencyAuthenticationFilterTest` 11 用例覆盖:无 header / 无 token / 错 token / 对 token / sharedSecret fallback / 动态 svc_token / 未知 service / 已认证保持 / legacy mode / 未知 service 即使 legacy 也拒绝 / disabled |

## 影响范围

- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java`
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/infra/InfraDataSourceResource.java`(简化 serviceTokenMatches)
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/PlatformInboundServiceAuthProperties.java`(加 legacyHeaderOnlyMode 字段)
- 测试: `ServiceDependencyAuthenticationFilterTest` 新增,`InfraDataSourceResourceTest` 调整

## 安全说明

- F3 完成后,**伪造 X-DTS-Service header 不再能拿到任何权限**
- legacy-header-only-mode 仅作为应急回滚,production 必须设为 false。配置加载时如发现 production profile + legacyHeaderOnlyMode=true,启动日志 WARN 级警告
- 单测必须覆盖默认模式与 legacy 模式各四种边界

## 验证

- [ ] curl `runtime-detail` 不带 token → 403(filter 已拦截)
- [ ] curl 带错 token → 403
- [ ] curl 带正确 token → 200
- [ ] 任一非 `runtime-detail` 但要求 OP_ADMIN 的端点,仅靠 header(不带 token)→ 403(F3 之前是 200,这是越权面修复证据)
- [ ] 设 legacyHeaderOnlyMode=true,行为退回 Sprint-27

## 完成标准

- [x] filter 默认强校验(F3 上线后,仅靠 X-DTS-Service header 不再获得任何权限)
- [x] legacy 开关存在但默认关,production profile 下开启会输出 SECURITY 级 WARN
- [x] runtime-detail 端点保留双重校验作为 security-in-depth(filter 已注入 principal,resource 端再校一次)
- [ ] 越权面修复证据收录到 it/evidence/(F7 阶段补)

## 实现记录

- 修改: `ServiceDependencyAuthenticationFilter` — 新增构造重载 `(authProperties, svcTokenAuthService)`;`resolveServiceName` 改为强校验路径(token 必带、必匹配);拒绝路径输出结构化 LOG.debug `event=service_auth_denied service=... reason=...` (F6 升级到 WARN + 审计)
- 修改: `SecurityConfiguration.serviceDependencyAuthenticationFilter` Bean 工厂 — 改注入 `ObjectProvider<SvcTokenAuthService>`(避免循环依赖)+ `Environment`(用于 production profile 检查);production + legacyMode 输出强 WARN
- 新增: `ServiceDependencyAuthenticationFilterTest`(11 用例)
- 验证: 38 测试全过
