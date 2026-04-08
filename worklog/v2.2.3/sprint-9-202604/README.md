# Sprint-9: GPMC 大屏跳转修复 + 甘特图弹层下钻

**时间**: 2026-04
**状态**: READY
**类型**: Implementation（实施型）
**目标**: 修复 GPMC 大屏点击事件"无目标却跳首页"的体验缺陷，并把执行层甘特图升级为"父项目汇总 + 弹层下钻子项目精致甘特"，让大屏更像深度分析工具而不是静态海报。

## 背景与问题

### 问题 1：点击事件如果无法下钻，跳到了首页

GPMC 5 个 v2 大屏（综合态势 / 执行 / 质量 / 风险 / 技术状态）的组件 `actions[].jumpUrlTemplate` 大量使用 `screen-ref:大屏名|/bi/gpmc/...` 模式或直链 `/bi/gpmc/drill/{execution,quality,risk,tech-state}`：

1. **`screen-ref:` 解析失败时**：当大屏列表里找不到目标名称时，`InteractionLayer.tsx` 当前会 fallback 到 URL 模板里的 `/bi/gpmc/...`（这些路由前端根本不存在），表现为"跳到首页"。
2. **直链路径根本不存在**：`/bi/gpmc/drill/...` 这类路径在前端路由表里没有注册，点击同样会被路由 fallback 抓走。
3. **编辑器侧 ScreenJumpPicker 自身有 UI/功能 bug**：暗色主题下下拉面板背景退化为白色（截图"留白"），列表项 name 因 flex 塌陷不显示。

**期望行为**：编辑器没设置跳转的 = 静默不动作；编辑器设置了但运行时解析失败的 = 取消跳转 + 视觉禁用（不显示鼠标手型）。

### 问题 2：甘特图缺乏深度交互价值

`gpmc-execution-board-v2.json` 的 `gpmc-execution-gantt` 组件目前是一段**硬编码 tasks 数组**（行 935-1090），平铺渲染所有任务，没有体现"重大项目—子项目—任务"的层级关系。`ProjectGanttBoard.tsx` 已经支持按 `majorProjectName` 折叠分组，但仅是折叠展开，不是真正的下钻；信息密度过高反而让领导一眼看不出重点。

**期望体验**：
- 大屏内的甘特只显示**父项目汇总条**（每个重大项目一行）
- 点击某个父项目 → 弹出 1440×820 居中大模态，模态内左侧 320px 项目元信息+KPI+风险列表，右侧 1120px 子项目精致甘特
- 弹层关闭回到大屏，状态保留
- 不再继续 3 层下钻

## 设计决策记录

| 决策点 | 结论 |
|--------|------|
| 编辑器没设置跳转的行为 | 静默不动作（已经如此，但要确认 ScreenJumpPicker bug 修后默认值不会污染） |
| 跳转设置后失败的行为 | 取消跳转（C 方案）+ 视觉禁用（B 方案），不 fallback 到任何 URL |
| 死链 JSON 处理 | 方案 X：清空 `screen-ref:` 的 fallback 部分；删除直链 `/bi/gpmc/drill/*` 的整条 action |
| "动作入口"标题视觉断层 | 一并修：action 卡片背景从浅色 hardcode 改为暗色主题适配 |
| Chrome 兼容下限 | Chrome 95（禁用 `:has()` / `structuredClone` / `findLast` / `text-wrap: balance` / container queries） |
| 甘特图下钻形态 | B：弹出居中大模态（不替换原视图，不打断大屏全局态势） |
| 模态内布局 | 方案 ②：左 320px 元信息+KPI+风险栏 + 右 1120px 主体甘特 |
| 模态内是否继续下钻 | 否，2 层就停（点子项目任务条仅 hover tooltip） |
| 甘特数据源 | SQL 驱动，部署时只换 `{{DATABASE_ID}}` 占位符，统一交付方式 |
| 数据形态 | 单 SQL 查询返回扁平行；前端 transformer 按 `project_no` → `subsystem` → tasks 做 group by 聚合 |
| 元信息字段来源 | 全部来自 `biz_dwd_project_node_v2`（已有 `project_manager` / `dept` / `owner` / `risk_content` 等字段） |
| 复用还是新建 | 新建 `ProjectDetailGanttModal.tsx`，内部复用增强后的 `ProjectGanttBoard.tsx` |
| 测试覆盖 | 仅核心：transformer 单测 + 引擎 sentinel 单测；UI 测试和 fixture 验证省略 |

## 架构概览

### 文件结构

