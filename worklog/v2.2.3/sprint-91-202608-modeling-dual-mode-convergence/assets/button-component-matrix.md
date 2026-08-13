# Sprint-91 控件与组件矩阵

| Control | 类型 | Owner | 用户意图 | Handler / API | 状态 | 首个测试 |
|---|---|---|---|---|---|---|
| 可视化模式 / 代码模式 | Segmented | 新的小型 mode switch，由 `ModelingWorkbenchEditor` 编排 | 在同一模型切换表现视图 | 更新 `view` query；读取 representation，不写库 | loading；可用；disabled+reason；error/retry；dirty confirm | URL/dirty/10 次切换无额外请求 |
| 接管代码实现 | 主按钮 | DESIGNER code panel | 将维护方式显式转为代码 | validate → confirm → commit transition | hidden for readonly；disabled+reason；validating；confirm；409/412；success | 取消零写入、不可逆告知、单次 commit |
| 文件列表 | 列表 | `AdvancedDbtWorkspace` | 选择主模型/schema/STG 文件 | 本地选择；无写 API | empty；loading；selected；per-file diagnostics | 3 文件预览分组、128 文件边界 |
| 保存文件 | Button / Ctrl-S | `AdvancedDbtWorkspace` + lazy `DbtCodeEditor` | 保存当前草稿 | 既有 save-files + ETag | disabled readonly/clean；saving；409；saved | Ctrl-S、旧 ETag 不覆盖 |
| 校验 | Button | `AdvancedDbtWorkspace` | 静态校验项目 | 既有 validate | disabled dirty；validating；diagnostics；passed | path/line marker 与 project-level fallback |
| 提交实现 | Button | `AdvancedDbtWorkspace` | 生成新 implementation revision | 既有 commit + checksum/idempotency | disabled until validated；committing；conflict；committed/read-only | edit→save→validate→commit 状态机 |
| 可视化只读说明 | Status/Alert | visual panel | 理解 DBT 模型为何不可编辑 | BUSINESS capability projection | trusted read-only；BLOCKED reasons；request error/retry | 无写控件、无回切 action id |
| 发布 | 既有按钮 | `ModelPublishDialog` | 进入统一候选发布链 | release candidate APIs | 由 `allowedActions` 驱动；三角色职责分离 | DESIGNER/DBT/接管 DBT 共用同一 API |

## Chrome 95 约束

- Monaco 仅在有维护权限且 `view=code` 时动态加载；visual 首屏不得请求对应 chunk。
- 不引入 Monaco worker、`:has()`、container query、`toSorted` 或新 viewport units。
- 模式控件、确认框和编辑区在 1366×768 与窄屏下均须可滚动、可聚焦。
