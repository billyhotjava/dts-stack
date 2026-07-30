# IT-01 result

**结果**：PASS_WITH_GAPS
**代码基线**：`36d03d1d44ab`
**最终镜像**：`sha256:7b939a81115eeda33c7e37800cf65e9ae3a507eaae0b9f228e9cf30a77130403`

## 已证明

- `/modeling/workbench` 在 DTS 正式布局中展示七个内部模块，无新增 portal 一级菜单。
- 首页、规划空态、数据元、业务维度、逻辑模型、指标、工具和关系图均由 canonical owner 页面承载。
- `module/workspaceView` 在模块切换和浏览器刷新后保持；不兼容的子视图会在切换模块时清除。
- “通用工具 → 导入模型包”保留 `module=tools&workspaceView=utilities&modelImport=open`，不再回到丢失上下文的硬编码 URL。
- 页面无 JavaScript error、非导航取消的 request failure 或 API 4xx/5xx。
- 一次性认证用户、管理员快照和 Playwright storage state 均清理为 0。
- 最终镜像已真实回切旧镜像并恢复，两个方向均为 HTTPS 200。

## 现场事实与缺口

- 当前租户没有建设计划，因此本轮只能证明规划真实空态，不能证明有代表数据时的详情、CAS 冲突和 `planId` 刷新恢复。
- 临时 `ROLE_MODEL_MAINTAINER` 身份在 UI 中仍表现为只读；本轮只验证可达性，没有冒充写权限成功。
- 系统只有 Chrome 150；Chrome 95 四态证据仍未完成。
- `assetKind/assetId` 的纯函数契约已通过，但 live 对象选择属于 F2。

## 截图

- `workspace-home.png`：无计划真实空态。
- `workspace-tools-import.png`：导入抽屉与只读边界。
- `workspace-seven-modules.png`：关系图 canonical panel。