```
# 新增
source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/
  ProjectDetailGanttModal.tsx         ← 子项目甘特弹层（Portal + 蒙层 + 1440x820 卡片）

source/dts-platform-webapp/src/analytics/pages/project-cockpit/utils/
  majorProjectAggregator.ts           ← SQL 扁平行 → MajorProject[] transformer
  majorProjectAggregator.test.ts      ← transformer 单测

# 修改 (TypeScript)
source/dts-platform-webapp/src/analytics/pages/screens/components/PropertyPanel.tsx
  - 行 830-958 ScreenJumpPicker：暗色主题、列表项 flex 修复、默认模式调整
  - 行 6086-6094 action 卡片背景：浅色 hardcode → bg-surface-muted/40

source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx
  - 行 28-67 parseScreenReferenceUrl / resolveScreenReferenceUrl：sentinel 改造，移除默认 /bi/screens fallback
  - 行 144-166 navigateToResolvedUrl：检测空 sentinel → trackEvent 后 return
  - 新增 useResolvableJumpStatus hook：预解析所有 jump-url 判断组件可点性

source/dts-platform-webapp/src/analytics/pages/screens/renderers/EChartsRenderer.tsx
  - 行 323-481 gantt-chart 分支：识别 renderMode='board-hierarchical' + drillMode='modal'
  - 行 327 onTaskClick：根据 useResolvableJumpStatus 决定是否绑定
  - 接入 ProjectDetailGanttModal 状态管理

source/dts-platform-webapp/src/analytics/pages/screens/renderers/TableRenderer.tsx
  - 接入 useResolvableJumpStatus：禁用时 cursor: default + 移除 hover

source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/ProjectGanttBoard.tsx
  - 新增 renderMode='hierarchical' 分支：从 majorProjects[] 渲染父项目汇总条
  - 增强 flat 模式：补 baseline 渲染、里程碑菱形、今日红线、任务高亮
  - 新增 highlightedTaskName prop：点风险项时甘特对应行加红色 ring

source/dts-platform-webapp/src/analytics/pages/screens/types.ts
  - 新增 MajorProject / SubProject / MajorProjectKPI / MajorProjectRisk 类型
  - 扩展 gantt-chart config 字段：renderMode='board-hierarchical' / drillMode='none'|'modal'

# 修改 (JSON 实例)
worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-strategic-overview-v2.json    清 fallback ×28 + 删直链 ×1
worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-execution-board-v2.json       清 fallback ×14 + 删直链 ×5 + 改 gantt 组件
worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-quality-board-v2.json         清 fallback ×14 + 删直链 ×4
worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-risk-board-v2.json            清 fallback ×14 + 删直链 ×3
worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-tech-state-board-v2.json      清 fallback ×14 + 删直链 ×2
```

### 数据流

```
用户在大屏(execution-board)上看到 gpmc-execution-gantt 组件
  ↓ 组件 dataSource.sqlConfig.query 返回扁平行（SQL）
  ↓ 部署时 {{DATABASE_ID}} 已被替换为现场数据集 id
  ↓ 行包含: project_no, subsystem, node_task, plan_date, actual_date, ...
  ↓ majorProjectAggregator.ts 把行 group by project_no → subsystem → tasks
  ↓ 输出 MajorProject[] 结构
ProjectGanttBoard renderMode='hierarchical' 模式
  → 每个 MajorProject 渲染一行（聚合时间条 + 完成率 + 风险颜色）
  → 用户点击某行 → onProjectClick(majorProject)

EChartsRenderer 内部 useState<MajorProject|null>(null) 接收
  → 状态非空时挂载 <ProjectDetailGanttModal>
  → Modal 内 left 栏渲染 majorProject.kpi / risks / 元信息
  → Modal 内 right 区域渲染 ProjectGanttBoard renderMode='flat' tasks=majorProject.subprojects[*].tasks 拍平
  → 用户按 ESC 或点蒙层 → onClose → setState(null) → Modal 卸载

平行链路: useResolvableJumpStatus
  → 大屏其他组件（KPI 卡、表格、其他图表）mount 时预解析自己的 jump-url
  → screen-ref 找不到目标 + 无其他可解析 → setStatus({ hasResolvableJump: false })
  → 渲染层据此关掉 onClick / cursor: pointer / hover 高亮
```

## Feature 设计

### F1: 跳转引擎"无目标=不跳"修复（运行时）

#### F1-T01: parseScreenReferenceUrl + resolveScreenReferenceUrl 引入空 sentinel

在 `InteractionLayer.tsx` 行 28-67：

**Step A** — `parseScreenReferenceUrl`：当前把空 fallback 默认成 `/bi/screens`。改为允许 null：

```ts
// 修改前
const fallbackUrl = decodeURIComponent(fallbackPart || '').trim() || '/bi/screens';
// 修改后
const fallbackRaw = decodeURIComponent(fallbackPart || '').trim();
const fallbackUrl = fallbackRaw || null;
```

返回类型 `{ screenName: string; fallbackUrl: string | null }`。

**Step B** — `resolveScreenReferenceUrl`：解析失败时返回空字符串 sentinel（不再无条件 fallback 到任何 URL）：

```ts
// 修改前 (行 66)
return parsed.fallbackUrl;
// 修改后
return parsed.fallbackUrl ?? '';  // null/undefined → '' = 取消跳转
```

返回类型保持 `Promise<string>`。

#### F1-T02: navigateToResolvedUrl 检测空 sentinel 取消跳转

`InteractionLayer.tsx:144-166`：

