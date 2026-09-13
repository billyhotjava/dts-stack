# Sprint-10: 大屏编辑器 Phase 1 — 组件配置增强 + 主题修复 + 发布弹窗

**时间**: 2026-04
**状态**: READY
**类型**: Implementation（实施型）
**范围**: dts-platform-webapp/src/analytics/pages/screens/

## 目标

1. 用 Schema 驱动架构替换 PropertyPanel.tsx 的硬编码 switch/case,修复全部 47 个组件的配置面板(4 个无配置 + 46 个缺失字段 + 9 处硬编码 + 5 类一致性问题)
2. 修复主题切换只改背景色的 bug
3. 发布结果从内联面板改为弹窗,整合链接 + 分享 + 权限管理

## 非目标(后续迭代)

- 新增图表类型(Phase 2)
- 升级装饰组件(Phase 3)
- PropertyPanel/ScreenHeader 大文件拆分(Phase 4,但本次 Schema 化会自然缩减 PropertyPanel 体积)
- 高级复合编辑器(tooltip-config、series-config、mark-line、map-region 第一版用 JSON 兜底)

---

## A. 组件配置 Schema 化

### A.1 类型系统

```ts
// screens/configSchema/types.ts

type FieldType =
  | 'text' | 'textarea' | 'number' | 'slider' | 'color' | 'gradient'
  | 'boolean' | 'select' | 'radio' | 'icon-select' | 'font-family'
  | 'json' | 'image-url'
  // 复合编辑器(第一版实现)
  | 'color-array'        // 多色选择器
  | 'axis-config'        // 坐标轴配置
  | 'legend-config'      // 图例配置
  | 'column-style'       // 表格列样式
  // 复合编辑器(第一版用 json 兜底,后续迭代)
  | 'tooltip-config' | 'series-config' | 'mark-line'
  | 'map-region' | 'border-box-style' | 'decoration-style'
  | 'conditional-color' | 'key-value-list' | 'field-group';

type ConfigField = {
  key: string;
  label: string;
  type: FieldType;
  group?: string;
  defaultValue?: unknown;
  min?: number;
  max?: number;
  step?: number;
  options?: { label: string; value: string | number | boolean }[];
  placeholder?: string;
  showIf?: (config: Record<string, unknown>) => boolean;
  themeTokenKey?: string;
};

type ComponentConfigSchema = {
  type: ComponentType;
  groups?: { key: string; label: string; defaultOpen?: boolean }[];
  fields: ConfigField[];
};
```

### A.2 统一分组标准

| 组 key | 标签 | 适用范围 | 默认展开 |
|---|---|---|---|
| `content` | 内容 | 全部 | 是 |
| `typography` | 文字 | 有文本的组件 | 是 |
| `appearance` | 外观 | 全部 | 是 |
| `layout` | 布局 | 容器/面板类 | 否 |
| `behavior` | 行为 | 动画/自动播放类 | 否 |
| `chart` | 图表 | ECharts 类 | 是 |
| `header` | 表头样式 | table | 是 |
| `body` | 数据行样式 | table | 是 |
| `column` | 列配置 | table | 否 |
| `pagination` | 分页 | table | 否 |
| `advanced` | 高级 | 全部(可选) | 否 |

### A.3 Schema 共享字段

ECharts 图表共享两组公共字段:

**ECHARTS_COMMON_FIELDS**(所有 ECharts 图表):
- title / titleColor / titleFontSize
- legend(legend-config 复合编辑器)
- tooltip（第一版 json 兜底）
- seriesColors（color-array）
- markLines（第一版 json 兜底）
- animation / animationDuration

**AXIS_CHART_FIELDS**(有坐标轴的图表:line/bar/scatter/combo/waterfall):
- xAxis / yAxis（axis-config 复合编辑器）

### A.4 第一版复合编辑器(4 个)

| 编辑器 | 内部字段 |
|---|---|
| `ColorArrayEditor` | 颜色列表,可增删,拖拽排序 |
| `AxisConfigEditor` | 显示开关 / 标签字号 / 标签颜色 / 标签旋转 / 分隔线开关 / 分隔线颜色 |
| `LegendConfigEditor` | 显示开关 / 位置(top/bottom/left/right) / 字号 / 颜色 |
| `ColumnStyleEditor` | 列列表,每列:宽度 / 对齐 / 格式化 / 条件着色 / 冻结 |

其余复合类型(tooltip-config / series-config / mark-line / map-region / border-box-style / decoration-style / conditional-color)第一版渲染为 JSON 编辑器,后续迭代替换为专有 UI。

### A.5 通用渲染器

`SchemaConfigRenderer` 组件:

1. 从 `COMPONENT_CONFIG_SCHEMAS[componentType]` 取 schema
2. 按 groups 渲染 antd `<Collapse>` 手风琴,每个 group 一个面板
3. 面板内按 fields 顺序渲染 `<FieldEditor>`
4. `FieldEditor` 根据 `field.type` 路由到具体编辑器(Input / InputNumber / ColorPicker / Select / 复合编辑器)
5. `showIf` 条件字段:传入当前 config,返回 false 时隐藏
6. `themeTokenKey` 非空时,在字段 placeholder 显示"主题默认: {tokenValue}"
7. onChange 回调:`(key: string, value: unknown) => void`,由 PropertyPanel 统一 dispatch 到 ScreenContext

