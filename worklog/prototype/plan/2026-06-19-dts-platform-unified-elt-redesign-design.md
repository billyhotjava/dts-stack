# dts-platform 统一 ELT 旅程重构 · 设计文档

> 状态：已与用户（架构师）逐节确认
> 日期：2026-06-19
> 范围：**只做前端原型**，纯前端 + mock API，不做后台
> 产物位置：`worklog/prototype/`（工程在 `source/`，计划文档在 `plan/`）

---

## 1. 背景与问题

dts-platform 现网（`source/dts-platform-webapp`，React 19 + Ant Design 5 + Vite 的 slash-admin 底座）功能已经很全，约 60 个页面，但**按"职能筒仓"组织**，菜单由后端 `dts-admin` 下发再按筒仓分组。架构师视角的核心病灶：

1. **IA 按"管理后台模块"切，而不是按"用户要完成的事"切**——各功能各自独立，没有统一入口（工作台只是门户聚合页，不是引导式入口），没有优先级、没有"下一步"。
2. **指标能力分裂**在 `modeling` 与 `governance` 两个筒仓。
3. **dbt 裸露**给用户（`DbtFileBrowser`/`ModelPipeline` 直接是顶层菜单）。
4. **ELT 能力零散**：Transform / Orchestration / ScriptStudio / EltConsole 分散在 `explore/etl` 下。

架构师目标：设置好数据源与连接之后，应能沿一条 **数据源 → 可视化 ELT → 资产 → 可视化指标设计** 的黄金主线走完，把 dbt 隐藏在底部。

## 2. 目标与非目标

**目标**
- 用全新 IA 把现网全部筒仓重新编排为一条**可走通的黄金主线 + 平台级旁路区**。
- 交付**可复用的 React 骨架原型**（与现网同栈，组件未来可回植）。
- 纯前端：mock API 层复刻现网契约、预留接口、可注入样例数据。
- 全量重皮：现网每个筒仓都要在新 IA 里有归属。

**非目标**
- 不写后端、不接真实服务（保留一键切换开关）。
- 不在本原型里追求现网每个页面的全部细节字段；以验证新架构、走通主线为先。

## 3. 关键决策（已确认）

| 决策点 | 选择 |
|---|---|
| 原型形态 | 独立的可复用 React 骨架工程（新建 `worklog/prototype/app`） |
| IA 范式 | **项目/工作空间 + 阶段向导**（项目内 连接→集成→资产→指标 阶段进度条） |
| 覆盖范围 | **全量重皮**所有筒仓 |
| 视觉方向 | **Swiss 网格工作台**（国际主义/瑞士风，亮色为主，高信息密度，单一功能强调色） |
| 兼容性约束 | **Chrome 95**（部分客户只支持）——禁用 oklch / `:has()` / 容器查询 / subgrid |
| 画布 | `@xyflow/react` v12（现网同款，已在 legacy 构建跑通） |
| mock 层 | **复刻现网 `*Service.ts` 契约**返回 `Result<T>`（非 MSW） |
| 组织模型 | **三层：平台 → 工作区(=部门) → 项目(=交付目标)**（2026-06-20 确认，见 §3a） |
| 数据源归属 | **混合制**：全所级系统(PLM/ERP/QMIS)平台层共享登记；部门本地源工作区层登记（2026-06-20 确认） |

## 3a. 组织模型（多部门 / 多租户）

> 2026-06-20 增补。回答"项目为主线时如何容纳多个部门"。

**部门对应"工作区"而非"项目"**——项目=有始有终的交付目标（阶段状态可派生），部门=常驻组织单元、同时跑多个项目。若部门=项目会粒度错配，黄金主线无法收敛。

```
平台 Platform（网信中心统管，跨部门）
  ├─ 共享基础设施：连接器 / JDBC 驱动 / 物理数据源(PLM·ERP·QMIS) / 安全·治理·运维
  ├─ 全局资产目录（跨部门可发现，按权限可见）
  └─ 指标商店 / 领导驾驶舱（跨部门聚合消费）
        ▼
工作区 Workspace = 部门 / 处室（组织边界：成员、角色权限、配额、本地源登记、项目集合）
        ▼
项目 Project = 一个交付目标（黄金主线 连接→集成→资产→指标）
```