```ts
const navigateToResolvedUrl = useCallback(async (targetUrl, openMode, source) => {
    const resolved = await resolveScreenReferenceUrl(targetUrl);
    if (!resolved) {  // 新增：sentinel 拦截
        runtime.trackEvent({
            kind: 'jump',
            key: 'jumpUrl',
            value: '',
            source,
            meta: `cancelled;raw=${targetUrl}`,
        });
        return;
    }
    // ... 原有 normalizeRuntimeJumpUrl + window.location/window.open 逻辑
}, [runtime]);
```

注意：现有 jump-url action 处理（`InteractionLayer.tsx:226-233`）已经有空字符串预检查，无需改动。

#### F1-T03: 单测 — InteractionLayer sentinel 行为

新增 `source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.cancelOnEmpty.test.tsx`，覆盖：
- `resolveScreenReferenceUrl('screen-ref:NotFound|')` 返回 `''`（无 fallback）
- `resolveScreenReferenceUrl('screen-ref:NotFound|/explicit/url')` 返回 `/explicit/url`（显式 fallback 仍生效）
- `resolveScreenReferenceUrl('screen-ref:Target|')` mock listScreens 命中 → 返回 `/<id>/preview` 路径
- `resolveScreenReferenceUrl` listScreens 抛错且无 fallback → 返回 `''`
- 非 screen-ref URL 原样返回

#### F1-T04: useResolvableJumpStatus hook + hasNonJumpInteractivity 实现

新增到 `InteractionLayer.tsx` 内：

```ts
export function useResolvableJumpStatus(component: ScreenComponent, mode: string): {
    hasResolvableJump: boolean;
    isResolving: boolean;
} {
    const [status, setStatus] = useState({ hasResolvableJump: true, isResolving: true });
    useEffect(() => {
        // 仅在 preview 模式预解析；编辑器/草稿模式视为可点
        if (mode !== 'preview') {
            setStatus({ hasResolvableJump: true, isResolving: false });
            return;
        }
        const candidates: string[] = [];
        // 收集所有可能的跳转模板
        for (const a of component.actions ?? []) {
            if (a?.type === 'jump-url' && String(a.jumpUrlTemplate || '').trim()) {
                candidates.push(String(a.jumpUrlTemplate));
            }
        }
        if (component.interaction?.enabled === true
            && component.interaction?.jumpEnabled === true
            && String(component.interaction?.jumpUrlTemplate || '').trim()) {
            candidates.push(String(component.interaction.jumpUrlTemplate));
        }
        // 没有任何 jump 模板 → 视为"无可解析跳转"
        if (candidates.length === 0) {
            setStatus({ hasResolvableJump: false, isResolving: false });
            return;
        }
        let cancelled = false;
        (async () => {
            for (const tmpl of candidates) {
                const resolved = await resolveScreenReferenceUrl(tmpl);
                if (cancelled) return;
                if (resolved) {  // 任一成功即标记可点
                    setStatus({ hasResolvableJump: true, isResolving: false });
                    return;
                }
            }
            setStatus({ hasResolvableJump: false, isResolving: false });
        })();
        return () => { cancelled = true; };
    }, [component.actions, component.interaction, mode]);
    return status;
}
```

性能保障：`resolveScreenReferenceUrl` 已有 30s 内存缓存（行 25-26），多组件共享同一次 `listScreens` 请求。

同 task 内还实现复合判断辅助函数 `hasNonJumpInteractivity(component)`：判断组件是否有非跳转类 action（drill-down/drill-up/open-panel/set-variable/emit-intent）或交互映射（interaction.mappings）。这样组件在 F1-T05 接入时可以做"仅当所有交互手段都不可用时才禁用"的判断：

```ts
const isFullyDisabled = !isResolving && !hasResolvableJump && !hasNonJumpInteractivity(component);
```

#### F1-T05: EChartsRenderer / TableRenderer 接入视觉禁用

在 `EChartsRenderer.tsx` 和 `TableRenderer.tsx` 顶部 import `useResolvableJumpStatus` 和 `hasNonJumpInteractivity`，调用 hook 拿到 `isFullyDisabled`：

- EChartsRenderer：`isFullyDisabled` 为 true 时把 `echartsClickHandler` 置为 undefined，容器加 `cursor: default`
- TableRenderer：`isFullyDisabled` 为 true 时移除行 `onClick`，cursor 改 default
- ProjectGanttBoard 不需要直接接入（其 `onTaskClick` 由父组件 EChartsRenderer 决定是否传，自动级联生效）

### F2: 编辑器 ScreenJumpPicker UI 修复

#### F2-T01: 下拉面板暗色主题适配

`PropertyPanel.tsx:885-942`，下拉面板的所有 inline `style` 中的 hardcode 颜色全部替换为 Tailwind 类：

```tsx
// 修改前
<div style={{
    position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 999,
    maxHeight: 240, overflowY: 'auto',
    border: '1px solid rgba(148,163,184,0.3)', borderRadius: 6,
    background: 'var(--color-surface-card, #fff)',
    boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
}}>

// 修改后
<div className="absolute top-full left-0 right-0 z-[999] max-h-60 overflow-y-auto
                border border-border-default rounded-md bg-surface-card shadow-lg">
```

