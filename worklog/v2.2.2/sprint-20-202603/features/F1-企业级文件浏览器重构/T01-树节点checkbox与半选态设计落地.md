# T01: 树节点 checkbox 与半选态设计落地

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: 无

## 目标

为逻辑建模左侧目录树增加多选 checkbox，并支持目录半选/全选状态。

## 技术设计

- 以现有 `SqlModelingPage` 左侧目录树为基础，补充节点级 checkbox 渲染
- 目录节点根据子节点选择状态计算 `checked / indeterminate / unchecked`
- 节点正文点击仍然保留“打开模型编辑”的语义
- checkbox 点击只改批量选择状态，不切换当前编辑模型

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- 新增或抽取的文件浏览器树节点渲染组件
- `source/dts-platform-webapp/src/pages/modeling/sqlModeling.types.ts`

## 验证

- [ ] 左树目录节点可全选/半选/取消选择
- [ ] 勾选模型时不会误切换右侧编辑器
- [ ] 取消勾选后半选状态能正确回退

## 完成标准

- [ ] checkbox 树交互稳定
- [ ] 半选态逻辑正确
- [ ] 与当前单模型编辑逻辑不冲突
