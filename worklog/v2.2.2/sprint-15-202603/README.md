# Sprint-15: 大屏设计系统重构

**时间**: 2026-03
**状态**: DONE
**目标**: 重构大屏设计器核心架构——渲染器分层拆分、主题系统 CSS Variables 化、数据联动与下钻增强、布局自适应

## 背景

当前大屏设计器（38,957 行代码）面临以下瓶颈：
1. ComponentRenderer.tsx 4004 行巨石，阻塞新功能开发
2. 主题切换不生效（CSS token 通过 props 传递而非 CSS Variables）
3. 组件间无数据共享，无级联筛选，下钻只能跳 URL
4. 绝对定位布局无法自适应多终端
5. 客户需要大屏（展示型）+ 看板（工作型）融合体验

产品定位：DataV（传统 BI 大屏）+ Metabase（现代分析看板）的融合系统。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 渲染器分层拆分 | 3 | DONE — DataLayer/InteractionLayer/6族渲染器已拆分，ComponentRenderer 4004→1809行 |
| F2 | 主题系统 CSS Variables 化 | 3 | DONE — CSS Variables 注入+主题切换+画布设置面板已实现 |
| F3 | 数据联动与下钻增强 | 4 | DONE — QueryDAG/SharedStore/CascadeVariable/DrillRouter 全部完成 |
| F4 | 布局自适应 | 2 | DONE — ScaleAdapter (fit/fill/stretch) 已实现并集成 |
| F5 | 编辑器体验增强 | 5 | DONE — Tab重构/右键菜单/动画/图层拖拽/Table列宽拖拽全部完成 |
| F6 | 数据源增强 | 3 | DONE — Excel/CSV上传已重构为平台级数据库导入(DatabaseUploadTableService) |
| F7 | 仪表盘图表统一为ECharts | 5 | DONE — Line/Bar/Pie/Scalar全部迁移到ECharts，ChartRenderer懒加载 |

## 完成标准

- [x] 编辑器强制暗色主题，统一 AvueData 风格
- [x] 右侧面板从 2Tab 改为 5Tab（样式/数据/交互/图层/高级）
- [x] Canvas 右键上下文菜单（复制/删除/置顶/锁定等）
- [x] 工具栏分组优化 + 格式刷入口
- [x] 组件库 3列卡片 + 分类标签过滤
- [x] 入场动画配置（8种动画 + 自动编排）
- [x] 图层面板拖拽排序
- [x] 分辨率刻度尺（水平+垂直）
- [x] SharedStoreProvider 接入预览运行链路
- [x] useDrillView 接入 ScreenRuntimeContext
- [x] DataLayer 支持 shared store 变量解析
- [x] ComponentRenderer ECharts 懒加载提取为 useEChartsLoader (1969→1807行)（F1）
- [x] 自定义主题编辑面板 — 空选时显示画布设置+6色自定义编辑器（F2）
- [x] Table 列宽拖拽调整 — 表头分隔条拖拽（F5）
- [x] Excel/CSV 上传数据源（F6）— 已重构为平台级数据库导入
- [x] Chrome 95 兼容验证 — antd v5 支持 Chrome ≥ 64
