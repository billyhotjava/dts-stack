# Sprint-64 API 契约（v1）

后端资源前缀：`/api/governance/sprint64`。标准响应沿用平台 `ApiResponse<T>`，前端 `apiClient` 自动解包 `data`。

## 业务过程

- `GET /domains/{domainId}/processes`：返回 `BusinessProcessDto[]`。
- `POST /domains/{domainId}/processes`：请求 `{ processId, name, description? }`；`processId` 规范为 2–64 位小写字母、数字、`_`、`-`。
- `DELETE /domains/{domainId}/processes/{processId}`：同时清理该过程的总线矩阵链接。

响应对象：`{ version, processId, domainId, name, description, createdAt, updatedAt }`。

## 分层、粒度与一致性维度

- `GET /warehouse-layers`：返回 5 个受控层及 `allowedUpstream`、`namingPrefixes`。
- `POST /grain/validate`：请求 `{ warehouseLayer, statement, grainKeys[] }`，返回 `status`（`not_required` / `blocked` / `ready`）、`message` 和规范化后的声明。
- `GET /domains/{domainId}/conformed-dimensions`：返回 8 个公共维度种子。

## 总线矩阵

- `GET /domains/{domainId}/bus-matrix`：返回已保存的 `{ processId, dimensionId, enabled }[]`。
- `PUT /domains/{domainId}/bus-matrix`：幂等保存同一过程和维度的勾选状态，请求体与返回对象相同。

GET 接口失败时 UI 使用 session 草稿回退；写入接口失败时保留当前 session 状态并提示下一次同步。数据库由 Liquibase 变更集 `20260711_01_sprint64_governance.xml` 创建业务过程和矩阵表。
