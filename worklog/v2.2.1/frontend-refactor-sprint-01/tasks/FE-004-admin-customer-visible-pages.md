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

## 验收

- `pnpm -C source/dts-admin-webapp build`
- 页面头、卡片、表单分组、空态表现一致

## 风险

- 某些后端能力不完整时，需要明确显示“暂无数据/暂不可用”，不能再用占位说明糊住
