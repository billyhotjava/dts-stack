# T02：建立隐藏 SQL 的业务可视化

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T01、F1/T03～T04

## 目标

在同一 revision 下提供逻辑表/字段、业务或模型依赖以及物理结果摘要；普通界面隐藏 SQL/dbt 技术正文，并明确设计事实、投影事实和运行事实的 provenance。

## Contract-first

- **逻辑结构**：字段名、类型、nullable、key/role、standard、declared provenance 和 drift。
- **依赖视图**：面向业务的 model/source 依赖与阻断原因；普通界面不展示完整 dbt technical node、文件路径或 SQL 片段，不猜动态 edge。
- **物理摘要**：物化状态、目标 relation、observed columns 和漂移摘要；样例数据由 T04/D12 控制。
- **隐藏清单**：SQL/Jinja、compiled SQL、macro、dbt 文件树、project path、完整技术 DAG 在普通视图中为 0。
- **错误路径**：source-only 无 enforced schema contract 或字段缺显式 `data_type` 时，最多显示只读声明结构且 apply BLOCKED，并提示补齐完整 contract 或上传 artifact-rich ZIP；动态/macro/package 依赖不可验证时显示受影响闭包和恢复动作；不得提供手工重建技术字段入口或生成虚假列。
- **可访问性**：键盘切换、aria tab、Chrome95。

## 验证

- [ ] BUSINESS_VISUAL_EDIT、BUSINESS_VISUAL_READ、BLOCKED 三种 UI fixture。
- [ ] 逻辑、依赖和物理摘要的 revision/checksum/provenance 完全一致且可解释。
- [ ] DOM、截图、可访问名称和错误详情均不泄露 SQL 正文或技术文件路径。

## Definition of Done

- [ ] 用户可看清“设计的表”“依赖”“是否已物化”，无需理解 SQL/dbt 技术细节。