### A.6 PropertyPanel.tsx 改造

- 删除「组件配置」section 的整个 switch/case 块(预计 -200KB 代码)
- 替换为 `<SchemaConfigRenderer schema={schema} config={config} onChange={handleConfigChange} theme={theme} />`
- 保留「数据源」「下钻」「交互」「动作」等 section 不变
- Quick 模式 / Advanced 模式的 ECharts 图表配置也统一由 schema 驱动,删除 configMode toggle 逻辑

### A.7 组件覆盖清单

全部 47 个组件类型都需要 schema 定义。按文件分组:

**charts.ts** (14 个): line-chart, bar-chart, pie-chart, gauge-chart, scatter-chart, radar-chart, funnel-chart, map-chart, combo-chart, treemap-chart, sunburst-chart, wordcloud-chart, waterfall-chart, gantt-chart

**basic.ts** (11 个): title, markdown-text, richtext, number-card, datetime, countdown, marquee, carousel, tab-switcher, progress-bar, image, video, iframe

**enterprise.ts** (5 个): stat-card, section-panel, divider, shape, container

**datav.ts** (8 个): border-box, decoration, scroll-board, scroll-ranking, water-level, digital-flop, percent-pond, flyline-chart

**filters.ts** (3 个): filter-input, filter-select, filter-date-range

**table.ts** (1 个): table

**3d.ts** (3 个): globe-chart, bar3d-chart, scatter3d-chart

### A.8 关键修复点(从审计报告)

| 问题 | 修复方式 |
|---|---|
| number-card 缺 suffix | schema 补 `{ key: 'suffix', label: '后缀', type: 'text' }` |
| stat-card 无配置面板 | 新建完整 schema(14 字段) |
| section-panel 无配置面板 | 新建完整 schema(16 字段) |
| divider 无配置面板 | 新建完整 schema(5 字段) |
| gantt-chart 无配置 | 新建 schema(renderMode/sideTextColor + 数据 JSON) |
| title 缺 textAlign/fontWeight | schema 补 2 字段 |
| 全部 filter 缺样式字段 | schema 补 labelColor/inputTextColor/inputBorderColor/inputBackground |
| carousel 硬编码 border/borderRadius | 移到 schema 字段,渲染器读 config |
| progress-bar 硬编码 height:12 | 增加 trackHeight 字段 |
| countdown 硬编码 fontSize | 增加 digitFontSize/labelFontSize 字段 |
| container 缺 borderWidth/borderColor/radius/backgroundColor | schema 补 4 字段 |
| Quick vs Advanced 矛盾 | 统一为 schema 驱动,删除 configMode toggle |

---

## B. 主题系统修复

### B.1 根因

`handleToolbarThemeChange` 只更新 `config.theme` + `config.backgroundColor`。组件 config 中已硬编码旧主题颜色值,渲染器的 `c.color || t.textPrimary` fallback 永远被旧值短路。

### B.2 修复

切换主题时自动调用增强版 `applyThemeToComponents`:

```ts
// ScreenHeader.tsx
const handleToolbarThemeChange = useCallback((e) => {
    const theme = (e.target.value as ScreenTheme) || undefined;
    const tokens = getThemeTokens(theme);
    const updatedComponents = applyThemeToComponents(config.components, theme, 'force');
    updateConfig({
        theme,
        backgroundColor: tokens.canvasBackground,
        components: updatedComponents,
    });
}, [config.components, updateConfig]);
```

### B.3 Schema 驱动的 theme patch

重写 `applyThemeToComponents`:遍历每个组件的 ConfigSchema,找所有 `themeTokenKey` 非空的字段,用新主题的 token 值覆盖 config:

```ts
function applyThemeToComponents(components, theme, mode) {
    const tokens = getThemeTokens(theme);
    return components.map(comp => {
        const schema = COMPONENT_CONFIG_SCHEMAS[comp.type];
        if (!schema) return comp;
        const patched = { ...comp.config };
        for (const field of schema.fields) {
            if (!field.themeTokenKey) continue;
            const tokenValue = resolveTokenValue(tokens, field.themeTokenKey);
            if (mode === 'force' || !patched[field.key]) {
                patched[field.key] = tokenValue;
            }
        }
        return { ...comp, config: patched };
    });
}
```

这样 Schema 新增 `themeTokenKey` 字段时,theme patch 自动跟上,不再遗漏。

### B.4 删除旧代码

- 删除 `screenThemes.ts` 中旧的 `patchComponentConfig` 函数(被新的 schema 驱动逻辑替代)
- 删除 ScreenHeader 中的"应用样式"独立按钮(合并到主题切换动作中)

---

## C. 发布弹窗重构

### C.1 问题

发布成功后的内联面板(ScreenHeader.tsx:2302-2366)占据编辑空间,且与分享/权限功能割裂。

### C.2 方案

