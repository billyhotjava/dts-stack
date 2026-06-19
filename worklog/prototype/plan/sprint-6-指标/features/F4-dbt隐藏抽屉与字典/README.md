# F4: dbt 隐藏抽屉与字典

**优先级**: P1
**状态**: READY

## 目标

兑现本 sprint 的**核心架构动作——dbt 下沉**：把现网顶层菜单的 `DbtFileBrowser`/`ModelPipeline` 降级为阶段④的「查看生成的 dbt」**只读抽屉**（消费 S4 F4 在画布上生成的 dbt 映射，**不暴露文件树式编辑入口**）；同时收纳字典域 `SubjectAreas/Glossary/Elements/ReferenceCodes`（主题域/术语/参考码），作为指标与语义建模的术语支撑。普通用户全程在指标/语义/SQL 建模层完成工作，**只有想"看底层生成了什么"时才打开这个只读抽屉**——这是普通用户与 dbt 唯一的、被动的、只读的接触点。命名对齐现网 `DbtFileBrowserPage` / `SubjectAreasPage` / `GlossaryPage` / `ElementsPage` / `ReferenceCodesPage`，service 用 `dbtService`（只读）、`glossaryService`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-查看生成的dbt只读抽屉.md) | 「查看生成的 dbt」只读抽屉 | P1 | READY | S4 |
| [T02](./T02-主题域术语参考码.md) | 主题域/术语/参考码 | P1 | READY | S4 |

## 完成标准

- [ ] 现网 `DbtFileBrowser`/`ModelPipeline` 的顶层菜单入口**被移除**；dbt 仅以阶段④内「查看生成的 dbt」只读抽屉形式存在。
- [ ] 抽屉**消费 S4 F4 的生成映射**（画布转换 + 语义/SQL 建模发布所生成的 dbt model），**只读**呈现（model 名/SQL/lineage 摘要），**无新建/编辑/删除/运行**任何写操作入口。
- [ ] 抽屉可从 F2 语义发布、F3 ModelPipeline、F1 指标详情等处以「查看生成的 dbt ▸」按钮被动唤起；不在主导航占位。
- [ ] `SubjectAreasPage`/`GlossaryPage`/`ElementsPage`/`ReferenceCodesPage` 在阶段④可路由可访问，接 `glossaryService`，CompactTable 默认 10 条/页。
- [ ] 主题域/术语与 F2 语义主题、F1 指标口径相互引用（术语支撑）。
- [ ] 普通用户主流程（F1/F2/F3）全程**不接触 dbt**；唯一接触点是本 Feature 的只读抽屉。