**三个连带修正：**
1. **"连接"阶段语义**：项目内"连接"= 从工作区可用源中**选取并绑定**（缺源时发起接入申请）；物理注册/连通/密钥下沉到 平台/工作区 层（呼应密钥统一管控）。
2. **资产/指标跨部门**：写/发布 scoped 到工作区/项目；目录为平台级只读发现、指标商店平台级聚合（领导驾驶舱跨部门）；跨部门取数走授权。
3. **旁路区天然平台级**：Serve/Govern/Security/Ops 不 scoped 到项目，坐实在平台层——与既有 IA 自洽，仅在"项目"之上补"工作区"一层，顶栏由单级项目切换器升级为「工作区 ▸ 项目」两级。

## 4. 技术架构

### 4.1 工程结构

```
worklog/prototype/
├── source/                   # 新 React 工程
│   ├── src/
│   │   ├── shell/            # 项目外壳：项目切换器 + 阶段导航轨 + 顶栏
│   │   ├── stages/           # ① connect ② integrate ③ assets ④ metrics（黄金主线）
│   │   ├── platform/         # 旁路区：serve / govern / security / ops / settings
│   │   ├── mock/             # API 层：契约 + 内存 fixtures + 延迟模拟 + 开关
│   │   ├── ui/               # Swiss 设计系统（tokens + 原子组件）
│   │   └── canvas/           # 可视化 ELT 画布（@xyflow/react 封装）
│   ├── vite.config.ts        # 搬现网 legacy 配置：plugin-legacy + chrome>=95
│   └── tools/postcss/        # 搬 legacy-css-fallbacks（oklch→hsl 兜底安全网）
└── plan/                     # sprint 实施计划 + 设计文档（本次产出）
```

### 4.2 选型（全部对齐现网，零兼容风险）

| 维度 | 决策 | 理由 |
|---|---|---|
| 框架 | React 19 + TS + Vite + Ant Design 5 | 与现网同栈，组件可回植 `dts-platform-webapp` |
| Chrome 95 | 整套搬 `@vitejs/plugin-legacy`（`chrome>=95`）+ `legacy-css-fallbacks` postcss | 现网已验证；CSS 源码层仍手写 HSL/hex token，不依赖兜底 |
| 拖拽画布 | `@xyflow/react` v12 + `@dnd-kit` | 现网同款，已在 legacy 构建跑通 |
| 血缘/关系图 | `@antv/g6` | 现网同款 |
| API 层 | 分域 `*Service.ts` 返回 `Promise<Result<T>>`，背后内存 fixtures + 可调延迟；`VITE_USE_MOCK` 开关一键切真实 axios | 复刻现网 `src/api/services` 契约形状；"预留接口 + 注入样例数据"一次满足 |

## 5. 全量 IA 映射（现网 ~60 页 → 新架构）

核心思想：**4 个阶段 = 项目内黄金主线**（有顺序、有"下一步"、有进度）；**Serve/Govern/Security/Ops/Settings = 平台级旁路区**（随时可达，但不在线性旅程里）；**dbt 折叠进阶段④做隐藏引擎**。

