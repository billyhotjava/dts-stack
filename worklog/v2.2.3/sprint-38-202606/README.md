# Sprint-38: 基于应用系统 API 的数据入湖重构

**时间**: 2026-06
**状态**: IN_PROGRESS
**目标**: 以「我方调用应用系统提供的 API 拉取数据入湖」为第一目标，从数据源连接、凭据安全、执行引擎到前端任务向导端到端重构，消除现有 API 入湖路径的硬编码与逻辑缺陷，使 API 成为与 JDBC/文件并列的一等入湖方式。

## 背景

现版本需对接客户应用系统数据，确定方向为**出站拉取**（我方持有客户 API 凭据，定时调用对方 API 取数入 ODS）。架构审查（2026-06-11/12，见 assets/api-ingestion-audit.md）结论：

1. **双层断裂**：契约/模型层（`ApiHttpSourceConnector`/`ApiSourceContracts`/`ApiAuthProviderRegistry`，干净的 Java SPI）是**死代码**，真实运行的是 `AirflowDagService.buildApiDagSource` 里 385 行内嵌 Python（Java 文本块→Jinja→Python 三层嵌套，不可单测、转义脆弱）。
2. **契约宣告 ≠ 运行时实现**：注册表对外 7 种鉴权，运行时仅 bearer/apikey/basic 3 种；`verifyTls/maxResponseBytes/followRedirects/initialValue/lookbackSeconds/burst/maxConcurrency/stagingFields/driftPolicy` 契约字段运行时 0 消费。
3. **密钥裸奔**：`_secret()` 直读 worker `os.environ`，多客户共享 env 命名空间、无隔离无轮换；与 JDBC 路径（数据源 secrets 加密落库 + 进程内解密）双标。
4. **逻辑缺陷**：游标字符串比较（数值游标判断错）、多资源单事务（一错全回滚）、分页提前停、SSRF 无防护、目标库连接硬编码 `dts-pg/biadmin`。

## 核心架构决策（已与用户确认）

| 决策点 | 结论 |
|--------|------|
| 接入方向 | **① 出站拉取**：我方调用对方 API（入站推送 ② 为后续 sprint） |
| 执行引擎 | **Java 执行器**：废除内嵌 Python，`ApiHttpSourceConnector` SPI 落地为 dts-ingestion 进程内真实执行器 |
| 调度关系 | **C1 瘦触发**：保留 Airflow 调度/监控，DAG 只回调 dts-ingestion 执行接口（与 JDBC/文件路径运维一致） |
| 密钥方案 | **存数据源 secrets**（dts-platform 加密落库）+ dts-ingestion **进程内解密**，复用 `IngestionSourceResolver`/`InfraSettingsCryptoService`（AES-GCM + keyVersion）；明文不出服务边界、不进 env/Airflow Variable |
| 落地契约 | 保留 raw landing（`_dts_raw_record JSONB` + 9 技术列 + checkpoint 表），schema-on-read，dbt STG 解 JSON |
| 鉴权范围 | **客户对接已确认 JWT token**（2026-06-12，对方应用提供登录端点换短时 token）：新增 `jwtLogin` 策略（登录→token 内存缓存→过期/401 重取）为 P0 主路径；bearer/apikey/basic 做稳；OAuth2 client credentials 同形态顺带 GA；签名(HMAC/国密)/mTLS 维持 PREVIEW |

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 |
|----|---------|--------|---------|------|
| F1 | API数据源与凭据安全 | P0 | 4 | DONE |
| F2 | Java执行器 | P0 | 6 | IN_PROGRESS |
| F3 | 任务编排与调度集成 | P0 | 4 | IN_PROGRESS |
| F4 | 前端改造 | P1 | 3 | READY |
| F5 | 配置外部化与旧路径下线 | P1 | 3 | DONE |

**依赖顺序**: F1 → F2 → F3 → F5（后端主线）；F4 依赖 F1 契约接口可并行启动。

## 完成标准

- [x] 新建 API 数据源（含凭据）→ 创建入湖任务 → 调度执行 → raw JSON 落 ODS → checkpoint 推进 → 第二轮增量，全链路在测试环境跑通（IT-01：`it/evidence/api-end-to-end-20260612.txt`）
- [x] 客户 API 凭据全程密文存储，DAG 文件/容器 env/Airflow Variable/日志中无明文（安全验证证据存 it/）
- [x] `buildApiDagSource` 内嵌 Python 路径删除，存量 API DAG 迁移为瘦触发 DAG（IT-08：`it/evidence/api-dag-migration-20260612.txt`）
- [x] 鉴权注册表 enabled 状态与运行时实现一致，UI 不可配置未实现的鉴权
- [x] API 运行时参数（maxPages/超时/退避/限流/表前缀）全部经 `ApiProperties` 配置化，代码内无业务硬编码（证据：`it/evidence/api-properties-hardcoding-20260612.txt`）
- [x] JDBC/文件入湖路径回归不受影响（IT-09：`it/evidence/jdbc-file-regression-20260612.txt`）
