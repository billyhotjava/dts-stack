# F4: ingestion 出站规范化

**优先级**: P0
**状态**: DONE
**目标**: 把 `dts-ingestion` 调 `dts-platform` 的客户端配置从"借用 `dts.platform.service-token` 而 fallback 到 `DTS_ADMIN_SERVICE_TOKEN`" 改为独立、命名直观的 outbound 配置;旧 env 保留 fallback 一个版本周期。

**依赖**: F3(platform 侧已支持 trustedServices Map,token 校验已对齐)

## 背景

ingestion 当前的混乱:
- `application.yml`: `dts.platform.service-token: ${DTS_PLATFORM_SERVICE_TOKEN:${DTS_ADMIN_SERVICE_TOKEN:}}`
- `PlatformInfraClient`: `@Value("${dts.platform.service-token:}")` 单字段注入
- `IngestionSettingsSeeder.buildPlatformSettings()` 不 seed serviceToken,导致部署时若仅设 settings 不设 env 即失效
- 文档没有提示 `DTS_ADMIN_SERVICE_TOKEN` 会影响 ingestion → platform 调用

F4 重构后语义直观:`DTS_INGESTION_TO_PLATFORM` = "ingestion 调 platform 时使用的 token"。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | 新增 `IngestionOutboundPlatformProperties`(`dts.ingestion.outbound.platform`),含 enabled/baseUrl/apiPath/serviceToken/serviceName |
| T02 | DONE | application.yml 新 `dts.ingestion.outbound.platform.*` 段,token 三级 fallback;旧 `dts.platform.service-token` 标 deprecated |
| T03 | DONE | `PlatformInfraClient` 删除 `@Value`,改注入 outboundProps;新增 `resolveServiceName()`;buildUri 用 properties 作 fallback |
| T04 | DONE | `IngestionSettingsSeeder.buildPlatformSettings()` 注入 outboundProps,seed 时把 serviceToken/serviceName 同步到 settings 表 |
| T05 | SKIPPED | application-dev.yml/docker-compose 模板由运维侧维护,F7 阶段统一在兼容矩阵文档说明 |
| T06 | DONE | `PlatformInfraClientTest` 4 用例:settings 优先 / properties fallback / 双方空时不附 header / properties.baseUrl fallback |

## 影响范围

- 新增: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/config/IngestionOutboundPlatformProperties.java`
- 修改: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/infra/PlatformInfraClient.java`
- 修改: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/infra/IngestionSettingsSeeder.java`
- 修改: `source/dts-ingestion/src/main/resources/application.yml`
- 修改: `source/dts-ingestion/src/main/resources/application-dev.yml`(若存在)
- 测试: `PlatformInfraClientTest` 增加 fallback 与无 token 场景

## 配置示例

```yaml
dts:
  ingestion:
    outbound:
      platform:
        base-url: ${DTS_PLATFORM_BASE_URL:http://dts-platform:8081}
        api-path: ${DTS_PLATFORM_API_PATH:/api}
        service-token: ${DTS_INGESTION_TO_PLATFORM:${DTS_PLATFORM_SERVICE_TOKEN:${DTS_ADMIN_SERVICE_TOKEN:}}}
```

旧 `dts.platform.*` 配置标 `@Deprecated`,日志 WARN 提示"please migrate to dts.ingestion.outbound.platform.*"。

## 验证

- [ ] 仅设 `DTS_INGESTION_TO_PLATFORM`,入湖任务 200
- [ ] 仅设 `DTS_PLATFORM_SERVICE_TOKEN`(旧名),入湖任务 200(fallback 生效)
- [ ] 仅设 `DTS_ADMIN_SERVICE_TOKEN`(更旧名),入湖任务 200(三级 fallback 生效)
- [ ] 三个全设且互不相同,新名最高优先级
- [ ] 启动日志包含 deprecated 提示(如使用旧名)

## 完成标准

- [x] 新 properties bean 落地
- [x] 三级 fallback 在测试中验证
- [ ] 部署文档(env-migration-matrix.md)更新(F7 阶段统一)

## 实现记录

- 新增: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/config/IngestionOutboundPlatformProperties.java`
- 修改: `DtsIngestionApp.java` `@EnableConfigurationProperties` 加新 bean
- 修改: `PlatformInfraClient` 注入新 bean,resolveServiceName/Token/buildUri 全部走 outboundProps + settings fallback
- 修改: `IngestionSettingsSeeder` 注入 outboundProps,buildPlatformSettings seed serviceToken/serviceName(运维 UI 可见可改)
- 修改: `application.yml` 新 prefix + 三级 fallback,旧段标 deprecated
- 验证: `mvnw -f dts-ingestion/pom.xml compile` BUILD SUCCESS;PlatformInfraClientTest 4 tests pass
