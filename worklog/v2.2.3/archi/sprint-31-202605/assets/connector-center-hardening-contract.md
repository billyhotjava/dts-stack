# Connector Center 企业级补齐契约

## 目标

Sprint-31 F2 将 Connector Center 从“只要能登记数据源”升级为可观测、可预检、可影响追踪的入口：

- 数据源列表失败必须暴露错误，不能静默返回空列表。
- JDBC、API、文件接入能力必须在页面上明确区分。
- 数据源连接参数变更必须生成入湖任务影响单。
- ODS 预检默认不得对源端大表执行无保护 `count(*)`。
- 多表 ODS 生成必须保留表级主键、增量列和字段映射覆盖。

## 运行规则

### 列表失败

`InfraManagementService.listDataSources` 对本地数据源查询异常直接抛出，由 API/前端展示可观测错误。默认数据湖合并失败仍保持非阻断，因为该数据来自 admin 侧外部依赖。

### 连接变更影响单

当 JDBC URL、用户名、驱动、库名、schema、readerType、文件/API 关键 props 或凭据引用变更时，platform 调用 ingestion 创建变更记录：

```text
changeType = CONN_PARAM
riskLevel = H
status = NEEDS_REVIEW
summary = 数据源连接已更新，入湖任务需要复核
```

`NEEDS_REVIEW` 可在变更中心提交审批，审批通过后进入 `DONE`。

### 大表预检

源表精确行数扫描默认关闭：

```json
{
  "precheckExactRowCount": false
}
```

需要行数基线校验时，由工程师在数据源 props 中显式启用：

```json
{
  "precheckExactRowCount": true,
  "precheckQueryTimeoutSeconds": 10,
  "precheckRowCountBaselines": {
    "public.order": {
      "expected": 100000,
      "maxDeviationRatio": 0.3
    }
  }
}
```

### 能力矩阵

| 接入类型 | 页面标识 | 连接测试 | Schema 探测 | ODS 预检 | 入湖任务 | 说明 |
|---|---|---:|---:|---:|---:|---|
| JDBC | JDBC 正式 | yes | yes | yes | yes | 黄金链路正式支持 |
| API | API 预览 | yes | no | no | yes | 依赖接口契约和任务资源路径 |
| Excel/CSV | 文件预览 | no | no | no | yes | 依赖文件接入任务和 staging 质量检查 |
| 其他 reader | 需补契约 | no | no | no | 条件支持 | 必须补 readerType、资源路径或 JDBC 地址 |

## 验收

- 列表接口异常不会被前端显示为“暂无数据源”。
- 连接变更影响单能在变更中心以“需复核”状态出现并提交审批。
- 默认 ODS 预检结果包含 `SOURCE_ROW_COUNT_ESTIMATE`，而不是直接执行源表 `count(*)`。
- 页面能区分 JDBC / API / 文件能力边界。
