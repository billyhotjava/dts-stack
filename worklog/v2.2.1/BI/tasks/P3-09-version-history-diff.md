# P3-09 版本历史与配置 diff 对比

`status`: `done`
`priority`: `P3`
`sprint`: `Sprint 3 - 企业能力`
`inspiration`: `DataEase(版本历史) + Git diff 思路 + 现场"谁改了什么"审计需求`

## 目标

提供完整的版本历史浏览和配置变更 diff 对比能力，支持按版本回滚，满足企业审计和变更追溯需求。

## 当前状态

- P0-04 已实现草稿/发布双轨 + 版本号 + 回滚接口。
- P2-02 已实现"版本对比"基础能力（diff 摘要）。
- 缺少：可视化 diff 界面、历史列表、按组件粒度的变更追踪。

## 子任务

### 1. 版本历史列表面板

**新增组件**: `components/VersionHistoryPanel.tsx`

**入口**: ScreenHeader → "历史" 按钮

**展示内容**:
```
┌─────────────────────────────┐
│ 版本历史                     │
│ ┌─────────────────────────┐ │
│ │ v3 · 2026-02-24 14:30   │ │
│ │ 张三 · 已发布            │ │
│ │ 新增 3 组件, 修改 5 属性  │ │
│ ├─────────────────────────┤ │
│ │ v2 · 2026-02-23 16:00   │ │
│ │ 李四 · 已发布            │ │
│ │ 删除 1 组件, 修改 2 数据源│ │
│ ├─────────────────────────┤ │
│ │ v1 · 2026-02-22 10:00   │ │
│ │ 张三 · 首次发布          │ │
│ └─────────────────────────┘ │
│ [回滚到此版本] [对比]        │
└─────────────────────────────┘
```

### 2. 配置 diff 引擎

**新增**: `utils/screenConfigDiff.ts`

**diff 输出结构**:
```typescript
interface ConfigDiffResult {
  summary: {
    componentsAdded: number;
    componentsRemoved: number;
    componentsModified: number;
    canvasChanged: boolean;
    variablesChanged: boolean;
  };
  componentChanges: Array<{
    componentId: string;
    componentName: string;
    type: 'added' | 'removed' | 'modified';
    propertyChanges?: Array<{
      path: string;       // e.g., "config.title", "x", "dataSource.cardConfig.cardId"
      oldValue: unknown;
      newValue: unknown;
    }>;
  }>;
  canvasChanges?: Array<{
    path: string;
    oldValue: unknown;
    newValue: unknown;
  }>;
  variableChanges?: Array<{
    key: string;
    type: 'added' | 'removed' | 'modified';
  }>;
}
```

**算法**:
- 组件级：按 `id` 匹配，新增/删除/修改三分类。
- 属性级：递归深比较 config 对象，忽略 `_` 前缀的内部字段。
- 画布级：比较 width/height/backgroundColor/theme。
- 变量级：按 key 匹配 globalVariables。

### 3. 可视化 diff 面板

**新增组件**: `components/VersionDiffPanel.tsx`

**布局**: 全屏 Modal，左右双栏对比。

**展示**:
- 变更摘要卡片（顶部）
- 组件变更列表：
  - 新增：绿色标记
  - 删除：红色标记
  - 修改：黄色标记 + 展开查看属性变更
- 属性变更：旧值 → 新值，JSON 值高亮差异
- 画布/变量变更独立区域

### 4. 后端版本存储

**文件**: 后端 `ScreenService`

- 每次发布时存储完整 ScreenConfig 快照。
- 版本号自增。
- 版本列表 API：`GET /api/screens/{id}/versions`
- 版本详情 API：`GET /api/screens/{id}/versions/{version}`
- 版本对比 API：`GET /api/screens/{id}/versions/{v1}/diff/{v2}`

### 5. 回滚增强

- 在版本历史面板中选择版本 → "回滚到此版本"。
- 回滚创建新版本（不是覆盖），保持版本链完整。
- 回滚前弹窗确认并展示 diff 摘要。

## Chrome 95 兼容性

- 纯 DOM 操作和 JSON 处理，无 API 依赖限制 ✅。

## 验收标准

- 版本历史列表展示所有发布版本。
- diff 正确识别组件增删改。
- 属性级变更可展开查看具体差异。
- 回滚操作创建新版本。
- Chrome 95 下历史面板正常交互。

## 风险与回滚

- 风险：大量版本占用存储空间。
- 回滚：版本保留策略（如最多保留 50 个版本，超出后自动清理最旧版本）。
