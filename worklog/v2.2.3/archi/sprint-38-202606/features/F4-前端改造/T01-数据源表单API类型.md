# T01: 数据源表单API类型

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F1-T01（契约 v1.2）

## 目标

`DataSourcesPage` 支持创建/编辑 API 类型数据源：baseUrl/defaultHeaders/TLS/限流 + 鉴权动态表单，由契约接口驱动渲染。

## 技术设计

- 表单由 `GET /api/ingestion/api/contract` 的 `authProviders` 驱动：按 `fields[]`（name/label/type/required/sensitive）动态渲染；`enabled=false` 的 provider 置灰+「即将支持」徽标。
- 敏感字段（`sensitive=true`）：新建必填、编辑回显为掩码占位（留空=不修改）；提交仅传变更项。
- 集成 F1-T04 连接测试按钮：展示 connected/authOk/分类建议。
- 复用现有数据源表单框架与校验风格（参照 JDBC 类型的实现模式）。

## 2026-06-12 进展

- 已接入 API 契约接口：`DataSourceFormModal` 继续通过 `ingestionTaskAPI.getApiConnectorContract/getApiAuthProviders` 驱动鉴权字段。
- 已新增前端连接测试 API：`ingestionTaskAPI.testApiConnection({ dataSourceId })` / `testApiConnection({ sourceConfig, secrets })`，走 platform 代理 `/api/ingestion/api/test-connection`。
- 已在 API 数据源编辑态新增“测试已保存连接”按钮，展示 `connected/httpStatus/sampleCount/elapsedMs` 或失败建议；新建未保存态可用“测试当前配置”直接以表单 raw config+secrets 探活。

## 影响范围

- `dts-platform-webapp/src/pages/foundation/DataSourcesPage.tsx`（+ 新子组件 ApiSourceForm/AuthProviderFields）
- API 契约 service/types

## 验证

- [x] 构建通过：`pnpm build`
- [x] 新建态保存前 raw config+secrets 探活已接入
- [ ] 各 provider 动态表单渲染快照测试；disabled 不可选
- [ ] 编辑时密钥不回显明文（network 面板与 DOM 断言）

## 完成标准

- [ ] 创建→测试连接→保存→编辑全流程可用
