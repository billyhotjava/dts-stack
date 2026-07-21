# 建设规划台账与计划头编辑设计

**日期**：2026-07-21
**状态**：REVIEW
**归属**：Sprint-67 / F2-T05

## 1. 问题与目标

当前数据建设工作台可以列出、创建和查看 WarehousePlan，也可以编辑业务分类、分层策略和来源盘点，但创建后的计划名称、建设目标、建设范围、负责人和负责部门没有前端修改入口。计划数量增加后，右上角下拉框也无法承担搜索、筛选、状态识别和归档管理。

本设计补齐两个互补入口：

1. `/modeling/plans` 建设规划台账，负责跨计划查找、筛选和生命周期操作；
2. 工作台及计划详情的“编辑规划”，负责当前计划的就地修正。

两者只操作 Sprint-65/67 canonical `WarehousePlan`，不复用旧 `/api/modeling/plans` 项目空间聚合，也不创建第二张规划主表。

## 2. 方案比较

| 方案 | 结论 | 说明 |
|---|---|---|
| A. 只在工作台增加编辑弹窗 | 不采用 | 能修正当前计划，但多个计划仍只能在下拉框中盲选，缺少台账和归档入口 |
| B. canonical 台账 + 当前计划就地编辑 | **采用** | 同时解决跨计划管理和创建后修正，复用现有 WarehousePlan API 与权限边界 |
| C. 改造旧“项目空间管理”页面 | 不采用 | 旧页面持有仓库、环境和协作边界，数据源是旧 `/api/modeling/plans`，与建设规划不是同一聚合 |

## 3. 信息架构

```text
数据开发与运维
└─ 数据建模
   ├─ 建模工作台            /modeling/workbench
   └─ 数仓规划
      ├─ 建设规划            /modeling/plans
      └─ 业务分类            /governance/subjects
```

工作台仍是单计划的证据投影和唯一下一步入口；建设规划台账只做查找与管理，不复制九站建设轨迹。两个页面通过 `planId` 互相跳转。

## 4. 页面设计

### 4.1 建设规划台账

页面标题为“建设规划”，主动作是“新建规划”。台账列：

| 列 | 来源 | 说明 |
|---|---|---|
| 规划名称/编码 | WarehousePlan header | 名称可点击进入计划详情；编码只读 |
| 开始方式 | onboardingMode | 从业务目标开始 / 从现有数据开始 |
| 负责人/负责部门 | ownerId/ownerDepartmentId | 部门缺失显示“未设置” |
| 生命周期 | lifecycleStatus | 规划中、待发布、已发布、已归档等客户语言 |
| 当前阶段/首要阻塞 | StageProjection | 独立加载失败时显示“证据未知”，不把计划行变为空 |
| 更新时间 | lastModifiedDate | 若 header 当前契约尚未返回，则本 Task 不新增伪时间列 |
| 操作 | 权限与生命周期派生 | 查看、编辑、归档 |

筛选条件：关键字、生命周期、负责人；默认不展示已归档计划，可显式切换。第一版不做批量归档、导出、复制计划和永久删除。

### 4.2 当前计划编辑

工作台“规划摘要”和计划详情标题区增加“编辑规划”。两个入口复用同一 `WarehousePlanHeaderEditor` 抽屉：

- 可编辑：名称、建设目标、建设范围、负责人、负责部门；
- 只读：计划编码、开始方式、生命周期、tenantId；
- 已发布或已归档：不显示编辑动作，只显示只读原因；
- 保存成功：关闭抽屉，刷新当前 header 和台账行，不重置当前 Tab、`planId` 或 StageProjection；
- 保存失败：保留输入。

负责人使用目录选择器；若目录能力不可用，保留当前负责人并禁止自由文本伪造身份。第一版不实现任意跨部门转派 ACL，后端现有权限仍是最终裁决。

### 4.3 归档

归档属于行级“更多”操作，不与“查看”争夺主动作：

1. 二次确认展示计划名称和影响说明；
2. 调用 canonical `POST /api/modeling/warehouse-plans/{id}/archive`；
3. 成功后从默认活跃列表移除，并提供切换到“已归档”筛选查看；
4. 不提供前端物理删除；
5. 归档当前工作台计划后返回台账，不静默切换到其他计划。

## 5. API 与并发契约

前端补齐现有 canonical API：

```text
PATCH /api/modeling/warehouse-plans/{id}
If-Match: "plan-head:{version}"

POST /api/modeling/warehouse-plans/{id}/archive
If-Match: "plan-head:{version}"
```

更新请求只发送 `name/objective/scope/ownerId/ownerDepartmentId`。`onboardingMode/code/lifecycleStatus/tenantId/version` 不得由表单回写。

并发规则：

- 409/version conflict 时保留表单，展示服务端当前版本；
- 用户可选择“保留当前输入并基于最新版重试”或“放弃并加载最新版”；
- 归档冲突必须重新加载行状态后再确认，禁止自动覆盖；
- 列表刷新失败保留已有台账，显示独立重试，不伪装为空态。

## 6. 权限与状态

- 查看：沿用后端当前租户可见范围；指定 `planId` 不可见时不得回退到其他计划。
- 新建/编辑/归档：要求现有 modeling maintainer 权限。
- 无写权限：保留查看动作，隐藏或禁用写动作并解释原因。
- `PUBLISHED`、`ARCHIVED` 不允许修改计划头；归档接口以服务端实际生命周期校验为准。
- 任何前端按钮状态都不能替代后端 `@PreAuthorize`、租户校验和 ETag CAS。

## 7. 组件边界

- `WarehousePlanLedgerPage`：筛选、台账、空错权状态和行操作编排；
- `WarehousePlanHeaderEditor`：编辑抽屉、字段校验和冲突恢复；
- `WarehousePlanArchiveAction`：归档确认和结果反馈，可内聚在台账页面但不得复制 API 逻辑；
- `warehousePlanApi`：canonical list/get/create/update/archive；
- `warehousePlanViewModel`：生命周期标签、可编辑/可归档判定、路由构造和错误文案。

工作台不扩展成第二张台账；台账不加载和复制完整基线编辑表单。

## 8. 验证设计

1. 前端 API 契约：PATCH/POST archive 携带精确 URL、payload 和 `If-Match`。
2. 纯状态测试：筛选、权限、生命周期、指定计划恢复和冲突选项。
3. 页面 source-contract：canonical 路由、菜单、单主动作、无旧 `/api/modeling/plans` 消费。
4. 后端定向回归：现有 Resource/ApplicationService update/archive 正向、权限、冲突和生命周期测试。
5. Chrome 95：空台账、新建返回、编辑成功、409 保留输入、归档、只读账号、390px 无横向溢出。
6. 前端 production build 在整块代码完成后执行一次，不按单文件修改重复构建。

## 9. 完成判断

- 新用户创建计划后，在当前页面能发现并完成修改；
- 多计划用户可从稳定菜单进入台账并查找计划；
- 编辑与归档只写 canonical WarehousePlan，旧项目空间调用数保持为零；
- 失败、权限、并发和归档状态均有明确恢复；
- 操作手册更新为真实入口和界面截图；
- Sprint-67 从人工测试重开状态重新完成定向验证和 Go/No-Go。
