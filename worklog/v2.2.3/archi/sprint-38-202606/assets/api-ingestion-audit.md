# API 入湖现状审计底稿（2026-06-11/12）

> Sprint-38 的立项依据。证据均为 `source/dts-ingestion` 实测 file:line。

## 1. 双层断裂：契约层是死代码，运行层是内嵌 Python

- 干净的 Java SPI 全部存在但**无业务调用方**：`SourceConnectorRegistry` 仅被自身构造引用；`ApiHttpSourceConnector` 仅被自身+测试引用；`ExecutionPlan/CheckpointPolicy` 设计良好但未接线。
- 真实执行路径：`IngestionTaskResource:1693 isApiSourceType` → `AirflowDagService:380` → `buildApiDagSource(:1145-1529)` 385 行 Java 文本块内嵌 Python（→Jinja→Python 三层转义），PythonOperator 在 Airflow worker 进程内跑。
- 三层嵌套已出过事故：dbt 路径同构的 `syncManifest | tojson` 渲染 `raw = true` → NameError（2026-06-11 修复，`DbtDagService:372`）。

## 2. 契约宣告 ≠ 运行时实现（全部经 grep 0 命中验证）

| 契约项 | 宣告处 | 运行时 |
|---|---|---|
| 7 种鉴权 | `ApiAuthProviderRegistry:12-75` | 仅 none/bearer/apikey/basic（`:1337-1355`），oauth2/customSignature/mtls → 运行必抛 UNSUPPORTED |
| TlsPolicy(verifyTls/mtlsSecretRef) | `ApiSourceContracts:76` | 0 消费 |
| maxResponseBytes/followRedirects | `:72` | 0 消费（`resp.read()` 无限读 `:1383`） |
| initialValue/lookbackSeconds | `CursorPolicy:82` | 0 消费 |
| burst/maxConcurrency | `RateLimitPolicy:74` | 0 消费（仅 `sleep(1/rps)` `:1372`） |
| stagingFields/driftPolicy | `:88/:86` | 0 消费 |

## 3. 密钥问题

- `_secret()` 直读 `os.environ`（`:1359`），secretRef 被当 env 变量名；默认共享命名 `DTS_API_BEARER_TOKEN/DTS_API_KEY/...` → 多客户无隔离、无轮换、明文进 worker 环境。
- 对照组：JDBC 路径正确（`IngestionSourceResolver:80-89` 数据源 secrets + `extractSecret` 进程内解密）；Addax 路径有 `resolve_secret(Variable.get)`（`:655-678`）；**API 路径两者都没有**。
- 目标库连接硬编码默认 `dts-pg/biadmin/biadmin`（`:1205-1208`）。

## 4. 逻辑缺陷

| 缺陷 | 位置 | 后果 |
|---|---|---|
| 游标字符串比较 `str(v)>str(max)` | `:1268` | 数值/epoch 游标推进错误（"100"<"99"） |
| 多资源单连接单事务，最后统一 commit | `:1229-1233` | 一个资源失败回滚全部（含 checkpoint） |
| `_has_next_page` 以 `record_count>=page_size` 续页 | `:1433-1435` | 中途不足页提前停 → 丢数 |
| `next_url` 无校验直连 | `:1272` | SSRF（worker 在内网） |
| 落地无幂等键，永远 INSERT | `_insert_record:1461` | 重跑/重叠窗口重复行 |
| `execution_timeout=15min`/`maxPages=1000`/退避/超时全写死 | `:1510/:1276/:1396/:1369` | 不可调，无 ApiProperties 配置类 |

## 5. 方向澄清结论（与用户确认）

- 本版本场景 = **① 出站拉取**（我方调对方 API）。② 入站推送（对方调我方接收端点）代码中完全不存在（已核验全部 @PostMapping），列后续 sprint。
- 运行时形态决策 = **Java 执行器**（用户选定），调度关系 = C1 瘦触发（Airflow 保留调度/监控，执行回归 dts-ingestion 进程）。
- 密钥方案 = 存数据源 secrets + 进程内解密（与 JDBC 同构），废除 env 路径。
- raw landing 契约（`_dts_raw_record` JSONB + 9 技术列 + checkpoint 表）保留——「先收数据后建模」与 dbt STG 解 JSON 的分层不变。
