# T01: ApiProperties配置类

**优先级**: P0
**状态**: DONE
**依赖**: 无（F2 各任务消费其默认值，宜先行）

## 目标

新建 `@ConfigurationProperties(prefix="dts.ingestion.api")` 配置类，收编旧路径全部硬编码默认值，任务级配置可覆盖全局默认。

## 技术设计

收编项（对照审计，原值为内嵌 Python 写死值）：

| 配置项 | 原硬编码 | 默认值 |
|---|---|---|
| max-pages | 1000 (`:1276`) | 1000 |
| connect/read-timeout | 60s (`:1369`) | 10s/60s |
| execution-timeout | 15min (`:1510`) | 30min |
| retry.max-retries / backoff-cap | 2 / 30s (`:1368,1396`) | 3 / 30s |
| rate-limit.default-rps / burst | sleep(1/rps) (`:1372`) | 0(不限) / 2×rps |
| max-response-bytes | 无限制 (`:1383`) | 32MB |
| table-prefix | "ods_api_" (`:1241`) | ods_api_ |
| landing.batch-size | 逐行 INSERT | 500 |
| allow-http / allowed-hosts | 无防护 | false / [] |
| executor.pool-size / per-task-concurrency | n/a | 4 / 1 |

- 目标库连接不进此类——走数据源解析（F2-T05），彻底移除 `DTS_TARGET_DB_*` 默认值 `dts-pg/biadmin`。
- application.yml 增加带注释的默认段；所有项支持 env 覆盖。

## 影响范围

- 新增 `config/ApiProperties.java` + application.yml
- F2 各组件注入消费

## 验证

- [x] 配置绑定单测；非法值（负数超时等）启动失败 fast-fail
- [x] Java HTTP 执行器消费 `max-pages` / timeout / retry / rate-limit / `max-response-bytes` / `allow-http` / `allowed-hosts` 默认值，任务级 requestPolicy 仍可覆盖
- [x] API ODS 表名前缀经 `ApiProperties.table-prefix` 进入任务创建/更新、执行计划和 raw landing fallback
- [x] raw landing 消费 `landing.batch-size`，支持批量 flush
- [x] 瘦 API DAG 的 poll timeout 与 Airflow `execution_timeout` 使用 `ApiProperties.execution-timeout` 默认值生成

## 完成标准

- [x] 审计清单中 API 路径硬编码项 100% 收编或废除（T02 的旧 env/Python 路径清除另行核销）

## 进展记录

- 2026-06-12: 新增 `config/ApiProperties` 并注册到 `DtsIngestionApp`；`application.yml` 增加 `dts.ingestion.api.*` 默认段与 env 覆盖。
- 2026-06-12: `ApiHttpEngine` 改为配置驱动默认分页/超时/响应大小/重试/限流/HTTP host 策略；默认 `allow-http=false`，测试显式开启本地 HTTP。
- 2026-06-12: `ApiSourceConfigNormalizer` 新增 table-prefix 重载；`ApiHttpSourceConnector`、`IngestionTaskResource`、`ApiRawLandingService` 改为消费配置。
- 2026-06-12: 单测通过：`ApiPropertiesTest,ApiHttpEngineTest,ApiHttpSourceConnectorTest,ApiRawLandingServiceTest,ApiIngestionExecutorTest,ApiAuthProviderRegistryTest,ApiConnectorContractResourceTest,InternalApiIngestionResourceTest,ExecutionFailureClassifierTest,PlatformInfraClientTest,AirflowDagServiceTest#shouldGenerateThinApiDagThatCallsInternalIngestionEndpoint,AirflowExecutionSyncServiceTest,IngestionTaskServiceTest#executeInternalApi_shouldUseRequestBatchAndBackfillWindow,IngestionTaskResourceTest`。
- 2026-06-12: 复核 `ApiPropertiesTest` 与硬编码关键字扫描；API 默认值仅保留在 `ApiProperties` / `application.yml` env 覆盖段，证据见 `../../it/evidence/api-properties-hardcoding-20260612.txt`。
