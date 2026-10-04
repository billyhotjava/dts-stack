# P3-08 富文本编辑器组件

`status`: `done`
`priority`: `P3`
`sprint`: `Sprint 2 - 体验优化`
`inspiration`: `DataEase(所见即所得文本) + GoView(富文本) + 现场说明文字需求`

## 目标

新增所见即所得（WYSIWYG）富文本组件，替代 `title` + `markdown-text` 的组合，降低文本排版门槛。

## 当前问题

1. `title` 组件仅支持单行文本 + 字号/颜色。
2. `markdown-text` 需用户掌握 Markdown 语法。
3. 无法在设计器中直接所见即所得编辑。
4. 缺少插入图标、徽标、表情等能力。

## 子任务

### 1. 新增 `richtext` 组件类型

**文件改动**:
- `types.ts` — 新增 `richtext` 到组件类型联合
- `componentLibrary.ts` — 新增入口

**配置结构**:
```typescript
{
  type: 'richtext',
  config: {
    content: string,        // HTML 内容
    padding: number,        // 内边距
    verticalAlign: 'top' | 'middle' | 'bottom',
    overflow: 'visible' | 'hidden' | 'scroll',
  },
}
```

### 2. 编辑器选型与集成

**推荐方案**: 轻量级 `@tiptap/react`（基于 ProseMirror）。

**理由**:
- 体积小（~50KB gzip，比 CKEditor/Quill 更轻）
- React 原生支持
- 扩展灵活，可按需加载插件
- Chrome 95 兼容（ProseMirror 支持到 Chrome 63+）

**安装依赖**: `@tiptap/react`、`@tiptap/starter-kit`、`@tiptap/extension-color`、`@tiptap/extension-text-align`

### 3. 编辑器工具栏

**文件**: `components/RichtextEditor.tsx`

设计态嵌入式工具栏：
- **文字**: 粗体 / 斜体 / 下划线 / 删除线
- **字号**: 12 / 14 / 16 / 20 / 24 / 32 / 48
- **颜色**: 文字色 / 背景色
- **对齐**: 左 / 中 / 右
- **列表**: 有序 / 无序
- **插入**: 链接 / 分割线
- **清除格式**

**交互**:
- 设计态（mode='designer'）：双击组件进入编辑模式，显示工具栏。
- 预览态（mode='preview'）：仅渲染 HTML，不可编辑。

### 4. 内容安全

- 输出的 HTML 经过白名单 sanitize（允许 p/h1-h6/strong/em/u/s/ol/ul/li/a/br/hr/span）。
- 禁止 script/iframe/style 标签。
- 链接强制 `rel="noreferrer"` + `target="_blank"`。
- 复用 `sanitize.ts` 中的 `escapeHtml` 和 `isSafeSrcUrl`。

### 5. 属性面板集成

**文件**: `PropertyPanel.tsx`

- richtext 组件的属性面板仅展示：内边距、垂直对齐、溢出模式。
- 内容编辑通过画布上的内联编辑器完成（非属性面板）。

### 6. 渲染器实现

**文件**: `ComponentRenderer.tsx` (新增 richtext case)

预览态渲染：
```tsx
case 'richtext':
  return (
    <div
      style={{ padding, overflow, display: 'flex', alignItems: verticalAlign }}
      dangerouslySetInnerHTML={{ __html: sanitizedContent }}
    />
  );
```

## Chrome 95 兼容性

- Tiptap/ProseMirror 支持 Chrome 63+ ✅。
- 无 `contenteditable` 兼容问题。

## 验收标准

- 设计态双击可进入所见即所得编辑。
- 预览态正确渲染样式。
- HTML 输出经过安全白名单过滤。
- 导出 PNG/PDF 包含格式化文本。
- Chrome 95 下编辑和渲染正常。

## 风险与回滚

- 风险：Tiptap 依赖体积增加。
- 回滚：Tiptap 仅在 richtext 组件使用时 dynamic import，不影响其他组件加载。