同步：
- 搜索框分隔线 `borderBottom` → `border-b border-border-default`
- "加载中.../暂无大屏" 提示色 `#94a3b8` → `text-text-tertiary`
- "已发布/草稿"标签的 `rgba` → `bg-emerald-500/10 text-emerald-500` / `bg-slate-400/10 text-text-tertiary`

#### F2-T02: 列表项 name flex 塌陷修复

`PropertyPanel.tsx:916-937`，根因是 `display: flex; justify-content: space-between` 下 name span 没有 `min-width: 0`，长文本溢出时与右侧标签争抢空间导致 name 不显示。

```tsx
<div className="flex items-center justify-between gap-2 px-3.5 py-2 cursor-pointer
                hover:bg-brand/[0.06]"
     onClick={...}>
    <span className="flex-1 min-w-0 truncate text-xs">  {/* min-w-0 + truncate 是关键 */}
        {isSelected ? '✓ ' : ''}{s.name || `大屏 #${s.id}`}
    </span>
    <span className="flex-shrink-0 text-[10px] px-1.5 py-0.5 rounded ...">
        {isPublished ? '已发布' : '草稿'}
    </span>
</div>
```

#### F2-T03: 默认模式调整

`PropertyPanel.tsx:832`：

```tsx
// 修改前
const [mode, setMode] = useState<'screen' | 'custom'>(isScreenRef ? 'screen' : 'custom');
// 修改后
const [mode, setMode] = useState<'screen' | 'custom'>(
    !value || isScreenRef ? 'screen' : 'custom'
);
```

空值默认为"选择大屏"模式，引导用户从列表选而不是手输 URL。

#### F2-T04: 动作配置区段视觉一致性

`PropertyPanel.tsx:6086-6094` action 卡片背景：

```tsx
// 修改前
<div style={{
    border: '1px solid rgba(148,163,184,0.18)',
    borderRadius: 10,
    padding: 10,
    marginBottom: 10,
    background: 'rgba(248,250,252,0.72)',  // 暗色主题下变乳白色块
}}>

// 修改后
<div className="border border-border-default rounded-[10px] p-2.5 mb-2.5
                bg-surface-muted/40">
```

同步处理 `PropertyPanel.tsx:6125-6133` 的内层 mapping 卡片 `border: '1px dashed rgba(148,163,184,0.22)'`。

### F3: JSON 实例死链清理

#### F3-T01: 清理 5 个 GPMC v2 大屏 JSON

清理规则：

| 现状 | 处理 |
|------|------|
| `screen-ref:GPMC%20...\|%2Fbi%2Fgpmc...` | 改为 `screen-ref:GPMC%20...\|`（清空 fallback） |
| `/bi/gpmc/drill/{execution,quality,risk,tech-state}` 直链 | 整条 action 删除 |

涉及文件与处理量：
- `gpmc-strategic-overview-v2.json`: 28 处 fallback 清空 + 1 处直链 action 删除
- `gpmc-execution-board-v2.json`: 14 处 fallback 清空 + 5 处直链 action 删除
- `gpmc-quality-board-v2.json`: 14 处 fallback 清空 + 4 处直链 action 删除
- `gpmc-risk-board-v2.json`: 14 处 fallback 清空 + 3 处直链 action 删除
- `gpmc-tech-state-board-v2.json`: 14 处 fallback 清空 + 2 处直链 action 删除

每次修改后用 `python -m json.tool` 验证 JSON 仍合法。

**v1 目录不动**（`worklog/v2.2.3/s10/pjm/v1/screen-instances-*/`）。

### F4: 父项目汇总甘特组件改造

#### F4-T01: 类型扩展

新增到 `source/dts-platform-webapp/src/analytics/pages/screens/types.ts`：

```ts
export interface MajorProjectKPI {
    completionRate?: number;       // 0-100
    milestoneRate?: number;        // 0-1
    highRiskCount?: number;
    delayDays?: number;
}

export interface MajorProjectRisk {
    level: 'high' | 'warn' | 'normal';
    label: string;
    taskRef?: string;              // 对应到 subprojects[*].tasks[*].name
}

export interface SubProject {
    name: string;
    tasks: ProjectGanttTask[];
}

export interface MajorProject {
    name: string;                  // = project_no
    responsibleDept?: string;      // = MODE(dept) 或 MIN(dept)
    manager?: string;              // = MIN(project_manager)
    instituteLeader?: string;
    startDate?: string;            // = MIN(plan_date)
    plannedDeliveryDate?: string;  // = MAX(plan_date)
    stage?: string;                // 派生：进行中/已完成/延期
    kpi?: MajorProjectKPI;
    risks?: MajorProjectRisk[];
    subprojects: SubProject[];
}
```

`gantt-chart` config 类型扩展两个字段（可选）：
- `renderMode?: 'echarts' | 'board' | 'board-hierarchical'`
- `drillMode?: 'none' | 'modal'`

#### F4-T02: majorProjectAggregator 实现

新建 `source/dts-platform-webapp/src/analytics/pages/project-cockpit/utils/majorProjectAggregator.ts`：

```ts
type FlatRow = {
    重大项目?: string; 子项目?: string; 任务?: string;
    类型?: string; 计划日期?: string; 实际日期?: string;
    是否完成?: boolean; 是否超期?: boolean; 延期天数?: number;
    风险等级?: string; 责任科室?: string; 责任人?: string;
    项目经理?: string; 风险内容?: string; 延期影响?: string;
    所长?: string;
};

