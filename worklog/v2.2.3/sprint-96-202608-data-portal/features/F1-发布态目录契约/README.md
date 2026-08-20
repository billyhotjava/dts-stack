# F1：发布态目录契约

**优先级**：P0  
**状态**：READY

## 目标

让门户通过一个向后兼容的服务端参数获得“可读、非归档、有当前发布版本”的大屏目录，并消除列表逐屏读取版本的 N+1。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| REST | `GET /api/screens?publishedOnly=true` | `domainId?:string`、`domainUnassigned:boolean=false`、`publishedOnly:boolean=false` |
| Response | `ScreenListItem[]` | id/name/description/domainId/classification/publishedVersionNo/publishedAt/permissions |
| Repository | batch current published lookup | 输入 screenIds；返回 `List<AnalyticsScreenVersion>` |
| Error | 401/403 | 无会话 401；不可读不出现在列表，详情仍 403 |

## UI 绑定

`DataPortalPage` 首次加载和刷新调用 `analyticsApi.listScreens({publishedOnly:true})`；管理页不传参数，行为不变。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 发布态列表参数与批量版本水合 | READY | F0/T01 |

## Definition of Ready

- [x] 参数/响应/Repository 签名已钉死。
- [x] UI 调用点和错误路径已命名。
- [x] RED 测试可用 ScreenResourceIT 验证。
