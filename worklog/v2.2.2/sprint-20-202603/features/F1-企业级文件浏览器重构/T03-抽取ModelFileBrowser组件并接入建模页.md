# T03: 抽取 ModelFileBrowser 组件并接入建模页

**优先级**: P1
**状态**: IN_PROGRESS
**依赖**: T01

## 目标

将左侧目录树重构为独立 `ModelFileBrowser` 组件，降低 `SqlModelingPage` 复杂度，为后续批量操作扩展提供稳定边界。

## 技术设计

- 从 `SqlModelingPage` 中抽离左侧浏览器渲染逻辑
- 组件职责包括：
  - 搜索框
  - 树节点渲染
  - checkbox 交互
  - 已选数量展示
  - 批量动作事件抛出
- 页面层只负责状态持有和事件处理

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- 新增 `source/dts-platform-webapp/src/pages/modeling/components/ModelFileBrowser.tsx`
- 可能新增相关 helper/types 文件

## 验证

- [ ] 文件浏览器可独立渲染和复用
- [ ] 页面功能无回归
- [ ] 目录树性能和交互保持稳定

## 完成标准

- [ ] 左侧文件浏览器完成组件化
- [ ] `SqlModelingPage` 左侧树渲染逻辑明显收敛
