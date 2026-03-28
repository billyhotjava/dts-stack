# T03: ImportPreviewModal — 导入预览弹窗

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

创建共用的导入预览弹窗组件，在编辑器和模板市场中使用。

## 技术设计

文件: `pages/screens/components/ImportPreviewModal.tsx`

使用项目现有 UI 组件（Modal/Drawer、Button 等）。

**Props:**
```typescript
type ImportPreviewModalProps = {
  open: boolean;
  onClose: () => void;
  fileName: string;
  parsedSpec: ScreenConfig;
  templateMeta?: { name: string; description?: string; category?: string; tags?: string[] };
  validation: { errors: string[]; warnings: string[] };
  resourcesInlined: boolean;
  inlinedResourceCount: number;
  mode: "editor" | "marketplace";
  onReplaceCurrentScreen: () => void;
  onCreateNewScreen: () => void;
  onRegisterAsTemplate: () => void;
};
```

**UI 布局:**
- 文件名显示
- 基本信息卡片：名称、尺寸(width×height)、组件数、主题、资源内联状态
- 校验结果：errors 红色显示（阻断导入）、warnings 黄色显示（不阻断）
- 导入方式选择（radio group）：
  - mode="editor": "替换当前大屏" | "创建为新大屏"
  - mode="marketplace": "注册为模板" | "创建为大屏"
- 底部按钮：取消 | 确认导入（errors > 0 时 disabled）

**交互:**
- 有 errors 时确认按钮 disabled，提示修复后重试
- 选择操作后点确认，调用对应的 onXxx 回调
- 回调内部负责资源还原（如果 resourcesInlined）和实际操作

## 影响范围

- 新文件: `pages/screens/components/ImportPreviewModal.tsx`
- 被 ScreenHeader (T04) 和模板市场 (T07) 使用

## 验证
- [ ] Modal 正确显示大屏基本信息（名称、尺寸、组件数）
- [ ] 校验 errors 时确认按钮 disabled
- [ ] mode="editor" 时只显示"替换当前"和"创建新大屏"
- [ ] mode="marketplace" 时只显示"注册为模板"和"创建为大屏"
- [ ] 点击确认调用正确的回调

## 实施步骤

> 详见 `implementation-plan.md` Task 3

- [ ] 创建 `pages/screens/components/ImportPreviewModal.tsx`，完整代码见 plan
- [ ] 使用现有 `Modal` 组件 (`ui/Modal/Modal`)
- [ ] 支持 radio group 选择导入方式
- [ ] TypeScript 编译验证通过
- [ ] 提交: `feat(F1/T03): add ImportPreviewModal for import preview and action selection`

## 完成标准
- [ ] 组件在两种 mode 下正确渲染
- [ ] 校验错误时阻断导入
