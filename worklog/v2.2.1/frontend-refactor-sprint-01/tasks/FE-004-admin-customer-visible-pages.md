# FE-004

## 标题

重构 `admin-webapp` 客户可见页面。

## 范围

- `source/dts-admin-webapp/src/admin/views/infra-settings.tsx`
- `source/dts-admin-webapp/src/admin/views/workflow-config.tsx`
- `source/dts-admin-webapp/src/admin/views/system/other-config.tsx`
- 与这些页面关联的路由与页面头部结构

## 目标

- 去掉占位文案与无效配置块
- 页面采用统一的 `PageHeader + SectionCard + FilterBar` 语言
- 让 admin 看起来像企业控制台，而不是 demo 配置页集合

## 交付

- 基础设施、工作流、系统配置三个入口先完成风格和结构统一
- 页面进入后不再出现占位内容

## 当前进度

- 已新增共享页面骨架 `admin/components/console-page.tsx`
- `infra-settings.tsx` 已重构为概览头部 + 摘要卡片 + 服务面板布局
- `workflow-config.tsx` 已重构为概览卡片 + 过滤条 + 配置表控制台
- `system/other-config.tsx` 已改造成真实的系统策略与门户治理总览页
- `/admin/system` 路由已切换到新的系统总览页，不再跳转到占位页面
- `pnpm -C source/dts-admin-webapp build` 已通过

## 验收

- `pnpm -C source/dts-admin-webapp build`
- 页面头、卡片、表单分组、空态表现一致

## 风险

- 某些后端能力不完整时，需要明确显示“暂无数据/暂不可用”，不能再用占位说明糊住

## 本轮验证

- `pnpm -C source/dts-admin-webapp build`
