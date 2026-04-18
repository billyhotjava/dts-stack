# T04: Stub 最小管理页

**优先级**: P1
**状态**: READY
**依赖**: T02

## 目标
给运维/测试提供最小 UI，列出待审审批单，可手动 approve / reject / 延迟；显示审批单的 classification 和 bizContext。不对业务用户开放。

## 技术设计
详细方案在 F1 brainstorming 后续细化，本文件为 spec 就绪后的执行占位。

## 影响范围
- 新增前端（可嵌在 dts-admin 的运维子页，或独立简单页）
- 走 dts-proxy 路由，基础 HTTP Auth

## 验证
- [ ] 可在 UI 中手动驱动审批单走完三态

## 完成标准
- [ ] 管理页上线，F1-full 出来后可丢弃
