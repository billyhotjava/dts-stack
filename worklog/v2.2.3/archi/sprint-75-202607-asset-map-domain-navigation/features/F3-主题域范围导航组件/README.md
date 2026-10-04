# F3：主题域范围导航组件

**优先级**：P0
**状态**：DRAFT

## 目标

用一个共享的分区式导航替换两处复制的 antd `Tree`，让"哪个域有资产、哪个域有待处置"在不点击的前提下可见，并把范围选中态迁到 URL。

## 契约定义

| 类型 | 契约 | 关键签名 |
|---|---|---|
| 组件 | `components/catalog/DomainScopeNav.tsx` | 纯展示：props in / callback out，不取数、不读写 URL（ADR-75-08） |
| 类型 | `DomainScopeNode` | `{ id: string \| null; name: string; code?: string; stats?: DomainScopeStats; children?: DomainScopeNode[] }` |
| 类型 | `DomainScopeStats` | `{ total: number; attention: number }` |
| Props | `DomainScopeNavProps` | `nodes / allStats / unassignedStats / value / onChange / loading / truncated` |
| URL 状态 | `?domain=<uuid>` \| `?domain=__UNASSIGNED__` \| 缺省=全部 | 由页面容器绑定（ADR-75-06） |
| 工具 | `assetPageShared.buildTreeNodes` | 改为产出 `DomainScopeNode`，缺 id 时 `id: null`，**不再生成 `fallback-` key**（ADR-75-07） |

```ts
export interface DomainScopeNavProps {
  nodes: DomainScopeNode[];
  allStats?: DomainScopeStats;
  unassignedStats?: DomainScopeStats;
  value?: string;                          // undefined = 全部资产
  onChange: (next: string | undefined) => void;
  loading?: boolean;
  truncated?: boolean;                     // 数字前缀显示 ≥
}
```

## UI/UX 规格

```text
┌ 资产范围 ───────────────────── ┐
│  🔍 搜索主题域…                │   域数 >8 才渲染，按 name + code 匹配并高亮
├────────────────────────────────┤
│  全部资产              360  ⚠360│   概览行，非树节点（ADR-75-05）
├─ 业务主题域 ─────────────── 5 ─┤   分区标题 + 域计数
│    地铁域      DTMS       0     │   空域整行 40% 不透明
│    租赁域      LEASE      0     │
│    财务域      FIN        0     │
│  ▸ 项目管理域   PJM        0     │   有子域才出现展开箭头
├─ 待治理 ───────────────────────┤   独立分区（ADR-75-04）
│  ⚠ 未归域             360  ⚠360│
└────────────────────────────────┘
```

- **数字**：总数 `tabular-nums` slate-600；待处置 amber，仅 >0 时出现；`truncated` 时加 `≥` 前缀。
- **选中态**：3px 左侧主色条 + 中性加深底 + 文字加粗。不用 antd 默认淡蓝块。
- **空域**：整行 40% 不透明，使"五个域全空"一眼可见。
- **缺标识域**：disabled + tooltip「该主题域缺少标识，无法作为筛选条件」。禁止静默降级为"全部"。
- **控件最少化**：无折叠按钮（靠 `Sider breakpoint="md"`）、无行级 hover 按钮、无右键菜单。整行是唯一交互（ADR-75-09）。
- **无虚线**：去掉 `showLine`，层级用 12px 缩进 + hover 左侧色条表达。

### 四态

| 态 | 表现 |
|---|---|
| 加载中 | 保留分区骨架的 skeleton，不整块 spin |
| 统计缺失 | 树可用，数字位显示 `—`，不阻断导航 |
| 无主题域 | "尚未创建主题域" + 指向治理主题域页的行内链接 |
| 统计截断 | 分区标题右侧提示图标 + tooltip 说明真实原因 |

### 操作走查

进入地图 → 左侧读到"未归域 360 待处置 360、五个业务域全 0（弱化）" → 点"未归域" → URL 变为 `?domain=__UNASSIGNED__` → 刷新页面范围仍在 → 复制链接给同事打开范围一致 → 浏览器后退回到"全部资产"。

### 兼容

- Chrome95。
- 旧深链 `?view=table` 的重定向逻辑保持不变。
- 台账页的 `?domain=` 与其余既有筛选参数共存，互不覆盖。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 实现 DomainScopeNav 组件与类型契约 | P0 | DRAFT | F2/T03 |
| T02 | 重写 buildTreeNodes 并移除 fallback key | P0 | DRAFT | T01 |
| T03 | 地图页接入并绑定 ?domain= searchParams | P0 | DRAFT | T01、T02 |
| T04 | 台账页接入同一组件与 URL 约定 | P0 | DRAFT | T03 |

## Definition of Ready

- [ ] 已确认台账页现有 searchParams 键名，避免 `domain` 与既有筛选键冲突
- [ ] 已确认 `Layout.Sider breakpoint` 在两页的既有差异（地图页无、台账页 `md`）统一为 `md`（Sprint-72 契约已钉住台账为 md） 不影响既有布局
- [ ] 已确认项目内是否已有同类"分区式导航"组件可复用，避免第三种实现

## Definition of Done

- [ ] G-75-05 通过：代码中不再出现 `fallback-` key 生成与 `startsWith("fallback-")` 判断
- [ ] G-75-06 通过：`?domain=` 深链在两个页面均可复现范围，刷新/分享/后退全部可用
- [ ] `DomainScopeNav.test.tsx` 覆盖：空域弱化、缺 id disabled、搜索阈值（8 域/9 域）、onChange 回调、truncated 前缀
- [ ] 两个页面不再各自持有 Tree 实现，L01 的重复消除
- [ ] 侧栏在 320/768/1024/1440 四个断点下无横向溢出
