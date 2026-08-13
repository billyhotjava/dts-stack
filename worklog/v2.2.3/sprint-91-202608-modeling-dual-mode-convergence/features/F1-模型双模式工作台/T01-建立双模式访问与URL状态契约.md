# T01: 建立双模式访问与 URL 状态契约

**优先级**: P0
**状态**: IN_PROGRESS（源码与聚焦自动化完成；真实浏览器 IT 待补）
**依赖**: F1/T03（需要 `OPEN_DBT_PREVIEW` 能力码就位）

> **首版标 READY 已作废**：首版假定 TECHNICAL 表示已能为 DESIGNER 模型返回 `OPEN_DBT_PREVIEW`，源码事实相反（复核结论 C）。在 T03 落地前，本 Task 的输入契约不完整，不满足 DoR。

## 目标

先用纯函数与契约测试锁定 visual/code 的 URL、能力和只读规则，避免 UI 重构继续使用 `Boolean(selectedModel)` 猜测权限。

## 技术设计 (Contract-first)

- **输入契约**: `ModelRepresentationView | null`（BUSINESS/TECHNICAL）、`canMaintain:boolean`、`requestedView:string|null`、`legacyOpen:string|null`。
- **输出契约**:
  - `ModelingView = 'visual' | 'code'`；
  - `ModeAccess = { visible:boolean; editable:boolean; action:'EDIT'|'PREVIEW'|'BLOCKED'; reason:string }`；
  - URL 规范化结果只允许 `view=visual|code`。
- **数据流**: lifecycle/representation 响应 → access resolver → URL resolver → 工作台渲染；不写数据库。
- **错误路径**: representation 为 null/失败时 fail closed；未知 `view` 回落 visual；`open=advanced` 仅在 modelSpecId 有效时映射 code。
- **复用点**: 复用 `allowedActions`、`capabilityReasons` 和当前 `syncWorkbenchUrl`；不新建权限表。
- **能力码映射**（依赖 T03）:

  | ownership | TECHNICAL allowedActions | code 视图 `ModeAccess` |
  |---|---|---|
  | DESIGNER_GENERATED | `['OPEN_DBT_PREVIEW']` | `{visible:true, editable:false, action:'PREVIEW'}` |
  | DBT_MANAGED | `['OPEN_ADVANCED_DBT']` | `{visible:true, editable:true, action:'EDIT'}` |
  | 任一，投影不可信 | `[]` + reasons | `{visible:true, editable:false, action:'BLOCKED', reason}` |
  | 只读账号（无 `CATALOG_MAINTAINERS`） | TECHNICAL 不授权 | `{visible:true, editable:false, action:'BLOCKED', reason:'需维护权限'}` |

  `action:'CONVERT'` **不由本函数产出**——接管可用性来自 F2 的 transition validate，不塞进 capability 层（ADR-91-10）。
- **兼容**: 保留 source-contract 对 `open=advanced` 的旧入口断言，再增加归一化断言。

## 影响范围

- `ModelingWorkbenchPage.tsx`
- 新增工作台 mode access 纯函数/测试（放现有 prototype services 或 contracts 目录）
- `prototypeReplacement.source-contract.test.ts`

## 验证 (RED→GREEN)

- [ ] RED：当前 DESIGNER 模型 code access 仍被错误视为可编辑。
- [ ] 单测覆盖 DESIGNER/DBT、维护/只读、加载/失败、未知 URL、旧 deep-link 的组合矩阵。
- [ ] 断言 Tab 切换函数不会调用写 API，也不会改变 model/implementation revision。

## Definition of Done

- [ ] 访问矩阵只有一个纯函数 owner，组件不重复写条件。
- [ ] URL 与 compatibility source-contract 全绿。
- [ ] 无 UI 样式改动、无路由/菜单新增。
