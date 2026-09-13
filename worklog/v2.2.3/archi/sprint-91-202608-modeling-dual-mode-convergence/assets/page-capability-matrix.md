# Sprint-91 页面能力矩阵

| 页面 | 规范路径 | Owner | 当前能力 | Sprint-91 变化 | API/数据契约 | 风险 |
|---|---|---|---|---|---|---|
| 数据建模工作台 | `/data-modeling/dimensions/workbench` | `ModelingWorkbenchPage.tsx` | 选择模型、URL 恢复、dirty guard、打开高级 dbt | 增加 `view=visual\|code`，兼容 `open=advanced`；不新增路由 | lifecycle + BUSINESS/TECHNICAL representations | REAL |
| 模型编辑区 | 同上 | `ModelingWorkbenchEditor.tsx` | 基本信息、字段、实现绑定、保存、发布入口 | 唯一“可视化模式/代码模式”分段控件；补基础来源、维度引用与 dependency status；移除重复高级入口 | `allowedActions`、ModelSpec refs、dependency snapshot、lifecycle pins | PARTIAL → REAL |
| 可视化转换区 | 同页 visual implementation panel | 既有 editor/compiler owner | 目前只能回显部分 mappings，缺少从空编辑与完整聚合 | 增加结构化 mapping/cast/filter/dedup/join/groupBy/aggregation；超范围显式接管代码 | designer settings + F6 snapshot + compiler preview/commit | GAP → REAL |
| 高级 dbt 工作区 | 同页 code panel | `AdvancedDbtWorkspace.tsx` | create/save/validate/commit、ETag 冲突 | DESIGNER 只读预览与显式接管；DBT 使用懒加载 Monaco | dbt preview/transition + 既有 dbt-drafts API | PARTIAL → REAL |
| 可视化只读投影 | 同页 visual panel | 工作台 editor/read-only form | DBT 业务表示可读 | 删除伪编辑/回切入口，按 capability reasons fail closed | BUSINESS representation | PARTIAL → REAL |
| 模型列表/物化计划 | 现有维度/事实/汇总/应用模型列表 | 现有模型列表 + 新薄 plan dialog | 单条/批量选择、状态、物化历史 | 单表/批量共用 BUILD/REUSE/BLOCK preview；创建候选后回到既有发布工作区 | F8 preview + existing candidate/dispatch/observation | PARTIAL → REAL |
| 发布对话框 | 同页现有发布入口 | `ModelPublishDialog.tsx` + `ModelReleaseWorkflowPanel.tsx` | 构建、质量、评审、发布登记、上线就绪 | 回归手工/可视化/ZIP/接管，消费 plan/dependency checksum；不新增代码模式发布按钮 | release candidate + execution binding + governance correlation | REAL |

## 路由与入口边界

- 菜单、静态路由、动态 resolver 均不新增。
- 旧 `open=advanced` 只做 URL 兼容归一化，不保留第二个按钮 owner。
- 已删除的 `SqlModelingPage`、`DbtFileBrowserPage` 与 `/etl/dbt/files` 不恢复。
