# 按钮与组件矩阵

| 控件 | Owner 组件 | 用户意图 | Handler/API | 状态与失败反馈 | 首个测试 |
|---|---|---|---|---|---|
| 可视化模式 | `ModelingWorkbenchEditor` | 查看/编辑结构化定义 | 只更新 `view=visual`；读取同一 draft | dirty 时直接切换但保留内容；context 失败禁写 | source-contract + URL Vitest |
| 代码模式 | `ModelingWorkbenchEditor` / `AdvancedDbtWorkspace` | 查看/编辑 bundle | 只更新 `view=code`；读取同一 draft files | Monaco 懒加载；无技术权限不请求正文 | source-contract + network smoke |
| 创建新草稿版本 | 工作台工具栏 | 修改 PUBLISHED | `POST .../authoring-drafts` intent=FORK_PUBLISHED | loading；幂等成功；409 刷新；403 无权限 | component + MockMvc |
| 保存草稿 | 统一工具栏 | 保存 visual/code 当前修改 | `PUT .../authoring-drafts/{draftId}` | disabled: 无 dirty/无权限/PUBLISHED；409/412 保留本地内容 | component + service IT |
| 校验 | 统一工具栏 | 一次校验模型、代码、投影和依赖 | `POST .../validate` | 展示 model/code/projection/dependency 四组诊断；失败可定位 | component + validation IT |
| 提交实现 | 统一工具栏 | 生成一致 model/implementation pins | `POST .../commit` | 仅 VALIDATED 可用；幂等；失败无半提交 | component + PostgreSQL IT |
| 原始代码节点 | visual 节点卡 | 修改不可结构化表达式 | 更新对应 managed/raw file 范围 | 明示来源文件/行；unmanaged checksum 冲突拒绝 | golden projection test |
| 刷新 | 工作台工具栏 | 获取远端最新 context | GET context | 有 dirty 时二次确认；不自动覆盖 | component test |
| 发布 | 既有发布按钮 | 创建候选并走生命周期 | 既有 release APIs | 未 commit/校验不通过沿现有 blocker | Sprint-91/93 regression |
| 物化/再次物化 | 既有模型列表/发布弹窗 | 执行目标关系 | 既有 materialization plan/candidate | provenance 不影响计划；保留历史 | IT-07/08 |

## 必须删除或降级为兼容的可见交互

- “接管代码实现”按钮及“本版本不可回退”确认框；
- “转为可视化维护”按钮/概念；
- “当前由代码维护 · 可视化只读”整页提示；
- 基础表单里的“可视化配置 / 手工 dbt SQL”维护方式选择器。

来源可以保留为只读信息，例如“来源：dbt ZIP 导入”，但不能成为禁用编辑器的条件。

