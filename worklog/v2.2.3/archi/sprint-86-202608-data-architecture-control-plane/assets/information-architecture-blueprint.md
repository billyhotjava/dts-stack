# F4 目标信息架构、页面能力与兼容路由蓝图

**状态**：APPROVED

**形成日期**：2026-08-09

**评审入口**：IT-05

**评审人**：xiezm（已登记，兼任产品决策、前端/平台 owner）

## 1. ADR-86-09 已批准选择

新增一个一级产品入口 **“数据架构”**，但不新增平行数据表、API 或 CRUD：现有业务分类、数据域、业务过程、数仓分层、数据集市和主题域组件迁入一个权威工作区，旧入口只做兼容跳转。

这是 DTS “默认不新增菜单”规则的显式例外，理由是：这六类字典已经由建模、资产、指标、质量和权限共同消费，继续放在“数据建模/数仓规划”内部会与 ADR-86-08 的 canonical owner 冲突。例外只增加一个产品入口，不增加第二套能力。

## 2. 目标一级导航与职责

| 一级入口 | 唯一职责 | 主要页面形态 | 明确不做 |
|---|---|---|---|
| 数据架构 | 维护平台全局架构字典 | 层级树/分组 + Table、平面 Table、详情抽屉 | ModelSpec、SQL、物化、资产治理 |
| 数据建模 | 计划、模型设计、revision、候选、构建/物化意图 | 搜索/筛选 + 可多选 Table；独立详情编辑器 | 架构字典 CRUD、物理资产治理 |
| 数据资产 | 发现、检索、归域、责任、分级、发布/健康查看 | 域导航 + 搜索/筛选 Table + 详情抽屉 | 架构字典 CRUD、模型 revision 编辑 |
| 数据指标 | 指标定义、版本、派生、发布 | Table + 指标编辑器 + 只读业务上下文选择 | 业务分类/域/过程 CRUD |
| 数据质量 | 规则、绑定、运行、报告 | Table + 规则/运行详情 + 资产选择 | 资产身份、架构字典 CRUD |
| 主数据管理（后续） | 业务主数据金记录及来源映射 | 后续独立 Sprint | 不在 Sprint-86 新增菜单或页面 |

## 3. 数据架构工作区

### 3.1 路由协议

权威入口：`/data-architecture?view=<view>&active=<stableId>`

| view | 对象 | 页面形态 |
|---|---|---|
| `business-domains` | 业务分类 + 数据域 | 两层树/分组 + 同页 Table/详情；搜索始终可用 |
| `processes` | 业务过程 | 分类/域筛选 + Table + 抽屉 |
| `layers` | 数仓分层 | 搜索 + Table + 详情；内置/自定义状态可区分 |
| `marts` | 数据集市 | 业务分类筛选 + Table + 抽屉 |
| `subjects` | 主题域 | 数据集市筛选 + Table + 抽屉 |

首屏默认 `view=business-domains`。`active` 只接受稳定 ID；名称和中文文案不进入 URL 关联键。

### 3.2 线框

```text
┌ 数据架构 ─────────────────────────────────────────────────────────────┐
│ [业务分类与数据域] [业务过程] [数仓分层] [数据集市] [主题域]          │
│ [范围筛选▼] [状态▼] [搜索名称/编码/描述____________] [刷新] [新建]    │
├───────────────────────────────────────────────────────────────────────┤
│ 层级对象 view:  ┌ 业务分类/数据域树 ┐ ┌ Table + 行操作 + 分页10 ───┐ │
│                 │ 搜索 + 两层节点    │ │ 查看 编辑 退役 引用关系     │ │
│                 └───────────────────┘ └────────────────────────────┘ │
│ 平面对象 view:  ┌──────────── Table + 详情抽屉 + 分页10 ───────────┐ │
│                 └───────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────┘
```

- 10～50、两层按单一形态设计；不按数量在树/Table 两套页面间切换。
- `SEARCH_THRESHOLD=8` 的旧实现事实只决定旧控件何时显示搜索框；目标工作区搜索始终可见。
- 写按钮只对 ADR-86-17 方案 A actor 展示，但后端 guard 才是安全边界。

