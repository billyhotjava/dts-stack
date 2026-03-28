# T05: ScreenHeader 导入流程增强

**优先级**: P0
**状态**: READY
**依赖**: T03, T04

## 目标

将编辑器内的 JSON 导入从"直接覆盖+alert"改为"预览Modal+操作选择+资源还原"。

## 技术设计

文件: `pages/screens/components/ScreenHeader.tsx`

**改造 `handleImportJson`:**

现有流程（30行）:
```
文件 → parse → normalize → validate → loadConfig → alert
```

新流程:
```
文件 → parse → normalize → validate
  → 计算 inlinedResourceCount (扫描 data: URL 数量)
  → 打开 ImportPreviewModal(mode="editor")
  → 用户选择:
    A) 替换当前 → restoreResources → loadConfig
    B) 创建新大屏 → restoreResources → createScreen → navigate
```

**新增状态:**
```typescript
const [importModalState, setImportModalState] = useState<{
  open: boolean;
  fileName: string;
  parsedSpec: ScreenConfig | null;
  validation: { errors: string[]; warnings: string[] };
  resourcesInlined: boolean;
  inlinedResourceCount: number;
} | null>(null);
```

**回调实现:**

`onReplaceCurrentScreen`:
1. 如果 resourcesInlined，调用 `restoreResources(spec)`
2. 调用 `loadConfig(materializeScreenPage(config, currentPageIndex))`
3. 关闭 Modal

`onCreateNewScreen`:
1. 如果 resourcesInlined，调用 `restoreResources(spec)`
2. 调用 `analyticsApi.createScreen({ name, ...spec })`
3. `navigate(/screens/${newId}/edit)`
4. 关闭 Modal

## 影响范围

- 修改: `ScreenHeader.tsx` — handleImportJson 重写、新增 ImportPreviewModal 渲染

## 验证
- [ ] 选择 JSON 文件后弹出预览 Modal（不再直接覆盖）
- [ ] Modal 显示正确的基本信息和校验结果
- [ ] "替换当前"正确替换配置
- [ ] "创建新大屏"创建新大屏并跳转
- [ ] 含内联资源的 JSON 导入后资源正确还原
- [ ] 校验失败时无法确认导入

## 完成标准
- [ ] 导入流程从"无确认直接覆盖"升级为"预览+选择+确认"