export function aggregateMajorProjects(rows: FlatRow[]): MajorProject[] {
    // 1. group by 重大项目
    const byProject = new Map<string, FlatRow[]>();
    for (const r of rows) {
        const key = String(r.重大项目 || '').trim();
        if (!key) continue;
        (byProject.get(key) ?? byProject.set(key, []).get(key)!).push(r);
    }
    return Array.from(byProject.entries()).map(([projectName, projectRows]) => {
        // 2. group by 子项目
        const bySub = new Map<string, FlatRow[]>();
        for (const r of projectRows) {
            const subKey = String(r.子项目 || '(无子项目)').trim();
            (bySub.get(subKey) ?? bySub.set(subKey, []).get(subKey)!).push(r);
        }
        const subprojects: SubProject[] = Array.from(bySub.entries()).map(([subName, subRows]) => ({
            name: subName,
            tasks: subRows.map(rowToTask),
        }));
        // 3. 聚合元信息
        const meta = aggregateMeta(projectRows);
        // 4. 聚合 KPI
        const kpi = aggregateKpi(projectRows);
        // 5. 聚合风险列表
        const risks = aggregateRisks(projectRows);
        return { name: projectName, ...meta, kpi, risks, subprojects };
    });
}

function rowToTask(r: FlatRow): ProjectGanttTask { /* 字段映射 */ }
function aggregateMeta(rows: FlatRow[]) { /* MIN/MAX/MODE */ }
function aggregateKpi(rows: FlatRow[]) { /* SUM/COUNT */ }
function aggregateRisks(rows: FlatRow[]): MajorProjectRisk[] { /* filter top-N */ }
```

KPI 算法：
- `completionRate` = `count(是否完成=true) * 100 / total`
- `milestoneRate` = `count(类型='里程碑节点' AND 是否完成=true) / count(类型='里程碑节点')`（无里程碑时返回 null）
- `highRiskCount` = `count(风险等级='高' AND 是否完成=false)`
- `delayDays` = `sum(延期天数 > 0 ? 延期天数 : 0)`

风险列表算法：取 `风险等级='高' OR 延期天数>0` 的行，按延期天数倒序，最多 8 条。

#### F4-T03: majorProjectAggregator 单测

新建 `source/dts-platform-webapp/src/analytics/pages/project-cockpit/utils/majorProjectAggregator.test.ts`，覆盖：
- 按 `重大项目` 分组
- 子项目分组（含 NULL `子项目` 默认为 `(无子项目)`）
- `completionRate` 算法（已完成 / 总数 × 100）
- `milestoneRate` 仅计入 `类型='里程碑节点'` 的行
- `highRiskCount` 仅计入未完成的高风险
- `delayDays` SUM 聚合
- 风险列表按延期天数倒序，最多 8 条
- 元信息字段 `pickFirstNonEmpty` 行为
- 跳过空 `重大项目` 的行

#### F4-T04: ProjectGanttBoard hierarchical 模式 + flat 模式精致化增强

`source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/ProjectGanttBoard.tsx`：

新增 prop：

```ts
type Props = {
    // 现有
    tasks?: ProjectGanttTask[];
    // 新增
    majorProjects?: MajorProject[];
    renderMode?: 'flat' | 'hierarchical';
    onProjectClick?: (project: MajorProject) => void;
    highlightedTaskName?: string;
    // 现有继续保留
    maxHeight?: number;
    onTaskClick?: (task: ProjectGanttTask) => void;
    sideTextColor?: string;
    dark?: boolean;
};
```

`renderMode='hierarchical'` 模式：
- 不调用 `groupByProject`，直接遍历 `majorProjects`
- 每个 MajorProject 渲染一行（高度 64px，比 flat 模式 ~50px 高）：
  - 左 240px：项目名（粗体 14px）+ 责任部门 + 项目经理（小字）
  - 中部轨道：聚合时间条，颜色按聚合风险（任一子任务高 → 红，否则任一延期 → 黄，否则蓝）
  - 右 170px：完成率大字 + 计划交付日期
- 整行 cursor-pointer + role=button + tabIndex=0 + Enter/Space 触发
- 点击调用 `onProjectClick(majorProject)`

`renderMode='flat'` 模式精致化增强（用于 modal 内主体）：

| 增强项 | 实现 |
|--------|------|
| 基线对比 | 已有，确认开启 |
| 里程碑菱形 | `task.type === '里程碑节点'` 时渲染 16×16 旋转方块代替条 |
| 今日红线 | 在轨道层叠加 `<div className="absolute top-0 bottom-0 w-0.5 bg-red-500/60">`，定位用今日相对 start/total 百分比 |
| 任务高亮 | 当 `highlightedTaskName === task.name` 时，bar 加 `box-shadow: 0 0 0 3px rgba(239,68,68,0.5)` 800ms |

### F5: ProjectDetailGanttModal 弹层组件

#### F5-T01: 组件骨架 + Portal + ESC + 子组件

新建 `source/dts-platform-webapp/src/analytics/pages/project-cockpit/components/ProjectDetailGanttModal.tsx`，单文件内含 `ProjectDetailGanttModal` 主组件 + `ModalHeader` / `ModalSidebar` / `ModalGanttArea` / `ModalFooter` / `KpiMini` / `Legend` 等内部子组件：

```tsx
interface Props {
    project: MajorProject | null;
    onClose: () => void;
}

