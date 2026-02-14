# P1-04 数据源一致化（API/DB/Card）

`status`: `done-first-pass`

## 目标
- 消除数据源配置断层，统一执行协议。

## 范围
- FE：PropertyPanel 开放 `api/database/card` 配置入口。
- FE：数据库数据源从“仅 ID 输入”升级为“数据库名称选择 + 手工 ID 兜底”。
- FE：运行期统一走 `useCardDataSource`，对 `card/api/database` 输出统一 `rows/cols` 结构。
- BE：沿用统一错误模型（`code/message/requestId`），前端展示 `requestId` 便于排障。
- QA：三类数据源一致性回归。

## 交付物
- 数据源统一配置面板（卡片/API/数据库）
- 运行时统一适配（`toCardData`）
- 执行协议文档：`worklog/v2.2.1/BI/datasource-execution-protocol.md`

## 验收
- 预览与发布环境行为一致。
- 数据库数据源可通过名称选择数据库，不再强依赖人工记忆 DB ID。
- `card/api/database` 三类数据源在组件渲染层可统一消费。

## 本轮进展
- 新增数据库选择器组件：`DatabaseIdPicker`（缓存列表、离线手工输入兼容）。
- 属性面板数据库配置改造：先选数据库，再配 SQL；保留手工 ID 回退。
- `useCardDataSource` 扩展为统一入口，支持 `card/api/database/static`。
- `analyticsApi.HttpError` 补充 `requestId` 提取和透传。

## 依赖
- P0-05 监控与错误码体系。
