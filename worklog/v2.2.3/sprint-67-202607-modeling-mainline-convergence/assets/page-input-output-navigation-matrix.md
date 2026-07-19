# 页面输入、输出与跳转矩阵

## 1. 主线页面

| 顺序 | 页面/目标路由 | 可直接浏览 | 创建/编辑输入条件 | 核心输入 | 输出产物 | 唯一主动作/下一步 |
|---:|---|---|---|---|---|---|
| 1 | 建模工作台 `/modeling/workbench` | 是 | 无 | 名称、目标、开始方式、负责人 | WarehousePlan + planId | 完善规划基线 |
| 2 | 计划基线 `/modeling/plans/:planId/baseline` | 否，需 planId | 计划可编辑 | 业务分类、分层策略、来源盘点 | PlanningBaseline | 进入模型设计 |
| 3 | 业务分类 `/governance/subjects` | 是 | 新增/编辑需治理权限 | 名称、编码、父分类、负责人 | catalog domain/domainId | 返回原计划 |
| 4 | 数据标准：数据元 `/governance/standards/elements`、码表 `/governance/standards/reference`、度量单位 `/governance/standards/units`、命名词典 `/governance/standards/glossary` | 是 | 新增/编辑需标准治理权限 | 各标准正文、编码、版本、责任人 | standardId/unitId/code/version | 返回原计划或模型字段 |
| 5 | 维度目录 `/modeling/dimensions` | 是 | 新建需 planId+domainId | 名称、维度键、层级、来源 | DIMENSION ModelSpec | 完善维度表 |
| 6 | 模型中心 `/modeling/models` | 是 | 新建需 planId+domainId+modelType | 四类表对应表单 | ModelSpec revision | 关联字段标准 |
| 7 | 模型详情 `/modeling/models/:modelSpecId` | 否 | modelSpecId 可访问 | 粒度、来源、字段、维度/上游引用 | 可实现 ModelSpec | 进入实现与验证 |
| 8 | 高级实现 `/studio/sql-modeling` 或 `/modeling/dbt-files` | 是 | 编辑需 planId+modelSpecId+revision | SQL/dbt、环境、selector | artifact/compile/test/run evidence | 提交发布审核 |
| 9 | 发布审核（模型详情 release Tab） | 否 | 当前 revision 产物齐备 | 质量、权限、所有权、评审意见 | 发布记录、资产、血缘 | 创建/关联指标 |
| 10 | 指标工作台 `/modeling/metric-workbench` | 是 | 新建需已发布模型或数据集 | 原子指标、周期、修饰词、派生关系 | 指标定义与版本 | 查看资产/服务/运维 |

数据元、码表和命名词典复用现有页面；度量单位是 Sprint-67 需补齐的标准模块能力。`/governance/standards/units` 在 F6-T01 的 API、页面和权限验收通过前不得进入生产菜单。

## 2. 每页状态契约

| 页面 | 空状态 | 阻塞状态 | 失败状态 | 无权限状态 | 恢复行为 |
|---|---|---|---|---|---|
| 建模工作台 | “创建第一个建设计划” | 无 | 保留列表缓存并允许重试 | 仅显示可访问计划 | 恢复最近 planId |
| 计划基线 | 指向业务分类或来源盘点 | 显示一个首要 blockerCode | 不改变确认状态 | 只读摘要和申请入口 | 回到原 Tab |
| 业务分类 | “创建业务分类” | 父分类失效/编码冲突 | 树保持可见 | 隐藏写操作 | `returnTo` 返回计划 |
| 数据标准 | 分类型显示“创建第一个…” | 编码冲突/版本失效/引用漂移 | 保留列表与未提交表单 | 隐藏写操作 | `returnTo` 返回计划或模型字段 |
| 维度目录 | “登记第一个维度” | 缺 planId 时先选计划；缺 domainId 时选分类 | 保留筛选和表单草稿 | 只读目录 | 恢复 domainId |
| 模型中心 | “创建明细/维度/汇总/应用表” | 按模型类型显示缺失输入 | 保留台账 | 只读模型 | 恢复 planId+筛选 |
| 高级实现 | 提示从模型详情进入 | revision 漂移/所有权冲突 | 保留编辑内容和重试 | 禁止保存/运行 | 恢复 modelSpecId+revision |
| 指标工作台 | 引导先发布模型 | 无可用模型/数据集 | 保留已加载指标 | 只读指标 | 恢复模型引用 |

## 3. 菜单信息架构

```text
数据开发与运维
└─ 数据建模
   ├─ 建模工作台                  /modeling/workbench
   ├─ 数仓规划
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

新链路只允许：`planId`、`domainId`、`modelSpecId`、`revision`、`modelType`、`returnTo`。

`processId`、`objectId`、`planningId`、`warehouseLayer`、`modelingMode` 只能由兼容适配器读取，不得由新页面继续写入 URL。`returnTo` 必须校验为站内白名单路径，防止开放重定向。
