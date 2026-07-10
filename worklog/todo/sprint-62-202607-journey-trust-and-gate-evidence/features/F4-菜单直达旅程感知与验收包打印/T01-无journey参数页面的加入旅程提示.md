# T01: 无 journey 参数页面的加入旅程提示

**优先级**: P1
**状态**: DONE
**依赖**: 无

## 目标
菜单直达旅程相关页面时，给一条轻量、可关闭的"此页面属于数据产品旅程第 N 步"提示，点击带上下文进入旅程。

## 技术设计
- `JourneyContextBar.tsx` 新增 joinable 模式：
  - `context.enabled === false` 且当前 stage 属于旅程 8 阶段 → 渲染细提示条（非 journey 全宽条），文案"此页面是数据产品旅程的第 N 步 · 从工作台开始完整旅程"，按钮"进入旅程"（buildJourneyUrl 当前路由）与关闭 ×。
  - 关闭状态写 sessionStorage（key 带 stage），会话内不再出现；storage 异常静默。
  - 判定逻辑抽纯函数 `resolveJourneyBarMode(context, dismissed)` 返回 "journey" | "joinable" | "hidden"。
- 不打扰：joinable 条视觉弱化（浅底、单行），不遮挡页面主按钮。

## 影响范围
- `JourneyContextBar.tsx` + 其 source-contract 测试增补

## 验证
- [ ] 三模式判定测试；关闭记忆测试（注入 storage）。

## 完成标准
- [ ] journey 模式行为零变化；测试 GREEN；build 通过。