| 新架构 | 收纳的现网页面 |
|---|---|
| **项目门户**（替代工作台，有优先级/下一步引导） | LeaderOverview · DataManagementWorkbench · WorkflowCenter · 个性化 · 项目选择/新建 |
| **① 连接** Connect | DataSources(+Detail/FormModal) · ConnectorRegistry · JdbcDrivers · AccessChanges · TaskScheduling |
| **② 集成** Integrate（画布中心） | **统一可视化 ELT 画布**(新) 收编 → Transform/TransformCreate/Detail/History · EltConsole · Orchestration(+Runs) · ScriptStudio(高级) · QueryWorkbench/SqlIde(探索查询) |
| **③ 资产** Assets | DataSearch · Datasets(+Detail) · DataProducts · Lineage(Graph/Columns/Diff/Impact/Import) · Metadata · Quality(+Report/Rules) · AssetOwnership/AssetGrant/MyGrants |
| **④ 指标** Metrics（dbt 隐藏底层） | IndicatorCenter/List/Store/Template/Dashboard/MyDashboard · Semantic(ModelingCenter/Models/Objects/Metrics/Subjects/Runs/Publish) · SqlModeling/ModelTemplates/ModelPipeline · SubjectAreas/Glossary/Elements/ReferenceCodes · **DbtFileBrowser→"查看生成的 dbt"抽屉** |
| **旁路·数据服务** Serve | ApiServices · DataProducts(服务) · BiLinks · Tokens · BusinessConsumption |
| **旁路·治理** Govern | GovernanceCenter · PermissionAudit · IndicatorStore(治理视角) · QualityRules(管理) |
| **旁路·安全** Security | data-security · DatasetAccessApproval |
| **旁路·运维** Ops | OpsOverview/Instances/Backfill/AlertLog/LogCenter · ReleaseGovernance · PlatformEventObservability · AuditEvidence |
| **旁路·设置** Settings | settings · sys |

**三处关键架构动作**
1. **指标能力收口**——`modeling` + `governance` 的指标/语义能力合并进阶段④。
2. **dbt 下沉**——`DbtFileBrowser/ModelPipeline` 降级为阶段④的"高级/查看产物"抽屉。
3. **ELT 收编**——零散的 Transform/Orchestration/Script/EltConsole 统一到阶段②的一张画布 + 列表视图双模。

## 6. 项目外壳 + 阶段向导 UX

```
┌──────────────────────────────────────────────────────────────┐
│ DTS  ▸ 销售准备项目 ▾      ⌘K 搜索        🔔   崔耀文 ▾        │ 顶栏:项目上下文
├────────────┬─────────────────────────────────────────────────┤
│ ✓ ① 连接   │  阶段 ② 集成 · 可视化 ELT          [画布|列表] ⇄ │
│ ● ② 集成   │  ┌──────── 节点面板 ────────┐   ┌── 属性抽屉 ──┐ │
│ ○ ③ 资产   │  │ 源 清洗 连接 聚合 输出   │   │ 选中节点配置 │ │
│ ○ ④ 指标   │  └──────────────────────────┘   │ 映射/SQL/过滤│ │
│ ───────    │     (画布区见 §7)                └──────────────┘ │
│ ▾ 平台     │  ────────────────────────────────────────────── │
│  服务/治理 │  ▶运行   调度   运行历史   日志 dock              │
│  安全/运维 │                                                   │
│  设置      │                                                   │
└────────────┴─────────────────────────────────────────────────┘
```

- **左轨 = 黄金主线**：4 阶段带状态点（`✓`完成 / `●`进行中 / `○`待开始）。状态由项目数据派生：连接=已连通≥1 源、集成=转换作业跑通≥1、资产=发布数据集≥1、指标=发布指标≥1。下方分隔线后是折叠的"平台"旁路区。
- **解决"没有优先级/下一步"**：项目门户用一张 **"你的下一步" 引导卡** 直接指向当前阶段的待办动作（如"③ 资产：你有 2 个数据集待补充质量规则 →"）。
- **项目上下文贯穿**：顶栏项目切换器，所有阶段数据 scoped 到当前项目——工作空间范式的连贯性兑现点。

## 7. 可视化 ELT 画布（重点）

