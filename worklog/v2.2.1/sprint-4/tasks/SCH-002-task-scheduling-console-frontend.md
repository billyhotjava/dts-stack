# SCH-002: TaskSchedulingPage 从导航壳页升级为调度控制台

## 范围

- `source/dts-platform-webapp/src/pages/foundation/TaskSchedulingPage.tsx`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- 相关调度控制台组件

## 目标

- 让任务调度页成为一个真正可读、可操作、可跳转的控制台

## 交付

- 调度总览卡片
- 最近执行列表
- 失败任务聚合
- 控制动作入口与 drill-down 跳转

## 验收

- 页面不再只是 3 张入口卡片
- 用户可直接查看最近执行和关键状态
- 页面具备加载态、空态、错误态和稳定 selector

## 当前进度

- 状态：TODO
- 备注：一期允许部分控制动作跳到既有详情页完成
