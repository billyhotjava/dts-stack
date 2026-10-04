# 大屏设计系统重构设计方案

## 1. 产品定位

**定位**：传统 BI 大屏（A）+ 现代分析看板（B）的融合系统。

- **大屏模式**：展示型，深色 sci-fi 风格，大字 KPI，适合投屏/会议室
- **看板模式**：工作型，浅色主题，表格/筛选/导出，适合日常分析

**对标竞品**：DataV + Metabase 的交叉地带——既有 DataV 的视觉震撼力和拖拽设计能力，又有 Metabase 的自适应布局和深度分析交互。

## 2. 现状评估

| 维度 | 现有能力 | 痛点 |
|------|---------|------|
| 渲染器 | 49 种组件 | 4004 行巨石 ComponentRenderer，无法独立测试 |
| 数据 | 6 种数据源 | 组件间无共享，无依赖调度，无级联变量 |
| 交互 | 6 种 action | 无页内下钻，无条件逻辑 |
| 布局 | 绝对定位 | 无自适应，投影变形 |
| 主题 | 3 个内置 | 切换不生效（bug），无自定义，CSS Variables 缺失 |

**代码规模**：38,957 行，37 个 UI 组件文件，17 个测试文件。

## 3. 重构优先级

1. **P0：渲染器分层 + 主题统一**（C + E 合并）
2. **P1：数据联动与下钻**（D）
3. **P2：布局自适应**（A）
4. **P3：双模态体验**（B）

## 4. 架构设计

### 4.1 渲染器分层（P0）

#### 目标架构

```
ComponentShell (入口, ~100行)
  ├── ErrorBoundary (每个组件独立错误边界)
  ├── DataLayer (数据获取+缓存+映射)
  ├── InteractionLayer (action 分发+drill+联动)
  └── RenderLayer (按族分发)
        ├── EChartsRenderer (14种图表)
        ├── DataVRenderer (8种装饰)
        ├── BasicRenderer (16种基础组件)
        ├── FilterRenderer (3种筛选)
        ├── TableRenderer (表格+滚动榜)
        └── PluginRenderer (3D+扩展)
```

#### 三步实施

**第一步：抽取 DataLayer**
- 从 ComponentRenderer 中抽取数据获取逻辑（useCardDataSource 调用、cardDataMapper 调用、effectiveConfig 计算）
- 新建 `renderers/DataLayer.tsx`（~400 行）
- ComponentRenderer 缩小 ~800 行
- 验证：所有现有大屏配置渲染结果不变

**第二步：抽取 InteractionLayer**
- 抽取 action 分发、drill-down 处理、filter variable 更新、click handler
- 新建 `renderers/InteractionLayer.tsx`（~300 行）
- ComponentRenderer 再缩小 ~500 行
- 验证：所有交互行为不变

**第三步：拆分 RenderLayer**
- 按组件族拆分渲染 JSX 为 6 个文件
- ComponentRenderer 变成 ~100 行的 Shell（路由分发 + ErrorBoundary）
- 各渲染器 300-500 行

#### 迁移策略
- 配置格式不变，零破坏
- 新增 `config._version: 2` 字段用于未来的新接口
- 旧配置自动按 v1 路径处理
- 迁移脚本：`scripts/migrate-screen-config-v2.ts`

### 4.2 主题系统重构（P0，与渲染器同步）

#### 现状问题
- 主题 token 通过 props 逐层传递，切换主题时组件不响应
- ECharts 颜色在初始化时注入，不跟随主题变化
- DataV 装饰有独立颜色系统
- 部分组件硬编码颜色

#### 目标架构

```
Theme Selection
  ↓
CSS Variables 注入到 .screen-runtime 容器
  ↓
所有子组件通过 var(--screen-*) 自动响应
```

#### CSS Variables 定义

```css
/* 画布 */
--screen-bg: #0d1b2a;
--screen-bg-secondary: #10233a;
--screen-text: #e6f0ff;
--screen-text-muted: #8da2c3;
--screen-border: rgba(103, 140, 189, 0.18);

/* 品牌色 */
--screen-primary: #22c3ff;
--screen-primary-soft: rgba(34, 195, 255, 0.14);

/* 语义色 */
--screen-success: #3ddc97;
--screen-warning: #ffb74d;
--screen-danger: #ff6b7a;

/* 图表色板 */
--screen-chart-1: #22c3ff;
--screen-chart-2: #3ddc97;
--screen-chart-3: #ffb74d;
--screen-chart-4: #ff6b7a;
--screen-chart-5: #a78bfa;
--screen-chart-6: #54e3ff;

/* 组件 */
--screen-card-bg: rgba(10, 22, 40, 0.7);
--screen-card-border: rgba(34, 195, 255, 0.12);
--screen-card-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
--screen-table-header-bg: #112238;
--screen-table-row-hover: rgba(34, 195, 255, 0.06);
```

#### 内置主题扩展

| 主题 | 风格 | 适用场景 |
|------|------|---------|
| legacy-dark | 深蓝科技 | 传统大屏投屏 |
| titanium | 钛金属灰 | 工业/制造 |
| glacier | 冰蓝清新 | 通用大屏 |
| **light-business** (新) | 浅色商务 | PC 看板/日常分析 |
| **dark-command** (新) | 深色指挥中心 | 军工/sci-fi |
| **brand-custom** (新) | 用户自定义 | 企业品牌适配 |