```
   节点面板          画布 (@xyflow/react)                属性抽屉
 ┌─────────┐   ┌──────────────────────────────┐   ┌──────────────┐
 │▦ 源表    │   │ ┌────────┐   ┌────────┐       │   │ 清洗·去重     │
 │▣ 清洗    │ → │ │PLM.订单│──▶│ 去重    │──┐    │   │ ─────────────│
 │◇ 连接    │   │ │●已连通 │   │●3.2万行 │  │    │   │ 主键: order_id│
 │∑ 聚合    │   │ └────────┘   └────────┘  ▼    │   │ 策略: 保留最新│
 │▤ 输出    │   │ ┌────────┐        ┌─────────┐ │   │ [预览20行]    │
 └─────────┘   │ │ERP.客户│───────▶│ ODS.宽表 │ │   └──────────────┘
   拖入画布     │ │●已连通 │        │▤ 输出    │ │
  (dnd-kit)    │ └────────┘        └─────────┘ │   底部 dock:
               └──────────────────────────────┘   ▶运行 ⏱调度 📜日志
```

- **节点类型**：源表 / 清洗 / 连接(join) / 聚合 / 输出，卡片含 类型图标 · 名称 · 状态点 · 行数徽标。连线表数据流向。
- **dbt 隐藏**：画布上搭转换 → 底层自动生成 dbt model；仅在阶段④提供"查看生成的 dbt"只读抽屉，普通用户全程不接触 dbt。
- **双视图**：`画布 ⇄ 列表`切换，同一份转换作业数据，列表视图收编现网 Transform/Orchestration 表格。
- **Chrome 95 兼容**：reactflow v12 已在现网 legacy 构建跑通；面板→画布拖放用 dnd-kit（带键盘 a11y 兜底：选节点→"添加到画布"）；连线/平移/缩放走 reactflow 原生；**不**用 `:has()`/容器查询布局画布，改用 flex/grid 固定栏 + ResizeObserver。

## 8. Swiss 设计系统（Chrome 95 安全）

- **色彩**：中性灰阶 + **单一功能强调色**（沉稳蓝）+ 语义色（成功/警告/错误/信息）。**全部 HSL/hex，禁用 oklch**（`legacy-css-fallbacks` 仅作安全网）。
- **排版**：Inter（现网已有 `@fontsource-variable/inter`）；强字号层次；数据区用 **tabular-nums** 等宽数字对齐。
- **网格与密度**：8px 基线、严格 12 列内容网格；高密度表格沿用 **CompactTable / 默认 10 条每页** 约定。
- **状态语言**：阶段状态点、节点状态点、运行状态统一一套 token。
- **动效**：仅 `transform/opacity`，克制。

## 9. Mock 数据策略

- **一条可走通的样例项目**："销售准备项目"贯穿 4 阶段（PLM 订单 + ERP 客户 → 去重/连接 → ODS 宽表资产 → 销售达成率指标），保证黄金主线**端到端可点可走**。
- `mock/services/*Service.ts` 复刻现网契约返回 `Result<T>` + 可调延迟；`VITE_USE_MOCK=1` 默认开；带"重置样例数据"开发入口。

## 10. Sprint 计划概览（详细工件见 plan/ 下 sprint-workflow 产物）

| Sprint | 主题 | 关键产出 |
|---|---|---|
| **S1** | 地基 | Vite+legacy 脚手架 · Swiss 设计系统 · mock 框架 · 项目外壳+阶段轨+项目门户 |
| **S2** | ① 连接 | 数据源/连接器/驱动/接入变更/调度 |
| **S3–S4** | ② 集成 | 可视化 ELT 画布（拆两段：画布内核 → 配置抽屉/运行 dock/双视图） |
| **S5** | ③ 资产 | 目录/搜索/血缘(G6)/质量/权属 |
| **S6** | ④ 指标 | 指标设计/语义建模/dbt 隐藏抽屉 |
| **S7** | 旁路区 + 收尾 | 服务/治理/安全/运维/设置 · 全局搜索 · 端到端打磨 |

## 11. 风险与约束

- **Chrome 95** 是硬约束：任何 CSS 新特性（oklch/`:has()`/容器查询/subgrid）一律禁用；构建必须开 legacy。
- **画布性能**：节点规模大时 reactflow 需虚拟化；原型阶段控制 demo 节点数即可。
- **可回植性**：组件、token、service 契约都对齐现网命名，降低未来回植成本。
- **范围蔓延**：全量重皮工作量大，靠 sprint 切分 + 旁路区"先占位后充实"控制节奏。
