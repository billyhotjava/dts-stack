# 大屏数据源统一执行协议（P1-04）

## 1. 目标
- 统一 `card/api/database/static` 四类数据源在前端运行时的输入与输出。
- 保证组件渲染层只消费一种标准结果结构：`{ rows, cols }`。

## 2. 配置协议

### 2.1 通用字段
- `type`: `static | card | api | database`
- `refreshInterval`: 自动刷新间隔（秒，`0/undefined` 表示不刷新）

### 2.2 Card
- `cardConfig.cardId`: Card ID
- `cardConfig.refreshInterval`: Card 局部刷新间隔（可覆盖通用值）

### 2.3 API
- `apiConfig.url`: 请求地址（支持相对路径）
- `apiConfig.method`: `GET | POST`
- `apiConfig.headers`: 请求头（可选）
- `apiConfig.params`: URL Query 参数（可选）
- `apiConfig.body`: POST 请求体（可选）

### 2.4 Database
- `databaseConfig.databaseId`: Analytics 数据库 ID（首选）
- `databaseConfig.connectionId`: 历史兼容字段（保留，不作为首选）
- `databaseConfig.query`: Native SQL

## 3. 执行路径
1. 组件读取 `dataSource`。
2. `useCardDataSource` 根据 `type` 分派：
- `card` -> `analyticsApi.queryCard`
- `api` -> `fetch(url)`
- `database` -> `analyticsApi.runDatasetQuery`
- `static` -> 不发请求
3. 统一调用 `toCardData(payload)` 规范化输出。
4. 渲染器基于 `rows/cols` 映射为组件配置。

## 4. 统一输出
- `rows: unknown[][]`
- `cols: [{ name, display_name, base_type }]`

支持以下输入自动转化：
- `{ rows, cols }`
- `{ data: { rows, cols } }`
- `Array<Record<string, unknown>>`
- `Array<Array<unknown>>`

## 5. 错误与观测
- 请求失败统一抛出 `HttpError`。
- 错误消息保留：HTTP 状态码、错误码（若存在）、`requestId`（若存在）。
- UI 直接展示消息，便于结合后端日志排查。

## 6. 兼容策略
- 老配置中 `databaseConfig.connectionId` 仍可读取。
- 新配置优先 `databaseId`，界面默认提供数据库名称选择，手工输入 ID 作为兜底。
