# 页面输入、输出与跳转矩阵

## 1. 主线页面

| 顺序 | 页面/目标路由 | 可直接浏览 | 创建/编辑输入条件 | 核心输入 | 输出产物 | 唯一主动作/下一步 |
|---:|---|---|---|---|---|---|
| 1 | 建设规划台账 `/modeling/plans` | 是 | 新建/编辑/归档需规划维护权限 | 关键字、生命周期、负责人；计划头字段 | canonical WarehousePlan 台账与 plan-head version | 新建规划 |
| 2 | 建模工作台 `/modeling/workbench` | 是 | 无 | 名称、目标、开始方式、负责人 | WarehousePlan + planId | 完善规划基线 |
| 3 | 计划基线 `/modeling/plans/:planId/baseline` | 否，需 planId | 计划可编辑 | 业务分类、分层策略、来源盘点 | PlanningBaseline | 进入模型设计 |
| 3A | 数据接入/元数据同步 | 是 | 建设 ODS 需连接与接入权限；纳入规划需 planId | 数据库源或 API 采集任务、映射、目标技术层 ODS_RAW/ODS_STANDARDIZED | 目录资产、API Landing、接入运行、SourceBinding 候选 | 同步/试跑并加入当前规划 |
| 4 | 业务分类 `/governance/subjects` | 是 | 新增/编辑需治理权限；进入模型需正式 planId | 名称、编码、父分类、负责人 | 全局 catalog domain/domainId；不创建 WarehousePlan | 返回原计划；无计划时前往建设规划台账 |
| 5 | 数据标准：数据元 `/governance/standards/elements`、码表 `/governance/standards/reference`、度量单位 `/governance/standards/units`、命名词典 `/governance/standards/glossary` | 是 | 新增/编辑需标准治理权限；浏览与维护无规划门禁 | 各标准正文、编码、版本、责任人 | standardId/unitId/code/version；字段绑定由 ModelSpec 保存 | 返回原计划或模型字段 |
| 6 | 维度目录 `/modeling/dimensions` | 是 | 新建需 domainId 和治理权限；planId 只作为返回上下文 | 名称、定义、业务分类、负责人、层级语义 | DimensionDefinition | 创建维度表 |
| 7 | 模型中心 `/modeling/models` | 是 | 新建需 planId+domainId+modelType | 名称、用途；DIMENSION 额外选择 DimensionDefinition | ModelSpec DRAFT revision | 完善逻辑设计 |
| 8 | 模型详情 `/modeling/models/:modelSpecId` | 否 | modelSpecId 可访问 | 逻辑设计、数据实现、物理资产三个阶段 | ModelSpec + ModelImplementation + PhysicalAssetRevision | 按阶段保存、验证或发布 |
| 9 | 高级实现 `/studio/sql-modeling` 或 `/modeling/dbt-files` | 是 | 编辑需 planId+modelSpecId+revision | SQL/dbt、环境、selector | artifact/compile/test/run evidence | 提交发布审核 |
| 10 | 发布审核（模型详情 release Tab） | 否 | 当前 revision 产物齐备 | 质量、权限、所有权、评审意见 | 发布记录、资产、血缘 | 创建/关联指标 |
| 11 | 指标工作台 `/modeling/metric-workbench` | 是 | 新建需已发布模型或数据集 | 原子指标、周期、修饰词、派生关系 | 指标定义与版本 | 查看资产/服务/运维 |

数据元、码表和命名词典复用现有页面；度量单位由 F6-T01 补齐。四类标准 owner 均是全局目录，不因缺少计划而阻断。某业务分类是否纳入计划及 readiness 由 WarehousePlan baseline 持有；字段标准绑定由模型详情持有，owner 页面不得生成第二份 page-wide/session 草稿。

FACT 表单中的“目标数仓分层”属于当前模型产物；“上游来源分层”只属于已选择的物理输入。草稿可暂不选择输入；进入实现前必须满足当前计划有效物理来源或锁定 revision 的上游 ModelSpec 至少一种。来源选择器不得要求用户选择尚未生成的目标表。

维度目录不再读取 DIMENSION ModelSpec，也不展示来源、目标层、SCD 实现或物化字段。模型新建只创建最小 DRAFT；粒度和字段属于逻辑设计，物理资产/上游模型/生成器属于数据实现，目标表和运行血缘属于物理资产阶段。API 必须先由采集任务生成并登记 Landing 物理资产，再从当前计划来源中选择。

类型与目标层不再自由组合：DIMENSION/FACT 自动投影 DWD，SUMMARY 自动投影 DWS，APPLICATION 自动投影 ADS。ODS_RAW/ODS_STANDARDIZED/STG 不在四类模型目标层下拉中；“建设 ODS”目标入口为数据接入，已有 ODS 表则从元数据同步/来源盘点纳入，该入口 UI 尚待 F3-T07 实现。上游选择器按类型过滤：FACT dependsOn 仅 FACT@DWD，维度另走 dimensionRefs；SUMMARY 为 DIMENSION/FACT@DWD 或 SUMMARY@DWS；APPLICATION 为任意合法 DWD/DWS/ADS 四类模型。服务端按同一矩阵复验。