#### ECharts 主题联动
- ECharts 初始化时从 CSS Variables 读取色板：`getComputedStyle(container).getPropertyValue('--screen-chart-1')`
- 主题切换时调用 `chart.setOption({ color: [...] })`
- 封装为 `useScreenChartColors()` hook

### 4.3 数据联动与下钻（P1）

#### QueryDAG — 依赖调度
- 分析组件的 `cardConfig` 引用，构建 DAG
- 按拓扑序执行查询，被依赖的组件先查
- 循环依赖检测（复用现有 `interactionGraph.ts`）

#### SharedStore — 组件间共享
- 组件可声明 `exports: { key: "totalProjects", path: "data.total" }`
- 其他组件可引用 `{{ shared.totalProjects }}`
- 基于 React Context + 发布订阅模式

#### CascadeVariable — 级联变量
```typescript
interface ScreenGlobalVariable {
  key: string;
  label: string;
  type: 'string' | 'number' | 'date';
  defaultValue: string;
  dependsOn?: string;           // 新增：依赖的变量 key
  optionSourceMode?: 'manual' | 'data';  // 已有
}
```
当 `dependsOn` 的变量变化时：
1. 清空当前变量值
2. 如果 `optionSourceMode === 'data'`，重新加载选项列表
3. 触发依赖链下游的所有组件刷新

#### DrillRouter — 页内下钻
新增 action 类型 `drill-view`：
```typescript
{
  type: 'drill-view',
  viewId: string;         // 目标视图层级
  paramName: string;      // 传递的参数名
  breadcrumbLabel: string; // 面包屑显示文字
}
```
在当前大屏内切换"视图层级"：
- 战略层 → 管控层 → 执行层
- 面包屑导航回溯
- 不跳转 URL，不离开当前页面

### 4.4 布局自适应（P2）

#### 不改编辑器核心
绝对定位是编辑器的基础，改动风险极大。采用运行时适配层。

#### ScaleAdapter 组件
```typescript
interface ScaleAdapterProps {
  designWidth: number;    // 设计稿宽度 (默认 1920)
  designHeight: number;   // 设计稿高度 (默认 1080)
  mode: 'fit' | 'fill' | 'stretch';
  children: ReactNode;
}
```

运行时行为：
1. 检测容器实际尺寸
2. 计算缩放比例 `scale = min(containerW/designW, containerH/designH)`
3. 应用 `transform: scale(${scale})`
4. `fit` 模式：完整显示，可能有黑边
5. `fill` 模式：填充容器，可能裁剪
6. `stretch` 模式：拉伸变形（不推荐）

#### 安装位置
在 `ScreenPreviewPage.tsx` 和 `PublicScreenPage.tsx` 的画布容器外层包裹 `ScaleAdapter`。

## 5. 文件结构变更

```
pages/screens/
  renderers/                    ← 新目录
    ComponentShell.tsx          ← 入口 (~100行)
    DataLayer.tsx               ← 数据层 (~400行)
    InteractionLayer.tsx        ← 交互层 (~300行)
    EChartsRenderer.tsx         ← ECharts 族 (~500行)
    DataVRenderer.tsx           ← DataV 族 (~300行)
    BasicRenderer.tsx           ← 基础组件族 (~400行)
    FilterRenderer.tsx          ← 筛选组件族 (~200行)
    TableRenderer.tsx           ← 表格族 (~300行)
    PluginRenderer.tsx          ← 3D/扩展族 (~200行)
    ScaleAdapter.tsx            ← 自适应缩放 (~80行)
  themes/                       ← 新目录
    screenCssVariables.ts       ← CSS Variable 定义与注入
    builtinThemes.ts            ← 6 个内置主题
    useScreenChartColors.ts     ← ECharts 色板 hook
  hooks/
    useQueryDAG.ts              ← 依赖调度
    useSharedStore.ts           ← 组件间共享
    useCascadeVariable.ts       ← 级联变量
    useDrillView.ts             ← 页内下钻
  components/
    ComponentRenderer.tsx       ← 保留但缩减为 Shell 导入
```

## 6. 实施路线

### Phase 1: 渲染器 + 主题（4 周）
- Week 1: 抽取 DataLayer + CSS Variables 主题注入
- Week 2: 抽取 InteractionLayer + 修复主题切换 bug
- Week 3: 拆分 RenderLayer 为 6 个文件
- Week 4: 内置主题扩展 + 自定义主题面板

### Phase 2: 数据联动（3 周）
- Week 5: QueryDAG + SharedStore
- Week 6: CascadeVariable + 级联筛选 UI
- Week 7: DrillRouter + 页内下钻 + 面包屑

### Phase 3: 布局自适应（2 周）
- Week 8: ScaleAdapter + 三种适配模式
- Week 9: 预览/投屏集成 + 多终端测试

### Phase 4: 双模态 + 打磨（2 周）
- Week 10: 大屏/看板模式切换（基于 DrillRouter）
- Week 11: GPMC 模板迁移到新架构 + 验收

## 7. 约束条件

- Chrome 95+ 兼容（离线环境）
- 无 CDN 依赖
- ARM/Kunpeng + KylinOS 适配
- 现有大屏配置需通过迁移脚本兼容
- ECharts 本地 bundle（已有）
