# F2：数据标准真实化

**优先级**：P0  
**状态**：CODE_COMPLETE（部署/E2E 待 F5/T02）

## 目标

标准管理员在现有五类标准入口中查询、维护、导入和引用真实标准，模型字段可选择真实标准版本。

## 契约

复用 `/modeling/standards/**`、`/modeling/metadata-standards/**`、standard packages、reference codes、glossary、measurement units；所有写审计由既有服务端 owner 产生。

| Task | 状态 |
|---|---|
| T01 接入标准目录和详情 | DONE |
| T02 接入 CRUD、导入预检与应用 | DONE |
| T03 接入模型字段标准引用和七态 | CODE_COMPLETE |

## DoR

- [x] 五类页面到现有 API 的映射已冻结
- [x] 导入使用 preview/apply，不模拟成功
- [x] 模型引用保存精确 code/version

## 本轮证据

- `StandardsWorkspace.tsx` 已删除硬编码业务记录、`BackendPendingButton` 和 `UiStageNotice`，列表失败不会回退示例数据。
- 字段标准复用 `/modeling/standards/**`；标准代码复用 reference codes；命名词典复用 glossary；标准映射按 metadata standard 引用关系只读展示。
- 字段标准和标准代码支持新建、编辑、归档；命名词典支持新建、编辑。没有独立词根或全局映射写契约的控件明确禁用并显示原因。
- 标准包导入严格执行 zip → preview → 阻断检查 → apply，逐文件显示新增、更新和失败原因。
- 聚焦测试：`pnpm exec vitest run src/pages/data-modeling/standards/standardsWorkspaceAdapter.test.ts src/pages/data-modeling/standards/StandardsWorkspace.source-contract.test.ts src/pages/data-modeling/standards/StandardsDialogs.test.tsx`，8/8 通过。
- `ModelingEditor` 已读取真实标准选项，保存精确 `standardCode/standardVersion` 并在重载时保留字段标准绑定。
- 标准代码 `bizCatalog/stdLevel` 分别映射，名称编辑不会互相覆盖；码表/词典不再展示 owner 无法持久化的输入，聚焦对话框/adapter 测试 `7/7`。
- 代码/契约已完成；真实标准包、权限与审计仍待部署后联合 E2E。
