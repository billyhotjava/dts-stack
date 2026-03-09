# AN-002

## 标题

增强模板库分类、标签与模板元数据表达。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/TemplateGallery.tsx`
- `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`

## 目标

- 模板库能按客户语义分类浏览
- 内置模板与资产模板在列表中使用一致的分类文案
- 为后续 5 套模板提供更清晰的搜索、标签、简介和视觉区分

## 交付

- 分类标签新增并在模板库可见
- 内置模板卡片文案和标签补齐
- 模板搜索能覆盖名称、描述、分类、标签

## 验收

- 模板库可筛出 `QMS / PLM / HR / 财务 / 项目管理`
- 旧分类模板仍能正常显示
- 新分类不会影响资产模板列表加载

## 当前进度

- 状态：`done`
- 完成项：
  - `TemplateGallery.tsx` 已接入新的分类标签与有序分类列表
  - 模板卡片已显示尺寸、组件数量、预置变量数量
  - 搜索占位与内置/资产模板分类展示已同步升级

## 风险

- 资产模板接口返回的分类可能仍是旧枚举，UI 显示需要兼容未知值
