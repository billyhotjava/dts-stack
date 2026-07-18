# Sprint-65：经典数仓规划内核与黄金主线重构

**状态**：IN_PROGRESS
**周期**：2026-07
**类型**：Architecture Convergence / Full-stack Refactor / Controlled Retirement
**总体设计**：[经典数仓规划内核与平台黄金主线设计](assets/classic-warehouse-planning-golden-path-design.md)

## 1. Sprint 目标

把 DTS 的默认建模路径收敛为“经典数仓规划”，建立一个可持久化的 `WarehousePlan` 规划聚合，允许业务驱动和资产驱动两种起点在同一规划基线汇合，并用真实后台证据贯通：

```text
数据连接 -> 来源盘点 -> 数仓规划 -> 数据标准 -> 事实/维度模型
-> 构建/质量/发布 -> 数据资产 -> 指标系统 -> 服务与运维
```

dbt 从“建模模式”调整为高级实现工具；关系建模调整为模型中心的可选关系视图；总线矩阵调整为事实与维度关系的派生分析视图。旧页面、旧路由、旧 session 状态和旧计划表采用受控退役，不进行破坏式删除。

## 2. 交付边界

### 2.1 本 Sprint 必须交付

- canonical `WarehousePlan` 聚合、版本/评审和规划基线持久化；
- BUSINESS_FIRST 与 ASSET_FIRST 两个入口、同一个计划详情和同一门禁；
- 经典数仓分层、事实/维度、粒度、时间语义、来源映射和标准绑定；
- 数据建设工作台的阶段证据投影和唯一下一步；
- 模型中心与高级 dbt 的信息架构、实现所有权和产物回写；
- 旧计划、旧菜单、旧路由和旧 session 事实源的兼容与迁移；
- 双起点、存量迁移、失败修复、回滚和 Chrome 95 集成证据。

### 2.2 明确不做

- 不删除 `modeling_plan*` 旧表；
- 不硬编码 PJM 或其他行业字段；
- 不重建连接、标准、资产、指标或调度的专业能力；
- 不把 AI/manifest 推断直接确认为业务事实；
- 不以页面访问记录计算完成状态；
- 不在没有迁移证据时移除旧菜单 ID 或角色绑定。

## 3. Feature 总览

| Feature | 优先级 | Task 数 | 状态 | 目标 |
|---|---:|---:|---|---|
| [F1-架构边界与受控退役](features/F1-架构边界与受控退役/README.md) | P0 | 3 | READY | 固化唯一概念、所有权和退役护栏 |
| [F2-WarehousePlan持久化与API](features/F2-WarehousePlan持久化与API/README.md) | P0 | 4 | IN_PROGRESS | 建立 canonical 规划聚合和后端契约 |
| [F3-双起点规划基线](features/F3-双起点规划基线/README.md) | P0 | 4 | IN_PROGRESS | 两种入口在同一基线收敛 |
| [F4-经典数仓架构与维度模型](features/F4-经典数仓架构与维度模型/README.md) | P0 | 4 | READY | 形成默认事实/维度设计闭环 |
| [F5-黄金主线与数据建设工作台](features/F5-黄金主线与数据建设工作台/README.md) | P0 | 4 | IN_PROGRESS | 用真实证据显示当前阻塞和下一步 |
| [F6-模型中心与高级dbt分离](features/F6-模型中心与高级dbt分离/README.md) | P0 | 4 | READY | 分离规划设计和工程实现职责 |
| [F7-菜单路由兼容与旧旅程退役](features/F7-菜单路由兼容与旧旅程退役/README.md) | P0 | 5 | IN_PROGRESS | 无损切换新入口并冻结旧事实源；前端收敛为 planId + StageProjection 单状态源 |
| [F8-集成验收与交付证据](features/F8-集成验收与交付证据/README.md) | P0 | 3 | IN_PROGRESS | 提供可复核的迁移与端到端证据 |

**Task 统计**：READY=23，IN_PROGRESS=8，DONE=0，BLOCKED=0

## 4. 推荐执行顺序

```text
F1 -> F2 -> F3
             ├-> F4 --┐
             └-> F5 --┴-> F6 -> F7 -> F8
```

更精确的 Task 依赖见 [`assets/feature-dependency-map.md`](assets/feature-dependency-map.md)。允许并行的前提是上游契约已经通过评审，而不是只创建了文件或接口占位。

## 5. 架构不变量

