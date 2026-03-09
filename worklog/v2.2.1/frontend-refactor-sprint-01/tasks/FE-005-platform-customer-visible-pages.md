# FE-005

## 标题

重构 `platform-webapp` 客户可见页面。

## 范围

- `source/dts-platform-webapp/src/pages/foundation/TaskSchedulingPage.tsx`
- 平台工作台、治理、开发相关客户入口的页面头与卡片结构

## 目标

- 清掉客户可见的预留、噪音和假内容
- 统一平台页的 header、metric card、信息卡片、状态区
- 让平台页和 admin 页形成同一产品语言

## 交付

- 先处理调度与工作台类页面
- 后续同样的视觉 contract 可以继续横向套用到其他平台页面

## 验收

- `pnpm -C source/dts-platform-webapp build`
- 页面信息组织明显优于当前旧后台式布局

## 风险

- 平台页面信息密度通常比 admin 更高，不能只做“换皮”，还要处理信息分区
