# F2：业务可视化与高级 dbt 实现分层

**优先级**：P0
**状态**：CODE_COMPLETE / E2E_PENDING
**依赖**：F1

## 目标

让用户在现有模型工作台内完成业务可视化设计、选择或确认 dbt 物化实现并查看物理结果；普通可视化隐藏全部 SQL/dbt 技术正文。技术维护者可在同一模型详情的“数据实现”阶段显式进入高级 dbt 实现，修改只通过隔离草稿提交为新实施修订。

**切片边界**：P0 先交付统一模型上下文、隐藏 SQL 的业务可视化和显式高级技术只读视图；高级草稿提交、物理预览与完整历史/冲突 UX 属于 P1，不能反向阻断 P0 表示切片。

## UI/UX 规格

- **入口**：现有 `/data-modeling/dimensions/workbench?modelSpecId={uuid}`；不新增菜单、独立清单或第二个模型中心。
- **页面上下文栏**：模型、Model revision/checksum、Implementation revision/checksum、ownership、stage、candidate。
- **普通可视化**：逻辑设计｜业务/模型依赖｜物理资产；SQL/Jinja、macro、project path、compiled SQL、完整 dbt DAG 不渲染。物理资产默认展示 serving 结构，用户点击后才读取受控样例。
- **高级 dbt 实现**：仅在“数据实现”阶段显式进入，与普通可视化互斥；DBT_MANAGED 的 SQL 可受控编辑，技术结构投影只读。
- **物化规则**：DESIGNER_GENERATED 选择/确认物化策略后由系统生成隐藏 dbt 制品；DBT_MANAGED 选择已固定的高级/外部实现修订。两者均经统一 gateway，物化动作不改变 ownership。
- **样例边界**：默认 100、最大 500，不分页/导出/持久缓存；普通视图只读 serving，高级维护者可读成功 candidate 并固定显示“候选结果，非正式资产”；历史 revision 不提供样例行。
- **四态**：无实施版本/未物化、加载中、解析/权限/密级/脱敏/冲突错误、修订固定成功态。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 接入统一模型详情上下文 | P0 | CODE_COMPLETE | F1/T01 |
| T02 | 建立隐藏 SQL 的业务可视化与高级技术只读入口 | P0 | CODE_COMPLETE | F1/T03～T04、T01 |
| T03 | 接入显式高级 dbt 实现与版本提交 | P1 | CODE_COMPLETE | T01～T02 |
| T04 | 接入候选固定的物理表结构与样例数据 | P1 | CODE_COMPLETE | F4/T02～T03 |
| T05 | 完成脏状态、冲突、阻断、权限和历史修订 UX | P1 | CODE_COMPLETE | T01～T04、F1/T05 |

## Definition of Ready

- [x] D01～D03 已确认。
- [x] D12 已确认。
- [x] D11 延期结论已于 2026-08-02 确认；当前 UI 不渲染导出、Git 或 ownership conversion 操作。
- [ ] 拉取 P0/T01～T02 前只冻结模型上下文、BUSINESS/TECHNICAL read scope 与技术正文为 0 的契约；拉取 P1/T03～T05 前再分别冻结 draft/validate/commit、physical-preview、ETag/CAS 与完整状态 UX，不反向阻断 P0。

## 完成标准

- [ ] 页面任何时刻只展示一个明确 revision/checksum 上下文。
- [ ] 普通可视化在所有状态下均不渲染 SQL/dbt 技术正文。
- [ ] 高级 dbt 实现保存不改共享 projectDir 当前值、不直接 run/publish。
- [ ] 选择 dbt 物化不静默改变 implementation ownership。
- [ ] 物理数据预览符合 [`assets/catalog-serving-and-preview-contract.md`](../../assets/catalog-serving-and-preview-contract.md)：显式加载、serving/candidate 隔离、历史无行、默认 100/最大 500、权限/密级/列策略/no-store/无导出。