1. `WarehousePlan` 是唯一规划聚合根，一个计划可以包含多个域、过程、来源和模型。
2. `modeling_warehouse_plan` 是 canonical 运行态主表；旧 `modeling_plan*` 只迁移、兼容和冻结，不新增第三套主表。
3. 起点只决定首次编辑顺序，不能分裂领域模型或后续旅程。
4. 连接、标准、资产、指标、dbt 产物和运行记录仍由原模块持有；规划只持有引用和证据摘要。
5. 阶段完成由后台证据投影产生，前端无权直接标记完成。
6. dbt 不属于规划阶段，不等于建模方法，不可无提示覆盖设计器管理的模型。
7. 总线矩阵和 ER 图都不能成为与 `ModelSpec` 冲突的第二事实源。
8. 自动推断产生候选，人工确认后才进入基线、模型或发布门禁。
9. 旧路由和菜单先兼容、再冻结、后移除；物理删除必须进入后续专项 Sprint。
10. 核心契约禁止出现客户、行业或示例专属字段。
11. 九站 `StageProjection` 是唯一可计算完成度的旅程骨架，`planId` 是唯一新链路上下文；八阶段总览只能映射九站投影，六阶段计划详情只是编辑 Tab。既有四套上下文与阶段导航按 R03、R11-R13 收敛，任何页面不得同时渲染两套阶段导航。

## 6. 主要资产

- [权威总体设计](assets/classic-warehouse-planning-golden-path-design.md)
- [Sprint-65a WarehousePlan 后端实施计划](assets/implementation-plan-65a-warehouse-plan-foundation.md)
- [Sprint-65b WarehousePlan 工作台实施计划](assets/implementation-plan-65b-warehouse-plan-workbench.md)
- [架构与模块边界](assets/architecture-overview.md)
- [建模关键对象单（canonical 词表）](assets/modeling-concept-canon.md)
- [领域与 API 契约](assets/domain-and-api-contract.md)
- [受控退役登记表](assets/controlled-retirement-register.md)
- [Feature/Task 依赖图](assets/feature-dependency-map.md)
- [集成测试与证据计划](it/README.md)

## 7. Sprint 完成标准

- 31 个 Task 的完成条件全部满足，并链接真实代码、测试或运行证据；
- BUSINESS_FIRST、ASSET_FIRST 共用同一套 API、表和计划详情；
- 空库升级、存量库升级、迁移 dry-run、幂等重跑和回滚全部通过；
- 新工作台不会跳转到主题域管理，且主屏仅有一个当前动作；
- 专业页面带 `planId` 进入并能返回同一计划；
- dbt import/compile/test/run 的产物回写模型、血缘和运行证据；
- Chrome 95 端到端覆盖双起点、失败修复、旧深链和权限；
- `git diff --check`、模块测试、前端 build、GitNexus 变更范围检查通过；
- 旧资产只进入兼容或冻结状态，不发生未授权删除；
- R03、R11-R13 前端上下文与导航收敛达成目标状态，单状态源守卫契约在测试基线内长期运行。

## 8. 风险与止损

| 风险 | 触发信号 | 止损动作 |
|---|---|---|
| 两套计划表继续双写 | 同一计划在两表出现不同版本 | 立即冻结旧写入，使用迁移映射核对 |
| 工作台复制专业模块 | 新增重复的连接/标准/指标表单 | 回退为摘要与深链，不合并所有权 |
| 起点分裂为两套产品 | 两个入口出现不同状态或发布流程 | 统一到 baseline API 和 plan detail |
| dbt 覆盖设计模型 | 同一模型出现双向静默写入 | 强制 implementation ownership 冲突门禁 |
| 前端显示假完成 | 完成状态来自页面点击或缓存 | 阻止发布并改用 evidence projection |
| 兼容切换破坏权限 | 旧菜单 ID 被删除或角色绑定丢失 | 恢复旧 ID，使用 target route 映射 |

## 9. 分期执行建议（2026-07-18 评审增补）

30+ 任务、关键路径纵深 7 级且全 P0，建议按两期执行以保留降级路径：

| 期 | 范围 | 出口判据 |
|---|---|---|
| 65a（先立后破） | F1 → F2 → F3 + F5-T01（内核、双起点、证据投影后端）；同时冻结旧 `modeling_plan*` 写入 | canonical 聚合可用、双起点同基线、旧写入零增量 |
| 65b（切换收尾） | F4 / F6 深化 → F5-T02/T03/T04 → F7 切换（含 T05 骨架收敛）→ F8 验收 | 九站投影成为唯一状态源、三种视图映射契约 GREEN、迁移与 Chrome95 证据齐备 |

若不分期，至少将 F7-T04（清理）与 F8-T02（Chrome95 全量）降为 P1 可滑项；F5 工作台与既有"端到端数据产品工作台"的关系为：**e2e 旅程保留为跨域总览首页，数据建设工作台是建模域主场，两者通过 planId 互通**（详见 F5 README）。
