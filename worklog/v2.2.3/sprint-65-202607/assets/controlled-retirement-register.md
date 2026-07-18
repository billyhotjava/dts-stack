# 受控退役登记表

## 1. 状态定义

| 状态 | 含义 |
|---|---|
| INVENTORY | 已登记，尚未切换 |
| COMPATIBLE | 新旧入口兼容，记录旧入口使用 |
| FROZEN | 旧入口只读，新写入已收敛 |
| REMOVAL_CANDIDATE | 满足移除条件，等待专项审批 |
| RETIRED | 已移除且回归通过 |

Sprint-65 的最高目标状态是 `FROZEN` 或 `REMOVAL_CANDIDATE`，不得直接删除旧表。

## 2. 登记清单

| ID | 旧资产 | 类型 | 当前问题 | 替代物 | Sprint-65 目标 | 回滚方式 | 移除条件 |
|---|---|---|---|---|---|---|---|
| R01 | 建模工作台旧入口 | 菜单/路由 | 跳转主题域管理 | 数据建设工作台 | COMPATIBLE | Feature Flag 恢复旧 target | 新旅程两版本稳定 |
| R02 | `/governance/subjects` 作为数仓规划首页 | 页面职责 | 治理、规划、矩阵堆叠 | 计划列表/详情 | FROZEN | 保留专业主题域深链 | 无主入口调用 |
| R03 | `warehousePlanningContext` session 事实源 | 前端状态 | 刷新/跨端不可信 | WarehousePlan API | FROZEN | 只读 fallback | 存量 context 迁移完成 |
| R04 | `DBT_NATIVE` 建模方法 | 枚举/文案 | 工具与方法混层 | DBT_MANAGED 实现方式 | FROZEN | 枚举 adapter | 数据映射与回归通过 |
| R05 | 页面顺序式旅程完成度 | 前端投影 | 访问即完成 | stage projection API | FROZEN | 旧展示 flag | 证据投影稳定 |
| R06 | 独立可写总线矩阵 | 数据/页面 | 可能成为第二真值 | 模型关系派生概览 | COMPATIBLE | 旧矩阵只读 | 关系迁移核对通过 |
| R07 | `modeling_plan*` 旧写路径 | DB/API | 与 vNext 计划割裂 | canonical warehouse plan | FROZEN | 只读和映射回退 | 两周期零写入且审批 |
| R08 | `modeling_warehouse_plan` 过程/分层单值粒度 | DB 模型 | 无法表达方案级聚合 | 主表 + 绑定子表 | COMPATIBLE | legacy columns 只读保留 | 绑定回填完全 |
| R09 | 旧规划 API | API | 两套写模型 | warehouse-plans API | COMPATIBLE | adapter 转发 | 调用审计零消费者 |
| R10 | 旧建模概念卡/顶部多卡片 | UI | 挤压工作区 | 首次说明折叠、单主动作 | FROZEN | 样式 flag | 可用性验收通过 |
| R11 | e2e 旅程白名单中的规划四参数（planningId/warehouseLayer/modelingMode/processId）与建模域旅程条 | 前端上下文/导航 | 与 planId 竞争、双阶段导航并存 | `planId` 统一上下文；e2e 旅程条在建模域降级为返回链接 | FROZEN | 白名单参数保留只读透传 | planId 链路两版本稳定且无消费者读取旧参数 |
| R12 | `businessModelingContext` | 前端上下文 | 第三套上下文事实源 | planId 上下文 + 兼容映射 | FROZEN | 映射函数保留 | 存量深链全部命中兼容映射 |
| R13 | `modelingJourneyContext` 与 SemanticWorkspaceFrame 四阶段导航（2026-07-17 引入） | 前端上下文/导航 | 与黄金主线九站竞争的骨架 | 数据建设工作台 + planId；Frame 若保留仅作为模型中心内部导航且不再自持上下文 | COMPATIBLE | Frame 保持现状渲染 | F5 工作台上线且建模页单骨架契约通过 |

## 3. 每个退役项必须附带的证据

- 当前调用者、路由、菜单 ID、角色绑定、数据量和负责人；
- 替代物可用性和正反向映射；
- 切换 Feature Flag、默认值和回滚时限；
- 迁移前后行数、主键、版本和引用差异；
- 浏览器深链、权限和刷新恢复证据；
- 零活跃消费者统计；
- 最终移除审批，不得只凭代码搜索结果。

## 4. 数据迁移护栏

1. 先 dry-run 生成差异报告；
2. 为每条旧记录生成稳定迁移映射；
3. 幂等 upsert，重复执行不得新增重复计划；
4. 冲突记录进入人工处理清单，不能“最后写入胜出”；
5. 迁移前后保留校验和与审计事件；
6. 回滚只切换读取和入口，不删除 canonical 新数据；
7. 旧表物理删除另立 Sprint 和备份审批。
