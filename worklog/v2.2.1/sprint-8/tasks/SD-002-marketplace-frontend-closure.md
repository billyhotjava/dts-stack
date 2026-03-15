# SD-002

## 标题

收口 `ScreenMarketplacePage`，把市场页从占位感页面升级为可交付页面。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenMarketplacePage.tsx`
- `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenMarketplacePage.test.tsx`

## 目标

- 真实消费 marketplace 后端接口
- 明确加载态、空态、失败态、安装态
- 去掉“市场暂无内容，敬请期待”式的占位感

## 交付

- 市场列表真实渲染
- 安装动作进度与结果反馈
- 详情查看或等价说明面板
- 组件测试覆盖基本状态切换

## 验收

- 打开市场页不再只看到占位文案
- 组件与模板 tab 都能请求真实接口
- 安装成功/失败有明确提示，不是静默失败

## 当前进度

- 状态：TODO
- 备注：优先保留现有页面信息架构，避免在本 Sprint 做大改版

## 风险

- 若后端字段不稳定，前端容易退化成兼容分支堆积