新建 `PublishResultModal` 弹窗,整合三块:
1. 发布信息(版本号 + warmup 统计)
2. 链接地址(预览 + 公开,带复制按钮)
3. 分享与权限管理(内嵌 `ScreenGrantManager`)

### C.3 组件拆分

从 `ScreenAclPanel.tsx` 提取核心授权逻辑为无 Modal 包装的 `ScreenGrantManager.tsx`:

```
ScreenGrantManager.tsx       ← 授权列表 + 搜索添加 + 删除(纯内容组件)
  ↑ 被两处复用:
  ├── ScreenAclPanel.tsx     ← Modal 包装,给 ScreenHeader 工具栏"权限"按钮
  └── PublishResultModal.tsx ← 发布弹窗内嵌
```

### C.4 变更清单

| 文件 | 动作 |
|---|---|
| 新建 `ScreenGrantManager.tsx` | 从 ScreenAclPanel 提取,props: `{ screenId, isOwner }` |
| 改造 `ScreenAclPanel.tsx` | 内部改为 `<Modal><ScreenGrantManager /></Modal>` |
| 新建 `PublishResultModal.tsx` | props: `{ open, onClose, publishInfo, screenId, isOwner }` |
| 改造 `ScreenHeader.tsx` | handlePublish 成功后 open PublishResultModal;删除内联面板(2302-2366);删除 publishNotice/publishNoticeDismissed state |

### C.5 PublishResultModal 布局

```
┌─ 发布成功 ──────────────────────────────────────────┐
│                                                      │
│  ✓ 已发布 v{n}（大屏 #{id}）                         │
│  Warmup: 总计 X，成功 Y，跳过 Z，失败 W              │
│                                                      │
│  ┌─ 链接地址 ──────────────────────────────────────┐ │
│  │ 预览链接  https://.../{id}/preview       [复制] │ │
│  │ 公开链接  https://.../public/...          [复制] │ │
│  └─────────────────────────────────────────────────┘ │
│                                                      │
│  ┌─ 分享与权限 ────────────────────────────────────┐ │
│  │ <ScreenGrantManager screenId={id} isOwner />    │ │
│  └─────────────────────────────────────────────────┘ │
│                                                      │
│                              [关闭]  [前往大屏中心]   │
└──────────────────────────────────────────────────────┘
```

---

## D. 文件结构

```
screens/
  configSchema/                          ← 新增目录
    types.ts                             ← ConfigField / FieldType 类型
    schemas/
      index.ts                           ← 导出 COMPONENT_CONFIG_SCHEMAS
      common.ts                          ← ECHARTS_COMMON_FIELDS, AXIS_CHART_FIELDS
      charts.ts                          ← 14 个 ECharts 图表 schema
      basic.ts                           ← 11 个文本/展示/媒体 schema
      enterprise.ts                      ← 5 个企业组件 schema
      datav.ts                           ← 8 个 DataV 组件 schema
      filters.ts                         ← 3 个筛选器 schema
      table.ts                           ← table schema
      three-d.ts                         ← 3 个 3D 图表 schema
    editors/
      SchemaConfigRenderer.tsx           ← 通用渲染器
      FieldEditor.tsx                    ← 基础字段路由
      ColorArrayEditor.tsx               ← 多色选择器
      AxisConfigEditor.tsx               ← 坐标轴编辑器
      LegendConfigEditor.tsx             ← 图例编辑器
      ColumnStyleEditor.tsx              ← 表格列样式编辑器
  components/
    ScreenGrantManager.tsx               ← 新:从 ScreenAclPanel 提取
    PublishResultModal.tsx               ← 新:发布弹窗
    ScreenAclPanel.tsx                   ← 改:内部用 ScreenGrantManager
    PropertyPanel.tsx                    ← 改:组件配置 section 替换为 SchemaConfigRenderer
    ScreenHeader.tsx                     ← 改:主题切换 + 发布流程
  screenThemes.ts                        ← 改:applyThemeToComponents 重写
```

---

## E. 迁移策略

1. **先建 Schema 基础设施**:types.ts + SchemaConfigRenderer + FieldEditor + 4 个复合编辑器
2. **逐批定义 Schema**:按文件分组(basic → enterprise → table → charts → datav → filters → 3d),每批完成后验证渲染效果
3. **PropertyPanel 切换**:用 `COMPONENT_CONFIG_SCHEMAS[type] ? <SchemaConfigRenderer> : 旧switch/case` 做渐进切换,全部组件 schema 就绪后删除旧代码
4. **主题修复**:Schema 全部就绪后(themeTokenKey 字段到位),重写 applyThemeToComponents
5. **发布弹窗**:独立于 Schema 工作,可并行

---

## F. 验收标准

- [ ] 全部 47 个组件在 PropertyPanel「组件配置」section 有可用编辑 UI
- [ ] 所有渲染器读取的 config 字段在 schema 中有对应 field
- [ ] 切换主题后,所有组件的文字色、背景色、图表配色跟随主题变化
- [ ] 发布成功后弹出 Modal,内含链接 + 分享权限管理
- [ ] 内联发布面板已删除
- [ ] TypeScript 编译 0 错误
- [ ] 各组件编辑体验统一(分组/命名/排列一致)
