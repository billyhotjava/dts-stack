# dbapi test service

最小只读 MySQL 数据 API，用于验证外部系统如何按 DTS 数据 API 约定读取 PJM ODS 数据。

## 接口与鉴权

所有响应使用 `{"code": 0, "message": "success", "data": ...}`；错误响应使用稳定业务码。`/` 和 `/health` 公开，其余 `/api/v1/**` 接口必须发送 `X-API-Key`。

| Method | Path | 作用 |
| --- | --- | --- |
| GET | `/` | Base URL 连接探针 |
| GET | `/health` | 进程健康检查，不访问数据库 |
| GET | `/api/v1/db/ping` | 验证 MySQL 连通性 |
| GET | `/api/v1/db/tables?schema=mysql` | 列出指定 schema 的表 |
| GET | `/api/v1/pjm/resources` | 列出 10 个允许访问的 PJM 资源 |
| GET | `/api/v1/pjm/{resource}?page=1&size=20` | 分页读取一个允许的资源 |

PJM 白名单固定为：

| resource | MySQL table |
| --- | --- |
| `project-subject-domain` | `ods_project_subject_domain_v2` |
| `progress-measure` | `ods_progress_measure_v2` |
| `quality-issue` | `ods_quality_issue_v2` |
| `quality-measure` | `ods_quality_measure_v2` |
| `tech-state` | `ods_tech_state_v2` |
| `tech-state-measure` | `ods_tech_state_measure_v2` |
| `risk-info` | `ods_risk_info_v2` |
| `risk-measure` | `ods_risk_measure_v2` |
| `material-info` | `ods_material_info_v2` |
| `budget` | `ods_budget_v2` |

资源查询始终按 `id ASC` 排序，返回对象数组并过滤 `_dts_*` 内部追溯字段：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [{"id": 1, "project_no": "P-001"}],
    "total": 1,
    "page": 1,
    "size": 20,
    "hasNext": false
  }
}
```

`page` 从 1 开始，`size` 上限由 `DBAPI_MAX_PAGE_SIZE` 控制。服务不开放任意 SQL 查询接口，只允许读取上表列出的固定资源。

## Secret 与数据库权限

- `DB_PASSWORD_FILE` 优先于 `DB_PASSWORD`，`DBAPI_API_KEY_FILE` 优先于 `DBAPI_API_KEY`。
- 配置了 secret 文件但文件不可读时服务会失败关闭，不回退到明文环境变量。
- Docker Compose 只读挂载两个 secret 文件；文件应放在仓库外，并在 `.env` 中配置绝对路径。镜像以固定 UID/GID `10001:10001` 运行，部署前需确保该 UID 能只读 secret 文件。
- `DB_USER` 必须是仅有目标 schema `SELECT` 权限的专用账号，不能使用 root 或带写权限的账号；示例中的 `dts_pjm_reader` 需要先在 MySQL 中创建并授权。
- 数据库连接默认使用 5 秒连接超时、10 秒读写超时；`DB_READ_TIMEOUT` 和 `DB_WRITE_TIMEOUT` 只接受 1–30 秒。
- 服务不记录或返回密码/API Key。

## 验证与启动

```bash
python3 -m unittest discover -s tests -v
cp .env.example .env
# 编辑 .env，设置实际 DB_NAME、只读 DB_USER 和两个仓库外 secret 文件的绝对路径
docker compose up --build -d
docker compose ps
curl http://127.0.0.1:8081/health
curl -H 'X-API-Key: <api-key>' http://127.0.0.1:8081/api/v1/pjm/resources
curl -H 'X-API-Key: <api-key>' 'http://127.0.0.1:8081/api/v1/pjm/budget?page=1&size=20'
```

容器默认使用 Gunicorn（2 workers、每 worker 4 threads、30 秒 timeout），避免单个慢连接阻塞整个服务。需要本地直接调试 WSGI 应用时，仍可在配置环境变量后运行 `python3 app.py`。

Compose 默认通过 `DBAPI_BIND_ADDRESS=127.0.0.1` 仅绑定宿主机回环地址。跨主机联调时可以在隔离测试网络中显式设置 `DBAPI_BIND_ADDRESS=0.0.0.0`，同时必须限制来源 IP；这种 HTTP 方式会明文传输 API Key，不用于生产。生产部署应保持回环绑定并通过受控的 TLS 反向代理暴露服务。

## 与 DTS 的对应关系

DTS 的 API ingestion connector 可通过 `/api/ingestion/tasks` 配置并采集本服务的资源端点。API 管理页的元数据发布与模拟 `execute` 属于另一条管理网关链路，不影响 ingestion connector 直接调用这些端点。
