# F2：数据标准真实化

**优先级**：P0  
**状态**：CODE_COMPLETE（最终 Review、构建、部署与 E2E 待 F5）

## 目标

标准管理员在现有五类标准入口中查询、维护、导入和引用真实标准，模型字段可选择真实标准版本。

## 契约

复用 `/modeling/standards/**`、`/modeling/metadata-standards/**`、standard packages、reference codes、glossary、measurement units；所有写审计由既有服务端 owner 产生。

| Task | 状态 |
|---|---|
| T01 接入标准目录和详情 | CODE_COMPLETE |
| T02 接入 CRUD、导入预检与应用 | CODE_COMPLETE |
| T03 接入模型字段标准引用和七态 | CODE_COMPLETE |

## DoR

- [x] 五类页面到现有 API 的映射已冻结
- [x] 导入使用 preview/apply，不模拟成功
- [x] 模型引用保存精确 code/version

## 当前编码证据（2026-08-03）

- 当前实现以原型的五类标准页面为 UI 基线，并通过 `standardsProjectionService` 接入既有标准、码表、词典及映射 owner。
- 所有列表都使用真实加载、空态、错误和权限态；不再保留硬编码记录、假成功或无 owner 写按钮。
- `ModelingWorkbenchPage` 的字段编辑器保存精确标准编码和版本引用。
- 代码和契约测试已完成；当前 bundle 尚未完成最终 Review、构建、部署及真实浏览器 E2E。
