# P3-11 大屏多页轮播

`status`: `done`
`priority`: `P3`
`sprint`: `Sprint 3 - 企业能力`
`inspiration`: `商业大屏产品(多页翻页) + 现场指挥中心大屏轮播展示需求`

## 目标

支持单个大屏项目包含多个"页面"，预览/公开展示时自动轮播切换，满足指挥中心多场景大屏循环展示需求。

## 当前状态

- 当前大屏为单页结构（一个 `ScreenConfig` = 一页）。
- 如需多页展示，用户需创建多个大屏并手动切换。
- 缺少页面级的统一管理和自动轮播。

## 子任务

### 1. 数据模型扩展

**文件**: `types.ts`

```typescript
interface ScreenConfig {
  // 现有字段...
  pages?: ScreenPage[];           // 多页配置（可选）
  carouselConfig?: {
    enabled: boolean;
    intervalSeconds: number;      // 默认 30
    transition: 'fade' | 'slide-left' | 'slide-up' | 'none';
    transitionDuration: number;   // 默认 800ms
    loop: boolean;                // 默认 true
  };
}

interface ScreenPage {
  id: string;
  name: string;
  components: ScreenComponent[];
  backgroundColor?: string;
  backgroundImage?: string;
}
```

**向后兼容**: 当 `pages` 为空时，使用顶层 `components` 数组（单页模式）。

### 2. 设计器页面管理

**新增组件**: `components/PageManagerPanel.tsx`

**入口**: 设计器底部 Tab 栏或左侧面板。

**功能**:
- 页面缩略图列表（横向排列）。
- 新增/删除/复制/排序页面。
- 点击切换当前编辑页面。
- 当前页面高亮显示。
- 页面重命名（双击标签编辑）。

### 3. ScreenContext 多页支持

**文件**: `ScreenContext.tsx`

- 新增 `currentPageIndex` state。
- `config.components` 代理为 `config.pages[currentPageIndex].components`。
- 页面切换时保留各页面的 undo 历史（或统一历史栈）。
- 全局变量跨页面共享。

### 4. 预览/公开页轮播

**文件**: `ScreenPreviewPage.tsx`、`PublicScreenPage.tsx`

**轮播逻辑**:
```typescript
const [pageIndex, setPageIndex] = useState(0);
useEffect(() => {
  if (!carouselConfig?.enabled || pages.length <= 1) return;
  const timer = setInterval(() => {
    setPageIndex(prev => carouselConfig.loop
      ? (prev + 1) % pages.length
      : Math.min(prev + 1, pages.length - 1));
  }, carouselConfig.intervalSeconds * 1000);
  return () => clearInterval(timer);
}, [carouselConfig, pages.length]);
```

**切换动画**:
- `fade`: opacity 过渡
- `slide-left`: translateX 过渡
- `slide-up`: translateY 过渡

**手动控制**:
- 底部页码指示器（圆点）。
- 左右箭头键手动翻页。
- 点击指示器跳转到指定页。

### 5. 轮播配置属性面板

**文件**: `PropertyPanel.tsx` (画布级配置)

- 轮播开关。
- 切换间隔（秒）。
- 过渡动画类型。
- 过渡时长（ms）。
- 循环模式开关。

### 6. 导出支持

- PNG 导出：导出当前页或全部页。
- PDF 导出：每页一个 PDF 页面。

## Chrome 95 兼容性

- CSS `transition`/`transform` Chrome 95 ✅。
- `setInterval` 无兼容问题。

## 验收标准

- 设计器可创建多页大屏并在页面间切换编辑。
- 预览时自动按设定间隔轮播。
- 过渡动画流畅（fade/slide）。
- 手动翻页正常工作。
- 单页大屏向后兼容（无 pages 时等同当前行为）。
- Chrome 95 下轮播和动画正常。

## 风险与回滚

- 风险：多页导致 ScreenConfig 体积膨胀。
- 回滚：限制最大页数（如 20 页），超出提示拆分为独立大屏。
