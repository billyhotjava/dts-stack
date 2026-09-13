# T04: PNG/SVG 导出 + minimap

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

让用户能把当前 lineage 图导出为 PNG/SVG 用于汇报，并在右下角显示 minimap 帮助大图导航。

## 技术设计

### Minimap

reactflow 自带 `<MiniMap>` 组件：

```tsx
<MiniMap
  nodeColor={(n) => COLOR_BY_ASSET[n.data.assetType] ?? '#999'}
  maskColor="rgba(0,0,0,0.05)"
  pannable
  zoomable
  position="bottom-right"
/>
```

支持：

- 拖动小图视野（pannable）
- 缩放（zoomable）
- 点击小图位置跳转

### 图片导出

新增 `src/pages/catalog/lineage/utils/export-image.ts`：

```typescript
import { toPng, toSvg } from 'html-to-image';

export async function exportLineageAs(
  format: 'png' | 'svg',
  options: { filename: string; width?: number; height?: number }
) {
  const viewport = document.querySelector('.react-flow__viewport') as HTMLElement;
  const fn = format === 'png' ? toPng : toSvg;
  const dataUrl = await fn(viewport, {
    backgroundColor: '#fff',
    width: options.width,
    height: options.height,
    pixelRatio: 2,
  });
  triggerDownload(dataUrl, `${options.filename}.${format}`);
}
```

引入 `html-to-image`（成熟、reactflow 官方推荐）。

### 导出 UI

工具栏新增下拉菜单：

```
导出 ▼
  ├─ PNG（当前视野）
  ├─ PNG（完整图，2x）
  ├─ SVG
  └─ CSV（保留现有）
```

完整图导出时：

- 临时 `fitView()`
- 用 reactflow 节点 bounding box 计算实际宽高
- 导出后恢复原视野

### 导出注意事项

- 字体：用网页字体导出 PNG 可能丢失，改用系统字体 fallback；或导出前嵌入字体 CSS
- 大图 (>500 节点)：先 toast 提醒"导出可能耗时 5-10s"
- 透明背景选项

## 影响范围

- 修改 `src/pages/catalog/LineagePage.tsx` —— 加 MiniMap
- 新增 `src/pages/catalog/lineage/utils/export-image.ts`
- 新增 `src/pages/catalog/lineage/components/ExportMenu.tsx`
- `package.json` 加 `html-to-image`

## 验证

- [ ] minimap 在右下角显示，拖动/点击正常工作
- [ ] PNG 导出：颜色、文字、图标正确（视觉走查）
- [ ] SVG 导出：在 Figma / Illustrator 中可编辑
- [ ] 完整图导出 200 节点 < 10s
- [ ] 字体降级正常（即使没有网络字体）
- [ ] 大图导出有进度提示
- [ ] 浏览器兼容：Chrome 110+ / Edge / Safari 16+

## 完成标准

- [ ] minimap 接入完成
- [ ] PNG/SVG 导出可用
- [ ] 文档更新（如有用户手册）
- [ ] 浏览器兼容性测试通过
