# T03: 修复 userStore.ts 缩进损坏

**严重度**: Critical
**文件**: `source/dts-platform-webapp/src/store/userStore.ts`

## 问题

第 78 行 `ctx.actions.setActiveDept(undefined)` 缩进丢失（tab 缺失），暗示可能有 merge 问题。
虽然 JavaScript 不依赖缩进做作用域，但需要确认该行在正确的 try 块内。

## 修复方案

修正缩进对齐，确认 `setActiveDept` 调用在 `try` 块内与相邻语句同级。

## 验证

- 代码缩进与上下文一致
- 登出流程正常
