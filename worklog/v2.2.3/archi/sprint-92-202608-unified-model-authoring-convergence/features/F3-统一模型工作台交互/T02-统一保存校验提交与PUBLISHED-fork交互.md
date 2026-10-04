# T02：统一保存、校验、提交与 PUBLISHED fork 交互

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：T01、F1/T03

## 目标

在同一工具栏提供保存草稿、校验、提交实现和 PUBLISHED fork，并让 visual/code 共享动作状态、诊断与回执。

## 技术设计（Contract-first）

- **输入契约**：provider draft state；F1 save/fork、F4 validate/commit REST。
- **输出契约**：按钮 enablement 仅由 status/permission/dirty/validation freshness 决定；成功更新 provider pins/ETag/receipt。
- **数据流**：点击/快捷键 → provider command → REST → normalize failure → 更新 context/draft。
- **错误路径**：403 权限说明；409/412 保留未保存内容并提供比较/刷新；422 分组诊断；5xx 显示 correlationId/重试。
- **复用点**：现有 Button/Status/RequestState、failure adapter、Monaco Ctrl/Cmd+S。

## UI 交互规格

- DRAFT：`保存草稿 / 校验 / 提交实现`；提交仅对当前 validatedChecksum 可用。
- PUBLISHED：上述三按钮不出现，只显示“创建新草稿版本”。
- busy 防双击；成功信息包含 revision，不显示内部 ownership。

## 影响范围

模型工作台统一工具栏、authoring provider、failure adapter、快捷键和 component/source-contract tests；不修改发布弹窗或模型列表物化入口。

## 验证（RED→GREEN）

- [ ] Vitest 覆盖按钮矩阵、快捷键、错误和幂等 replay。
- [ ] source-contract 断言不调用 ownership transition/convert。
- [ ] UI 走查：visual 改字段→code 改文件→save→validate→commit。

## Definition of Done

- [ ] 一个用户操作链，不再有 visual save 与 dbt commit 两套割裂心智。
- [ ] PUBLISHED fork 成功原地进入 DRAFT，旧发布 revision 可回溯。
- [ ] 冲突/失败不清空本地草稿。