## 4. 数据建模工作台

### 4.1 模型列表替代记录树

模型记录选择从左侧目录树迁为搜索/组合筛选 + Table；层级树只作为“业务分类/数据域”筛选控件，不承担记录选择和批量操作。

```text
┌ 模型工作台 ───────────────────────────────────────────────────────────┐
│ [计划▼] [分类/域▼] [模型类型▼] [分层▼] [状态▼] [搜索____________]   │
│ 已选 7/100  [清空] [生成物化候选]                         [新建模型] │
├──┬──────────┬──────┬────┬────────┬────────────┬────────────┬────────┤
│□ │模型名称  │类型  │层  │修订状态│最新物化    │物理资产状态│操作    │
│□ │时间维度表│维度表│DWD │r2 READY│attempt 2 成功│HEALTHY     │编辑…  │
│□ │项目事实表│事实表│DWD │r4 BLOCK│尚无          │UNKNOWN     │查看…  │
├──┴──────────┴──────┴────┴────────┴────────────┴────────────┴────────┤
│ 第 1/… 页  每页 10                       [上一页] [下一页]             │
└───────────────────────────────────────────────────────────────────────┘
```

### 4.2 选择与详情规则

- 行点击/“编辑”打开现有模型编辑器，URL 保留 `modelSpecId`、`planId`、`tab`；切换记录不整页 reload。
- checkbox 只控制批量交付，不改变当前编辑对象。
- 选择可跨页保留，但必须显示“已选清单”；只提供“选择当前页”，不提供无界“选择全部结果”。
- 最多选择 100 个 root models；过滤条件变化不静默清空，先提示“保留/清空已选”。
- 表格列明确区分：模型 revision、candidate、latest attempt、physical relation、asset eligibility。
- 行操作提供“查看物化历史”“再次物化”；再次物化进入新 attempt，不覆盖旧记录。

### 4.3 批量物化交互

1. 用户选择 1～100 个模型并点击“生成物化候选”。
2. 前端请求服务端预检，弹出候选预览：root、自动依赖、已满足依赖、blockers、拓扑层级。
3. 只要存在 blocker，服务端不创建候选；UI 可让用户显式取消无资格 root 后重新预检。
4. 用户确认后原子创建 candidate，再点击“创建并运行”。
5. 进度抽屉按拓扑层级显示逐项 `QUEUED/RUNNING/SUCCESS/FAILED/SKIPPED_DEPENDENCY_FAILED/CANCELLED`。
6. 失败项显示错误码、attempt、开始/结束/last heartbeat、修复入口；合法重试产生新 attempt。
7. 完成后刷新模型列表的 latest materialization，同时保留 candidate/attempt 历史。

当前已有 `/api/modeling/plans/{planId}/release-candidates`、`/{candidateId}/rematerialize`、
`/materializations?modelSpecIds=` 与 Workbench/EntryEvidence 契约，下一实施 Sprint 优先扩展它们，不新增第二套批量 API。

## 5. 数据资产页面

- `/catalog/assets`：治理概览；不保留重复域侧栏，域切换由矩阵/范围选择器承载。
- `/catalog/assets/ledger`：保留业务分类/数据域侧栏作为主范围导航，主体为搜索 + Table；窄屏在工具栏提供 TreeSelect。
- “全部资产”“未归域”“按域”始终可达；未选域也能批量归域。
- 默认分页 10；切换 pageSize 必须回到第 1 页。
- 统计投影 FRESH 时显示精确数；STALE/REBUILDING 显示 `截至 <asOf>`；兼容 fallback 触顶显示 `≥5000`，不得展示伪精确值。
- 批量归域返回逐项成功/失败和审计锚点；失败项保留选择并允许重试。

## 6. 指标与质量页面

- 指标工作台继续使用 `/data-modeling/metrics/*` 的 canonical 页面，不恢复已退役 `/modeling/metric-workbench`。
- 创建/编辑指标依次选择业务分类 → 数据域 → 业务过程，metricType 单独选择，metricGroupCode 为可选分组。
- 选择器旁提供“查看定义”，跳转 `/data-architecture?view=...&active=<id>`；消费者页面不提供字典编辑按钮。
- 质量页面继续从物理资产投影选择数据集；不从 ModelSpec 名称或指标 category 拼接 datasetId。

