# API 数据源契约 v1.2

**状态**: REVIEWED
**日期**: 2026-06-12
**适用范围**: 出站拉取模式（我方调用客户应用系统 API 拉取数据入湖）

## 边界

- 数据源保存「怎么连」：`baseUrl`、`defaultHeaders`、`auth`、`tls`、`rateLimit`、`requestPolicy`、`secrets`。
- 入湖任务保存「取什么」：`resources`、`path`、`method`、`query`、`bodyTemplate`、`recordPath`、`pagination`、`cursor`、`targetTable`、`stagingFields`。
- 敏感值只进入数据源 `secrets` 加密字段；`props` / DAG 文件 / env / Airflow Variable 不保存明文。

## 数据源 Props

```json
{
  "connectorType": "api",
  "readerType": "httpreader",
  "sourceCategory": "api",
  "contractVersion": "1.2.0",
  "baseUrl": "https://crm.example.com/openapi",
  "defaultHeaders": {
    "X-Tenant": "demo"
  },
  "auth": {
    "provider": "jwtLogin",
    "loginUrl": "https://crm.example.com/login",
    "loginBodyTemplate": "{\"username\":\"{{username}}\",\"password\":\"{{secretRefs.password}}\"}",
    "tokenPath": "$.data.token",
    "expiresInPath": "$.data.expiresIn",
    "tokenPlacement": "bearer"
  }
}
```

## 数据源 Secrets

```json
{
  "password": "plain value before platform encryption",
  "tenantSecret": "plain value before platform encryption"
}
```

运行时由 dts-platform 解密后仅通过 `/runtime-detail` 服务间接口提供给 dts-ingestion；普通详情接口只能返回 `secretRef`、掩码、版本和轮换状态。

## Auth Providers

| ID | enabled | 说明 |
|----|---------|------|
| none | true | 不注入鉴权信息 |
| apiKey | true | header/query 注入 API Key |
| bearerToken | true | Authorization Bearer token |
| basic | true | Basic Authorization header |
| oauth2ClientCredentials | true | tokenUrl 换短时 access_token |
| jwtLogin | true | 登录端点换 JWT，缓存并按过期/401 重取 |
| customSignature | false | 预留签名扩展，运行时未开放 |
| mtls | false | 预留双向 TLS，运行时未开放 |

前端只能允许选择 `enabled=true` 的 provider；后端保存任务时必须再次拒绝 disabled provider。

## 任务 Source Config

```json
{
  "sourceSystem": "CRM",
  "resource": {
    "resourceId": "orders",
    "path": "/v1/orders",
    "method": "GET",
    "recordPath": "$.data.items",
    "pagination": {
      "type": "page",
      "pageParam": "page",
      "sizeParam": "size",
      "pageSize": 100
    },
    "cursor": {
      "type": "datetime",
      "field": "updatedAt",
      "injectInto": "query",
      "parameterName": "updatedAfter",
      "endParameterName": "updatedBefore",
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

说明：

- 常规增量下界参数名优先级：`startParameterName` > `parameterName` > `field`；示例中 `parameterName=updatedAfter`。
- 补数窗口上界参数名使用 `endParameterName`；未配置时仅注入下界，适用于只支持“从某时间后拉取”的 API。
- `lookbackSeconds` 仅作用于常规增量；backfill 显式窗口优先，不叠加 lookback。

## Raw Landing

API raw 表必须包含：

- `_dts_raw_record JSONB`
- `_dts_source_system`
- `_dts_source_resource`
- `_dts_endpoint`
- `_dts_import_time`
- `_dts_batch_id`
- `_dts_execution_id`
- `_dts_page_no`
- `_dts_record_no`
- `_dts_cursor_value`

## 当前实现状态

- 已落地：contractVersion=1.2.0、authProviders.enabled、jwtLogin 契约、platform 默认版本、ingestion `resolveApiInfo`。
- 已落地：Java API 执行器主链路、连接测试端点、raw landing 同批幂等、TLS custom CA、API 运行错误分类与重试。
- 已对齐：`/api/ingestion/api/contract` 与 `/api/ingestion/connectors/capabilities/api` 均输出 1.2.0 与 provider enabled 口径。
- 待完整 IT：端到端主链路、密钥四处无明文、JDBC/文件回归、旧 Python DAG 迁移验证。
