# ApiProperties 配置参考

**配置前缀**: `dts.ingestion.api`  
**环境变量前缀**: `DTS_INGESTION_API_`  
**代码入口**: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/config/ApiProperties.java`

## 总览

| 配置项 | 环境变量 | 默认值 | 说明 |
|---|---|---|---|
| `max-pages` | `DTS_INGESTION_API_MAX_PAGES` | `1000` | 单资源单次执行最多翻页数 |
| `connect-timeout` | `DTS_INGESTION_API_CONNECT_TIMEOUT` | `10s` | 建连超时 |
| `read-timeout` | `DTS_INGESTION_API_READ_TIMEOUT` | `60s` | 读取响应超时 |
| `execution-timeout` | `DTS_INGESTION_API_EXECUTION_TIMEOUT` | `30m` | API 瘦 DAG 轮询和 Airflow execution timeout |
| `max-response-bytes` | `DTS_INGESTION_API_MAX_RESPONSE_BYTES` | `32MB` | 单次 HTTP 响应大小上限 |
| `table-prefix` | `DTS_INGESTION_API_TABLE_PREFIX` | `ods_api_` | 未显式指定 targetTable 时的默认表前缀 |
| `allow-http` | `DTS_INGESTION_API_ALLOW_HTTP` | `false` | 是否允许 HTTP 明文接口 |
| `allowed-hosts` | `DTS_INGESTION_API_ALLOWED_HOSTS` | 空 | 允许访问的 API host 白名单 |
| `retry.max-retries` | `DTS_INGESTION_API_RETRY_MAX_RETRIES` | `3` | 可重试 HTTP/网络错误最大重试次数 |
| `retry.base-backoff` | `DTS_INGESTION_API_RETRY_BASE_BACKOFF` | `200ms` | 初始退避 |
| `retry.backoff-cap` | `DTS_INGESTION_API_RETRY_BACKOFF_CAP` | `30s` | 单次退避上限 |
| `rate-limit.default-rps` | `DTS_INGESTION_API_RATE_LIMIT_DEFAULT_RPS` | `0` | 默认每秒请求数，0 表示不限速 |
| `rate-limit.burst-multiplier` | `DTS_INGESTION_API_RATE_LIMIT_BURST_MULTIPLIER` | `2` | 突发令牌倍数 |
| `rate-limit.max-concurrency` | `DTS_INGESTION_API_RATE_LIMIT_MAX_CONCURRENCY` | `1` | 单任务 API 并发上限 |
| `landing.batch-size` | `DTS_INGESTION_API_LANDING_BATCH_SIZE` | `500` | raw landing 批量写入大小 |
| `executor.pool-size` | `DTS_INGESTION_API_EXECUTOR_POOL_SIZE` | `4` | API 执行器线程池大小 |
| `executor.per-task-concurrency` | `DTS_INGESTION_API_EXECUTOR_PER_TASK_CONCURRENCY` | `1` | 单任务内部资源并发上限 |

## 推荐值

| 场景 | 建议 |
|---|---|
| 客户 API 有 QPS 限制 | 设置 `rate-limit.default-rps` 为客户给出的 70%-80% |
| 客户响应页很大 | 优先调小任务 pageSize，再考虑提高 `max-response-bytes` |
| 客户只提供 HTTP 测试地址 | 测试环境可设 `allow-http=true` 并限定 `allowed-hosts`；生产默认保持 false |
| 夜间大批量补数 | 提高 `execution-timeout`，同时观察 Airflow task timeout |
| 多 API 任务并发 | 提高 `executor.pool-size`，不要先提高单任务 concurrency |

## 配置示例

```yaml
dts:
  ingestion:
    api:
      max-pages: 500
      read-timeout: 45s
      execution-timeout: 45m
      max-response-bytes: 64MB
      allow-http: false
      allowed-hosts:
        - crm.example.com
      retry:
        max-retries: 4
        base-backoff: 500ms
        backoff-cap: 20s
      rate-limit:
        default-rps: 5
        burst-multiplier: 2
        max-concurrency: 1
```

## 验证

```bash
cd source
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=ApiPropertiesTest test
```

运行态变更后，重启 `dts-ingestion` 并用 IT 主链路脚本验证：

```bash
RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/api-end-to-end.sh
```