## 7. 旧路由与页面承接

### 7.1 `/data-modeling/planning/*`

| 旧路由 | 目标 | 兼容要求 |
|---|---|---|
| `/data-modeling/planning/business-categories` | `/data-architecture?view=business-domains` | 保留 query/hash；旧菜单变兼容跳转 |
| `/data-modeling/planning/domains` | `/data-architecture?view=business-domains` | 旧 `active` 映射稳定 ID |
| `/data-modeling/planning/processes` | `/data-architecture?view=processes` | domain 参数转为 active/filter |
| `/data-modeling/planning/layers` | `/data-architecture?view=layers` | 不改变 canonical layer code |
| `/data-modeling/planning/marts` | `/data-architecture?view=marts` | 分类筛选参数保留 |
| `/data-modeling/planning/subjects` | `/data-architecture?view=subjects` | mart/active 参数保留 |
| `/data-modeling/planning/spaces` | 保留在数据建模 | 隐藏单空间占位，不迁入架构字典 |
| `/data-modeling/planning/system` | 保留在数据建模 | 规划运行参数，不属于架构字典 |

### 7.2 `/governance/subjects`

| 旧 tab | 承接页面 | 参数映射 |
|---|---|---|
| `scope`（建模范围） | `/data-modeling/planning/spaces?view=baseline&tab=categories` | `active → domainId`，保留 `returnPlanId` |
| `data-marts` | `/data-architecture?view=marts` | `active → businessCategoryId` |
| `details`（主题域信息） | `/data-architecture?view=subjects` | `active → businessCategoryId/filter` |
| `governance` | `/catalog/assets?domain=<active>` | 保留治理下钻；指标入口指向 canonical 指标工作台 |

现有五处活引用（planning repair route、治理中心、数据管理工作台、帮助中心、资产空态）先统一改到兼容解析器；三条既有 E2E 继续走旧 URL 验证参数无损，再增加目标 URL 断言。观察期通过前不删除 `SubjectAreasPage`。

## 8. 页面四态与错误反馈

| 状态 | 数据架构 | 模型 Table/批量 | 资产/指标/质量 |
|---|---|---|---|
| Loading | skeleton/表格占位，保留筛选框 | 表格 skeleton；历史状态不清空 | 保留筛选与上一结果，标刷新中 |
| Empty | 解释对象和“新建”入口；无权限则只读说明 | 区分“无模型”与“筛选无结果” | 未归域/无结果语义分开 |
| Error | 错误码、correlationId、重试 | 逐项 blocker/attempt 错误；禁止无限 loading | 统计失败不阻断台账；写失败回滚 UI |
| Success | Table/详情与引用计数 | 候选、拓扑、逐项结果、历史 observation | 状态、asOf、治理动作可见 |

## 9. Chrome 95、可访问性与 Source Contract

- 只使用 Chrome 95 支持或已转译的语法/CSS；不依赖容器查询、`:has()` 等未验证特性。
- checkbox、Tabs、Drawer、错误提示和进度状态具备键盘焦点与可读 aria label。
- 下一实施 Sprint 更新：数据建模导航、模型工作台、资产概览/台账、`SubjectAreasPage` 兼容跳转、指标选择器对应的 `*.source-contract.test.ts`。
- 真实验收覆盖 320/768/1024/1440 宽度；E2E 必须从菜单点击，不以直接 URL 代替入口验收。

## 10. IT-05 通过清单

- [x] 接受新增一级“数据架构”入口这一 B1 显式例外，并确认不新增平行 CRUD。
- [x] 接受模型目录改为可多选 Table，树只作层级筛选。
- [x] 接受跨页显式选择、上限 100、候选预检和逐项运行反馈。
- [x] 接受旧 planning 路由与 `/governance/subjects` 四 Tab 的映射。
- [x] 接受资产概览/台账、指标、质量的职责边界。
- [x] 接受四态、分页 10、Chrome 95、source-contract 与菜单点击 E2E 要求。
- [x] 确认本蓝图不代表 UI 已实现或浏览器验收已执行。
