# IT-01 result

**结果**：PASS_WITH_GAPS
- **platform/webapp 构建树基线**：`239fba2c7`
- **admin 构建树基线**：`8742e2bfe`
- **Compose 运行基线**：`286ffc68a`
- **E2E 验收测试基线**：`9953198de`
**最终镜像**：platform `sha256:70dbc04c...`；webapp `sha256:d067edfa...`；admin `sha256:524a6e79...`

## 已证明

- `/modeling/workbench` 在 DTS 正式布局中展示七个内部模块，无新增 portal 一级菜单。
- 首页、真实计划详情、数据元、业务维度、逻辑模型、指标、工具和关系图均由 canonical owner 页面承载。
- `module/workspaceView` 在模块切换和浏览器刷新后保持；不兼容的子视图会在切换模块时清除。
- “通用工具 → 导入模型包”保留 `module=tools&workspaceView=utilities&modelImport=open`，不再回到丢失上下文的硬编码 URL。
- 页面无 JavaScript error、非导航取消的 request failure 或 API 4xx/5xx。
- 一次性认证用户、管理员快照和 Playwright storage state 均清理为 0。
- 关系图展示非零节点和关系，证明部署后的 PostgreSQL 投影已进入新面板。
- platform/webapp/admin 与菜单软删除均完成真实回切和恢复，两个方向均为 HTTPS 200。

## 现场事实与缺口

- 当前租户自动选中“项目模型Demo”，但 URL 不写入 `planId`；本轮证明默认计划上下文和 `module/workspaceView` 刷新保持，未执行 CAS 写冲突。
- 临时 `ROLE_OP_ADMIN` 仅用于严格只读旅程；本轮不验证创建、保存、发布或物化写权限。
- 系统只有 Chrome 150；Chrome 95 四态证据仍未完成。
- `assetKind/assetId` 的纯函数契约已通过，但 live 对象选择属于 F2。

## 截图

- `workspace-home.png`：默认计划下的工作台首页。
- `workspace-tools-import.png`：导入抽屉与只读边界。
- `workspace-seven-modules.png`：建设计划关系图及非零节点/关系。
