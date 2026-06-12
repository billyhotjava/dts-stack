# T02: API入湖任务向导

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01

## 目标

入湖任务创建流（explore/etl）支持 API 源：选数据源 → 配资源（path/method/recordPath/多资源）→ 分页/游标 → 目标表映射 → 采样预览 → 提交。

## 技术设计

- 在现有入湖任务创建流（`explore/etl/` ingestionFormHelpers 体系）新增 API 源分支；步骤组件：ResourceConfig（path/method/query/bodyTemplate/recordPath）、PaginationConfig（4 种类型+参数）、CursorConfig（type/field/initialValue/lookbackSeconds，增量模式才显示）、TargetMapping（targetTable 默认 `ods_api_{source}_{resource}` 可改）。
- 采样预览：调连接测试端点带 resource 配置，展示首页记录与 recordPath 解析结果，便于用户调对 JSON 路径。
- syncMode 仅暴露 full_refresh/incremental（对齐 `ApiHttpSourceConnector.validate`）。

## 2026-06-12 进展

- 已在创建页 API 分支补齐资源配置区：`resourceId/path/method/displayName/recordPath/query/bodyTemplate/pagination/cursor`，提交 payload 继续使用 `buildApiReaderConfig` 生成 `resource/resources`、raw ODS landing 和 schema snapshot。
- 已把 API 分支默认运行开关恢复为可执行语义：切换到 API 接入时默认 `airflowEnabled=true`、`runNow=true`，review 页不再提示“仅保存草稿/运行入口未开放”。
- 已接入 API 预览：创建页用当前 `resource` 调 `ingestionTaskAPI.testApiConnection({ dataSourceId, resource })`，展示 HTTP 状态、样本数、recordPath 解析状态和后端返回的前 5 条样本；recordPath 不匹配时复用后端 `API_RUNTIME_RESPONSE_PARSE` 分类和建议。
- 已补 helper 单测覆盖 API 分页/游标构造与编辑回显。

## 影响范围

- `dts-platform-webapp/src/pages/explore/etl/`（创建流 + ingestionFormHelpers）
- 任务提交 payload 对齐契约 v1.2

## 验证

- [x] 单资源任务配置/编辑回显正确
- [x] recordPath 配错时预览给出可读错误
- [ ] 多资源任务配置 UI（当前 payload/执行器支持 `resources`，创建页仍按首个 resource 编辑）
- [ ] mock API 浏览器/IT 证据

## 完成标准

- [ ] 配置出的任务可被 F2 执行器直接执行成功
