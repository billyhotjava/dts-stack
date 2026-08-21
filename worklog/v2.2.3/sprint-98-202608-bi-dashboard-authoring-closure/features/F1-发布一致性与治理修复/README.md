# F1：发布一致性与治理修复

**优先级**：P0  **状态**：IN_PROGRESS

## 目标

作者发布时校验的就是屏幕上刚保存的草稿，并能通过真实部门/角色选择和就地组件修复关闭所有阻断项。

## 契约与 UI

- “发布”先调用 `/bi/api/dashboard/save`，用响应 `ordered_cards` 更新页面，再调用 `/{id}/validate`。
- 发布范围显示组织名称/角色说明，提交值仍为 `deptCode`/role `name`。
- blocker 以中文业务提示显示，`components[n]` 解析为卡片名称并在画布标记。
- 历史旧绑定可原样保存或替换；新建/改绑必须指向已发布治理分析。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 保存后校验与目录化发布范围 | IN_PROGRESS | F0 |
| T02 | 治理组件元数据、旧卡片替换与写入门禁 | READY | T01 |

## Definition of Ready

- [x] 请求顺序、字段、错误路径已固定。
- [x] 真实目录和存量旧卡片样本可用。
- [x] 验收可由 Java 测试、source contract 和 mock E2E 执行。
