# dbapi test service

最小只读 MySQL 数据 API，用于验证外部系统如何按 DTS 数据 API 的基本约定提供服务。

## 接口约定

所有响应使用 `{"code": 0, "message": "success", "data": ...}`；错误响应的 `code` 为稳定业务码。所有请求和响应均为 `application/json`。

| Method | Path | 作用 |
| --- | --- | --- |
| GET | `/health` | 进程健康检查，不访问数据库 |
| GET | `/api/v1/db/ping` | 验证 MySQL 连通性 |
| GET | `/api/v1/db/tables?schema=mysql` | 列出指定 schema 的表 |
| POST | `/api/v1/db/query` | 执行一条只读 `SELECT` / `WITH` 查询 |

查询请求示例：

```json
{"sql":"SELECT 1 AS value"}
```

`query` 只接受单条 `SELECT` 或 `WITH`；参数必须为数组，最多返回 `DBAPI_MAX_ROWS` 行。服务不记录或返回数据库密码。

## 本地验证

```bash
python3 -m unittest -v tests/test_app.py
cp .env.example .env
docker compose up --build -d
curl http://127.0.0.1:8081/health
curl http://127.0.0.1:8081/api/v1/db/ping
curl -X POST http://127.0.0.1:8081/api/v1/db/query \
  -H 'Content-Type: application/json' \
  -d '{"sql":"SELECT 1 AS value"}'
```

## 与 DTS 的对应关系

DTS 管理页登记的是 API 元数据（编码、HTTP 方法、路径、数据集、密级、限流、请求/响应 Schema），并通过 `/api/apis/{id}/publish` 发布。这个版本的 DTS `execute` 仍返回模拟数据，不会转发到 `path`；因此本服务可用于外部 API 验证，但尚不能被该执行链路实际代理。
