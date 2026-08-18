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
| 基础来源 | Selector | `ModelImplementationBindingFields` | 选择 ODS 物理来源或一个上游模型 | ModelSpec save + F6 dependency resolver | empty；loading；selected+pin；stale；403 read-only | ODS/上游二选一、resolved version、ETag 冲突 |
| 维度引用 | Relation list/drawer | 同一实现绑定区 | 为 FACT 添加 DIM 固定修订及关联键 | ModelSpec `dimensionRefs` + F6 resolver | empty；searching；selected；invalid type；cycle/stale | FACT 同时保留基础来源+DIM，不按名称关联 |
| 字段映射与转换 | Table + structured rows | visual implementation panel | 配置 mapping/cast/filter/dedup/join/groupBy/aggregation | designer settings save/preview/commit | empty；dirty；validating；preview；blocked；committed | 白名单、schema drift、checksum、无自由 SQL |
| 依赖对账 | Status/Details | visual/code implementation panel | 查看 declared/parsed 依赖是否一致 | F6 dependency snapshot projection | MATCHED；MISSING；UNDECLARED；STALE；error/retry | dependency checksum、稳定 blocker、无客户端编辑 |
| 物化 / 再次物化 | Row action | 模型列表 | 对当前模型生成依赖计划 | F8 preview | loading；BUILD/REUSE；BLOCK；stale refresh | 单表 ADS 闭包、二次物化身份不变 |
| 批量物化 | Toolbar button | 模型列表 | 对多选模型合并依赖闭包 | 同一 F8 preview | disabled empty；previewing；ready；blocked；stale | 分页选择去重、与单表同 checksum |
| 创建候选 | Dialog primary action | materialization plan dialog | 确认计划进入统一发布链 | existing candidate API + `materializationPlanChecksum` | disabled blockers；creating；stale；success | 服务端重算、不能提交 ordered entries |
| 发布 | 既有按钮 | `ModelPublishDialog` | 进入统一候选发布链 | release candidate APIs | 由 `allowedActions`/command guard 驱动；状态显式推进 | DESIGNER/手工/ZIP/接管共用同一 API，无自动审批/发布 |

## Chrome 95 约束

- Monaco 仅在有维护权限且 `view=code` 时动态加载；visual 首屏不得请求对应 chunk。
- 不引入 Monaco worker、`:has()`、container query、`toSorted` 或新 viewport units。
- 模式控件、确认框和编辑区在 1366×768 与窄屏下均须可滚动、可聚焦。
- 来源/维度选择器、结构化转换表和物化计划长列表必须使用现有兼容滚动方案，不使用 container query、`:has()` 或仅新浏览器支持的 API。
