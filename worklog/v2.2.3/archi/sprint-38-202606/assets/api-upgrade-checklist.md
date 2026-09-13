# API 入湖升级与回滚清单

**适用范围**: Sprint-38 API 入湖重构上线、补丁升级、现场回滚。

## 1. 上线前

- 确认当前代码、镜像 tag、数据库备份点。
- 备份 `services/dts-airflow/dags/*.py`。
- 确认 `DTS_INFRA_ENCRYPTION_KEY` 和 `DTS_INFRA_KEY_VERSION` 在 platform 容器内有效。
- 确认 `DTS_INGESTION_API_*` 配置符合客户 API 限流、TLS、响应大小要求。
- 确认不再依赖 `DTS_API_BEARER_TOKEN`、`DTS_API_KEY`、`DTS_API_USERNAME`、`DTS_API_PASSWORD`。

## 2. 构建与重启顺序

1. 构建并发布 `dts-ingestion`。
2. 构建并发布 `dts-platform`。
3. 发布 `dts-platform-webapp`。
4. 重启相关容器并等待 health 通过。

参考命令：

```bash
./builds/dts-build.sh --image dts-ingestion --no-save
./builds/dts-build.sh --image dts-platform --no-save
docker compose -p v223 -f docker-compose-app.yml up -d --no-deps --force-recreate dts-ingestion dts-platform
```

## 3. 迁移动作

重建存量 API DAG：

```bash
docker exec v223-dts-ingestion-1 \
  curl -fsS -H 'X-DTS-Service: dts-platform' \
  -X POST http://127.0.0.1:8083/api/ingestion/tasks/dags/rebuild-api
```

验收脚本：

```bash
RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/api-dag-migration.sh
RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/api-secret-security.sh
RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/jdbc-file-regression.sh
```

必须看到：

- `rebuild_failed=0`
- API DAG 旧 env/旧 Python 标记为 0
- checkpoint 迁移后继续推进
- JDBC/file 回归各成功一轮
- 明文 secret 不出现在 DAG、env、Airflow Variable、日志

## 4. 回滚

1. 暂停 API 任务调度。
2. 切回上一版镜像和 git tag。
3. 恢复备份 DAG 文件，或用上一版 dts-ingestion 重建 DAG。
4. 不回滚 `dts_api_ingestion_checkpoint`。
5. 如新版本已写入错误数据，按 `ingestion_execution.batch_id` / `execution_id` 对 ODS 做数据侧回滚。
6. 禁止恢复旧 `DTS_API_*` env 明文密钥；存量任务必须改配数据源 secrets。

## 5. 证据位置

- 主链路：`it/evidence/api-end-to-end-20260612.txt`
- 密钥安全：`it/evidence/api-secret-security-20260612.txt`
- DAG 迁移：`it/evidence/api-dag-migration-20260612.txt`
- JDBC/file 回归：`it/evidence/jdbc-file-regression-20260612.txt`
- 配置化核销：`it/evidence/api-properties-hardcoding-20260612.txt`

