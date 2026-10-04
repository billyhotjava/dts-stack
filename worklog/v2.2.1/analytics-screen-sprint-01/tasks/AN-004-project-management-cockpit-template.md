# AN-004

## 标题

新增项目管理高定模板。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`

## 目标

- 用一套可交付的模板表达客户最重视的项目管理作战台
- 把项目管理特有的过滤、下钻、动作入口一次带进模板，而不是留给客户二次拼装

## 布局约束

- 顶部：全局过滤与当前项目状态摘要
- 第一层：项目整体 KPI
- 第二层：里程碑 / 阶段进展
- 第三层：风险 / 堵点 / 变更
- 第四层：责任组织 / 责任人负载
- 第五层：待办 / 问题 / 交付物

## 交互约束

- 支持全局过滤
- 支持多级下钻
- 支持上卷返回
- 支持详情面板入口
- 支持动作入口与意图事件

## 验收

- 从模板创建后可直接进入项目管理驾驶舱
- 预览态能演示过滤、下钻、上卷、打开详情面板
- 关键文案和变量命名贴近项目管理语义

## 当前进度

- 状态：`done`
- 完成项：
  - 已新增 `project-management-cockpit`
  - 已纳入 KPI、里程碑、风险堵点、责任负载、待办/交付物 5 个主区块
  - 已配置基础过滤变量和首版下钻链路

## 风险

- 如果交互 contract 不先定，项目管理模板会变成一堆静态图块
