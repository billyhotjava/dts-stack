# API 入湖现场操作手册

**适用版本**: v2.2.3 Sprint-38  
**对象**: 实施、运维、数据管理员  
**目标**: 从客户 API 文档出发，完成 API 数据源登记、任务创建、执行验证和常见故障定位。

## 1. 接入前拿到这些信息

| 信息 | 必填 | 示例 | 备注 |
|---|---|---|---|
| baseUrl | 是 | `https://crm.example.com/openapi` | 只填根地址，资源路径放任务里 |
| 鉴权方式 | 是 | `jwtLogin` / `bearerToken` / `apiKey` / `basic` | Sprint-38 默认推荐 `jwtLogin` 或 `bearerToken` |
| 敏感值 | 视鉴权 | token、password、clientSecret | 只能填入数据源 secrets，不写任务配置、不写 env |
| 资源路径 | 是 | `/v1/orders` | 放入入湖任务 resource.path |
| recordPath | 是 | `$.data.items` | 指向响应中数组位置 |
| 分页规则 | 视接口 | page/offset/token/nextUrl/link | 不分页接口可不填 |
| 增量字段 | 建议 | `updatedAt` | 用于 checkpoint；全量接口可不填 |
| 目标表名 | 建议 | `ods_api_crm_orders` | 不填时按 table-prefix 生成 |

## 2. 新建 API 数据源

在平台数据源管理中选择 API 类型。数据源只保存连接与鉴权信息：

```json
{
  "baseUrl": "https://crm.example.com/openapi",
  "auth": {
    "provider": "jwtLogin",
    "loginUrl": "https://crm.example.com/login",
    "loginBodyTemplate": "{\"username\":\"{{username}}\",\"password\":\"{{secretRefs.password}}\"}",
    "tokenPath": "$.data.token",
    "expiresInPath": "$.data.expiresIn",
    "tokenPlacement": "bearer"
  },
  "requestPolicy": {
    "allowHttp": false,
    "allowedHosts": ["crm.example.com"]
  }
}
```

敏感值放在 secrets：

```json
{
  "username": "api-user",
  "password": "plain value before platform encryption"
}
```

保存后检查：

- 普通详情接口只应看到 secret 摘要或掩码，不应出现明文。
- `runtime-detail` 只允许 dts-ingestion 服务 token 读取明文。
- Sprint-38 证据脚本：`RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/api-secret-security.sh`。

## 3. 创建入湖任务

任务只保存“取什么”和“怎么写”：

```json
{
  "sourceSystem": "CRM",
  "resource": {
    "resourceId": "orders",
    "path": "/v1/orders",
    "method": "GET",
    "recordPath": "$.data.items",
    "cursor": {
      "type": "datetime",
      "field": "updatedAt",
      "injectInto": "query",
      "parameterName": "updatedAfter",
      "initialValue": "2026-01-01T00:00:00Z",
      "lookbackSeconds": 300
    },
    "targetTable": "ods_api_crm_orders",
    "landing": {
      "mode": "raw_record",
      "rawRecordColumn": "_dts_raw_record"
    }
  }
}
```

表命名建议：

- API 原始层表用 `ods_api_业务对象`。
- 多资源任务每个 resource 单独配置 `resourceId` 和 `targetTable`。
- 不要在任务里放数据库账号、API token、password、env 变量名。

## 4. 执行与验收

首轮验收关注 4 件事：

1. Airflow DAG task 成功。
2. `ingestion_execution.status=success`，`rows_read/rows_written` 与接口返回一致。
3. ODS raw 表有 `_dts_raw_record` 和技术列。
4. `dts_api_ingestion_checkpoint` 推进到本轮最大 cursor。

可复用 Sprint-38 主链路脚本：

```bash
RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/api-end-to-end.sh
```

已有证据：

- `worklog/v2.2.3/sprint-38-202606/it/evidence/api-end-to-end-20260612.txt`
- `worklog/v2.2.3/sprint-38-202606/it/evidence/api-dag-migration-20260612.txt`

## 5. 补数规则

常规增量读取 `task_id + resource_id` 的 checkpoint：

- 有 checkpoint：下界 = `checkpoint - lookbackSeconds`。
- 无 checkpoint：下界 = `initialValue - lookbackSeconds`。
- backfill 使用显式窗口，不推进 checkpoint。

现场补数前先记录当前 checkpoint，补数后确认 checkpoint 没有被 backfill 改写。

## 6. 常见问题

| 现象 | 优先检查 | 处理 |
|---|---|---|
| 401/403 | auth provider、secret 摘要、登录 tokenPath | 先跑连接测试；JWT 过期应自动重取 |
| 429 | `Retry-After`、rate-limit 配置 | 降低 `default-rps` 或提高 backoff |
| 响应过大 | `max-response-bytes` | 缩小 pageSize 或提高上限 |
| 私网/本机地址被拒 | `allow-http`、`allowed-hosts` | 生产默认拒绝 HTTP 和未授权 host |
| ODS 无新增 | recordPath、cursor 参数、checkpoint | 比对接口响应和 `dts_api_ingestion_checkpoint` |
| DAG 含旧变量 | 是否跑过 rebuild-api | 执行 `api-dag-migration.sh` 重建并扫描 |