## 2. 每页状态契约

| 页面 | 空状态 | 阻塞状态 | 失败状态 | 无权限状态 | 恢复行为 |
|---|---|---|---|---|---|
| 建设规划台账 | “创建第一个建设规划” | 无 | 保留已有行并允许独立重试 | 只显示可访问计划和查看动作 | 恢复筛选与精确 planId |
| 建模工作台 | “创建第一个建设计划” | 无 | 保留列表缓存并允许重试 | 仅显示可访问计划 | 恢复最近 planId |
| 计划基线 | 指向业务分类或来源盘点 | 显示一个首要 blockerCode | 不改变确认状态 | 只读摘要和申请入口 | 回到原 Tab |
| 业务分类 | “创建业务分类” | 父分类失效/编码冲突；无计划只限制进入模型 | 树保持可见 | 隐藏写操作 | 白名单 `returnTo` 返回计划；否则前往规划台账 |
| 数据标准 | 分类型显示“创建第一个…” | 编码冲突/版本失效/引用漂移，不显示缺规划整页 blocker | 保留列表与未提交表单并允许重试 | 隐藏写操作 | 白名单 `returnTo` 返回同一计划或模型字段 |
| 维度目录 | “登记第一个业务维度” | 缺 domainId 时选择业务分类；无 planId 不阻断目录维护 | 保留筛选和表单草稿 | 只读目录 | 恢复 domainId 和可选 returnTo |
| 模型中心 | “创建明细/维度/汇总/应用表” | 按模型类型显示缺失输入 | 保留台账 | 只读模型 | 恢复 planId+筛选 |
| 模型详情 | “先完成逻辑设计” | 数据实现缺输入、输入漂移、API Landing 未就绪 | 保留各阶段草稿并局部重试 | 只读三阶段详情 | 恢复 planId+modelSpecId+activeStage |
| 旧 ODS/STG 模型详情（F3-T07 后续目标，尚未实现专属分类/UI） | “旧技术层模型（只读）” | 分类完成后复用 `MODEL_SPEC_LEGACY_READONLY`，展示迁移建议 | 保留审计/血缘 | 永久隐藏写操作 | 返回来源盘点或技术实现 |
| 高级实现 | 提示从模型详情进入 | revision 漂移/所有权冲突 | 保留编辑内容和重试 | 禁止保存/运行 | 恢复 modelSpecId+revision |
| 指标工作台 | 引导先发布模型 | 无可用模型/数据集 | 保留已加载指标 | 只读指标 | 恢复模型引用 |

## 3. 菜单信息架构

```text
数据开发与运维
└─ 数据建模
   ├─ 建模工作台                  /modeling/workbench
   ├─ 数仓规划
   │  ├─ 建设规划                /modeling/plans
   │  └─ 业务分类                /governance/subjects
   ├─ 数据标准
   │  ├─ 数据元                  /governance/standards/elements
   │  ├─ 公共码表                /governance/standards/reference
   │  ├─ 度量单位                /governance/standards/units
   │  └─ 命名词典                /governance/standards/glossary
   ├─ 维度建模
   │  ├─ 维度目录                /modeling/dimensions
   │  ├─ 模型中心                /modeling/models
   │  └─ 高级建模（SQL/dbt）     /studio/sql-modeling
   └─ 数据指标
      └─ 指标工作台              /modeling/metric-workbench
```

菜单用于稳定访问专业能力，不展示强制顺序；顺序只由计划详情中的 StageProjection 和唯一主动作表达。

## 4. 兼容路由

| 旧路由 | 兼容目标 | 参数映射 | 退出条件 |
|---|---|---|---|
| `/modeling/semantic/objects` | `/modeling/dimensions` | 保留 planId/domainId；objectId 映射 legacyRef | 两版本零活跃调用 |
| `/modeling/semantic/models` | `/modeling/models` | 保留 planId/modelSpecId | 菜单与消费者归零 |
| `/modeling/semantic/metrics` | `/modeling/metric-workbench` | 保留 planId/modelSpecId | 指标工作台完成接管 |
| `/modeling/semantic/publish` | `/modeling/models?view=release` | modelId 映射 modelSpecId | 发布记录迁移完成 |
| `/modeling/semantic/runs` | `/ops/instances` | modelId 映射 modelSpecId | 运维查询接管 |
| `/modeling/semantic/subjects` | `/governance/subjects` | active/domainId 归一为 domainId | 静态/动态路由无消费者 |

## 5. 跳转参数白名单

新链路只允许：`planId`、`domainId`、`dimensionDefinitionId`、`modelSpecId`、`revision`、`modelType`、`activeStage`、`returnTo`。

`processId`、`objectId`、`planningId`、`warehouseLayer`、`modelingMode` 只能由兼容适配器读取，不得由新页面继续写入 URL。`returnTo` 必须校验为站内白名单路径，防止开放重定向。
