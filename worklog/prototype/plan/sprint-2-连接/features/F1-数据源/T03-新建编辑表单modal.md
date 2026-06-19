# T03: 新建/编辑表单 modal

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

落地 `DataSourceFormModal`，支持新建与编辑数据源，字段对齐契约，接 mock。

## 技术设计

- 文件：`app/src/stages/connect/DataSourceFormModal.tsx`（对齐现网 `pages/foundation/DataSourceFormModal.tsx`）。
- 表单：AntD `Form` + `Modal`；两种模式（新建空白 / 编辑回填，编辑模式不回显密钥，留空表示不变更）。
- 字段对齐 `DataSourceUpsertPayload` 契约形状：名称、类型、连接参数（host/port/库名/账号/密钥）、负责人等。
- 校验：系统边界输入校验（必填、端口数值、名称唯一性提示），快速失败给清晰错误。
- 提交：`dataSourcesService.upsertDataSource(payload)` 返回 `Promise<Result<InfraDataSource>>`；成功后关闭 modal 并刷新列表（不可变更新，不就地改 props）。
- modal 内提供「测试连接」按钮，复用 T04 连通测试，提交前可先验证。

## 影响范围

- 新增 `app/src/stages/connect/DataSourceFormModal.tsx`
- 扩展 `app/src/mock/services/dataSourcesService.ts`（`upsertDataSource`）
- 被 `DataSourcesPage`（T01）与 `DataSourceDetailPage`（T02）调用

## 验证

- [ ] 新建提交成功后列表新增一行并刷新。
- [ ] 编辑模式正确回填非敏感字段，密钥留空不覆盖。
- [ ] 必填/端口/唯一性校验生效，错误信息清晰。
- [ ] modal 内「测试连接」可触发 T04 成功/失败态。

## 完成标准

- [ ] modal 支持新建与编辑两模式，字段对齐 `DataSourceUpsertPayload`。
- [ ] 经 `dataSourcesService.upsertDataSource` 提交，输入校验与错误处理完整。
- [ ] 提交走不可变更新，成功刷新列表。