export function ProjectDetailGanttModal({ project, onClose }: Props) {
    const [highlightedTask, setHighlightedTask] = useState<string | undefined>();
    // ESC 关闭
    useEffect(() => {
        if (!project) return;
        const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [project, onClose]);
    if (!project) return null;
    // 拍平所有子项目任务
    const allTasks = project.subprojects.flatMap(sp =>
        sp.tasks.map(t => ({ ...t, subprojectName: sp.name }))
    );
    return ReactDOM.createPortal(
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-window" onClick={(e) => e.stopPropagation()}>
                <ModalHeader project={project} onClose={onClose} />
                <div className="modal-body">
                    <ModalSidebar
                        project={project}
                        onRiskClick={(taskRef) => setHighlightedTask(taskRef)}
                    />
                    <ModalGanttArea
                        tasks={allTasks}
                        highlightedTaskName={highlightedTask}
                    />
                </div>
                <ModalFooter />
            </div>
        </div>,
        document.body
    );
}
```

样式：

```css
.modal-overlay {
    position: fixed; inset: 0; z-index: 9999;
    background: rgba(2, 6, 23, 0.72);
    -webkit-backdrop-filter: blur(8px);
    backdrop-filter: blur(8px);
    display: flex; align-items: center; justify-content: center;
    animation: modal-fade-in 200ms ease-out;
}
.modal-window {
    width: 1440px; height: 820px;
    max-width: calc(100vw - 80px); max-height: calc(100vh - 80px);
    background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
    border-radius: 20px;
    box-shadow: 0 32px 80px rgba(2,6,23,0.5), 0 0 0 1px rgba(148,163,184,0.18);
    overflow: hidden;
    display: flex; flex-direction: column;
    animation: modal-scale-in 300ms cubic-bezier(0.16, 1, 0.3, 1);
}
.modal-body { flex: 1; display: flex; min-height: 0; }
@keyframes modal-fade-in { from { opacity: 0; } to { opacity: 1; } }
@keyframes modal-scale-in {
    from { opacity: 0; transform: scale(0.96); }
    to { opacity: 1; transform: scale(1); }
}
```

Chrome 95 已稳定支持 `backdrop-filter` 和 cubic-bezier。`<dialog>` 元素**不用**（行为不一致），使用 React Portal + 自管理蒙层。

**子组件设计参考（在 F5-T01 内一并实现）：**

**ModalHeader（56px）**

```
┌────────────────────────────────────────────────┐
│ 制造协同平台          研发部 · 张工        [✕]  │
└────────────────────────────────────────────────┘
```

- 左：项目名 20px font-bold
- 中：责任部门 · 项目经理（14px text-text-secondary）
- 右：32×32 圆形关闭按钮，hover bg-gray-100

**ModalSidebar（320px 宽，三个区段）**

- 区段 1 — 项目元信息：责任部门 / 项目经理 / 所长 / 启动日期 / 计划交付 / 阶段
- 区段 2 — KPI 小条：完成率 / 里程碑 / 高风险 / 累计延期，2×2 grid，bg-slate-50 圆角 + 大数字
- 区段 3 — 风险提示列表：逐项 `🔴 设备线延期 (28d)`，cursor-pointer 点击触发 `onRiskClick(taskRef)`，最多 8 项

**ModalGanttArea（flex-1）**

- 顶部 24px 子项目分组 chips（"全部 / 平台基础 / ..."），点击切换可见任务
- 中部主体：复用 `ProjectGanttBoard` `renderMode='flat'`，传入 `highlightedTaskName`
- 底部 32px 时间轴 + 图例（计划│实际│基线│里程碑│风险│今日）

#### F5-T02: EChartsRenderer 接入

`source/dts-platform-webapp/src/analytics/pages/screens/renderers/EChartsRenderer.tsx:323-481` `gantt-chart` case 增加分支：

```tsx
case 'gantt-chart': {
    const renderModeRaw = String(c.renderMode ?? '').trim().toLowerCase();
    if (renderModeRaw === 'board-hierarchical') {
        const drillMode = String(c.drillMode ?? 'none');
        // 数据：从 dataSource 拉到的扁平行经 transformer 聚合
        const rows = dataSourceRows ?? [];
        const majorProjects = useMemo(() => aggregateMajorProjects(rows), [rows]);
        const [activeProject, setActiveProject] = useState<MajorProject | null>(null);
        return (
            <>
                <ProjectGanttBoard
                    renderMode="hierarchical"
                    majorProjects={majorProjects}
                    maxHeight={height}
                    onProjectClick={drillMode === 'modal' ? setActiveProject : undefined}
                    sideTextColor={...}
                    dark={...}
                />
                {drillMode === 'modal' && (
                    <ProjectDetailGanttModal
                        project={activeProject}
                        onClose={() => setActiveProject(null)}
                    />
                )}
            </>
        );
    }
    if (renderModeRaw === 'board') { /* 现有 board 逻辑保持 */ }
    /* 现有 echarts gantt 逻辑保持 */
}
```

注意：`dataSourceRows` 的取法需要按 EChartsRenderer 现有 dataSource 接入模式来（不在本设计深度展开，T05 的 plan 步骤中详述定位）。

### F6: gpmc-execution-gantt JSON 实例改造

#### F6-T01: 替换硬编码 tasks 为 SQL 数据源

`worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-execution-board-v2.json` 行 920-1108 的 `gpmc-execution-gantt` 组件：

**修改前**（节选）：
```json
{
  "id": "gpmc-execution-gantt",
  "type": "gantt-chart",
  "config": {
    "title": "任务执行甘特",
    "renderMode": "board",
    "tasks": [ /* 19 个硬编码 task */ ]
  },
  "actions": [
    { "type": "jump-url", "jumpUrlTemplate": "/bi/gpmc/drill/execution", "jumpOpenMode": "new-tab" }
  ],
  "dataSource": {
    "type": "sql",
    "sqlConfig": {
      "databaseId": "{{DATABASE_ID}}",
      "query": "WITH project_level AS (SELECT 'project' ...) /* 三层 UNION ALL，并未驱动甘特 */"
    }
  }
}
```

**修改后**：

```json
{
  "id": "gpmc-execution-gantt",
  "type": "gantt-chart",
  "name": "重大项目执行甘特",
  "x": 56, "y": 288, "width": 860, "height": 460,
  "zIndex": 15, "locked": false, "visible": true,
  "config": {
    "title": "重大项目执行甘特(点击下钻子项目)",
    "renderMode": "board-hierarchical",
    "drillMode": "modal",
    "sideTextColor": "#1c2833"
  },
  "actions": [],
  "dataSource": {
    "type": "sql",
    "sqlConfig": {
      "databaseId": "{{DATABASE_ID}}",
      "query": "SELECT d.project_no AS \"重大项目\", COALESCE(d.subsystem, '(无子项目)') AS \"子项目\", d.node_task AS \"任务\", d.node_type AS \"类型\", to_char(d.plan_date, 'YYYY-MM-DD') AS \"计划日期\", to_char(d.actual_date, 'YYYY-MM-DD') AS \"实际日期\", to_char(d.original_plan_date, 'YYYY-MM-DD') AS \"基线日期\", d.is_completed AS \"是否完成\", d.is_overdue_completed AS \"是否超期完成\", d.is_incomplete AS \"是否未完成\", COALESCE(d.delay_days, 0) AS \"延期天数\", d.risk_level AS \"风险等级\", d.completion_status AS \"完成情况\", d.dept AS \"责任科室\", d.owner AS \"责任人\", d.project_manager AS \"项目经理\", d.institute_leader AS \"所长\", d.risk_content AS \"风险内容\", d.delay_impact AS \"延期影响\" FROM biz_dwd_project_node_v2 d WHERE d.plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM') AND d.plan_month <= to_char({{dateTo}}::date, 'YYYY-MM') AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR d.project_no ILIKE '%' || {{projectNo}} || '%') AND ({{deptId}} IS NULL OR {{deptId}} = '' OR d.dept = {{deptId}}) AND ({{riskLevel}} IS NULL OR {{riskLevel}} = '' OR d.risk_level = {{riskLevel}}) ORDER BY d.project_no, d.subsystem NULLS LAST, d.plan_date",
      "queryTimeoutSeconds": 30,
      "maxRows": 5000
    }
  }
}
```

部署时 `{{DATABASE_ID}}` 通过 `worklog/v2.2.3/s10/pjm/v2/build-deploy-zips.sh` 替换成现场的真实数据集 id。

### F7: 文档与索引

#### F7-T01: 更新 sprint-queue.md

在 `worklog/v2.2.3/sprint-queue.md` 末尾追加 Sprint-9 条目：

```markdown
## Sprint-9: GPMC 大屏跳转修复 + 甘特图弹层下钻 (202604)
**状态**: READY
**类型**: Implementation（实施型）

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-跳转引擎"无目标=不跳"修复 | 5 | READY |
| F2-编辑器 ScreenJumpPicker UI 修复 | 4 | READY |
| F3-JSON 实例死链清理 | 1 | READY |
| F4-父项目汇总甘特组件改造 | 4 | READY |
| F5-ProjectDetailGanttModal 弹层组件 | 2 | READY |
| F6-gpmc-execution-gantt JSON 改造 | 1 | READY |
| F7-文档与索引 | 1 | READY |

**统计**: READY=18, IN_PROGRESS=0, DONE=0, BLOCKED=0
**设计文档**: `worklog/v2.2.3/sprint-9-202604/README.md`
```

## 边界条件

| 场景 | 处理 |
|------|------|
| `screen-ref:` 大屏名为空（截屏后 fallback 也清空） | navigateToResolvedUrl 检测到空 sentinel → 取消跳转 + trackEvent meta=cancelled |
| 大屏组件没设置任何 jump-url action 但有 drill-down/open-panel | useResolvableJumpStatus 返回 hasResolvableJump=false，但 hasNonJumpInteractivity=true，仍可点 |
| 大屏组件完全没设置 actions 也无 interaction | hasResolvableJump=false 且 hasNonJumpInteractivity=false → 视觉禁用 |
| ScreenJumpPicker 列表为空（API 失败或确实无大屏） | 显示"暂无大屏"提示，"自定义URL"模式仍可用 |
| 甘特 SQL 返回 0 行 | aggregateMajorProjects 返回 [] → ProjectGanttBoard 显示"当前筛选范围暂无执行任务" |
| 甘特 SQL 返回的某行 plan_date 为 NULL | aggregateMeta 跳过该行参与日期聚合，但仍纳入 KPI 总数 |
| 同一 project_no 下 project_manager 字段不一致 | 取 MIN（字典序最小）作为代表 |
| 同一 project_no 下 dept 字段不一致 | 取 MIN（字典序最小）作为代表 |
| 模态打开时用户点击大屏其他位置 | 点蒙层 → 关闭；点弹窗外其他大屏区域不可达（被蒙层挡住） |
| 用户连续点不同父项目 | activeProject state 更新，模态内容刷新（无需关闭再打开） |
| Chrome 95 上 backdrop-filter 在 GPU 加速失败 | 蒙层基础色 `rgba(2,6,23,0.72)` 即使 blur 失败也是可用暗色背景 |
| 模态在 1920×1080 大屏 + 系统级 DPI 缩放 | `max-width/max-height: calc(100vw - 80px)` 兜底，不会超出视口 |
| 模态内甘特 task 数过多导致溢出 | 主体区 overflow-y-auto，上方 chips 始终可见 |
| highlightedTaskName 在 task 列表中找不到 | 不报错，无视觉效果（800ms 后自动清除） |
| useResolvableJumpStatus 在组件卸载后回调 | cleanup `cancelled = true` 标志拦截 setState |

## 不做的事

- **不做** 3 层下钻（子项目 → 节点）
- **不接** 后端新 API（甘特数据全部来自现有 `biz_dwd_project_node_v2`）
- **不改** v1 大屏 JSON 文件（仅 v2）
- **不删** `worklog/v2.2.3/s10/pjm/v2/screen-instances/gpmc-drill-*-v2.json` 4 个独立 drill JSON（保留以备未来）
- **不改** `ProjectGanttBoard.tsx` 文件头的 `@ts-nocheck`（避免范围扩散）
- **不做** "动作禁用 toast 提示"（用户没要求，且会污染大屏体验）
- **不做** 完整 fixture 测试（用户已确认可省，仅保留 transformer 单测和引擎 sentinel 单测）
- **不重构** 现有 `interactionJump` 路径（行 309-317），统一在 `navigateToResolvedUrl` 内拦截
- **不引入**新依赖（不使用 framer-motion、reactflow 等额外库）

## 验收清单

- [ ] 编辑器：暗色主题下打开 ScreenJumpPicker 下拉，背景与编辑器一致，列表项 name 正确显示
- [ ] 编辑器：未设置跳转的动作不会触发任何跳转（运行时已支持，无需改动）
- [ ] 运行时：5 个 GPMC v2 大屏的所有点击事件不会跳到 `/bi/gpmc/...` 类首页路径
- [ ] 运行时：完全无可解析跳转的组件，鼠标悬停不显示手型，hover 高亮被禁用
- [ ] 运行时：仍有 drill-down/open-panel 等其他交互的组件，仍保持可点
- [ ] JSON：5 个 v2 大屏 JSON 通过 `python -m json.tool` 验证仍合法
- [ ] JSON：grep 5 个 v2 大屏 JSON 已无 `\|%2Fbi%2Fgpmc` 残留
- [ ] JSON：grep 5 个 v2 大屏 JSON 已无 `/bi/gpmc/drill/` 残留
- [ ] 甘特：执行层大屏的甘特从 SQL 拉数据，部署 `{{DATABASE_ID}}` 替换后正常显示父项目汇总条
- [ ] 甘特：点击父项目汇总条 → 弹出居中模态，1440×820 居中
- [ ] 甘特：模态左栏正确显示项目元信息 / KPI / 风险列表
- [ ] 甘特：模态右栏甘特正确显示该父项目所有子项目任务
- [ ] 甘特：点击模态左栏风险项 → 右栏对应任务条高亮 800ms
- [ ] 甘特：ESC / 蒙层点击 / 关闭按钮三种方式都能关闭模态
- [ ] 甘特：模态关闭后，大屏其他状态保留
- [ ] 甘特：在 Chrome 95 上验证 backdrop-filter 和动效正常
- [ ] 测试：majorProjectAggregator 单测通过
- [ ] 测试：InteractionLayer sentinel 行为单测通过
