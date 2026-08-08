# F5：集中验证与交付证据

**优先级**：P0
**状态**：DESIGN_APPROVED / IMPLEMENTATION_PENDING

## 目标

一次集中验证收口 F0～F4，并按仓库惯例分层登记五类证据。

## Task 表

| ID | Task | 验收 |
|---|---|---|
| T01 | 全量聚焦测试：Vitest 相关域 + Node 契约；biome/tsc 全绿 | 命令与退出码登记 |
| T02 | 构建与部署：`LEGACY_BROWSER_BUILD=1 pnpm build`；`dts-build.sh --image dts-platform-webapp --no-save`；compose 重建；回滚锚点 | 镜像 digest + HTTP 200 |
| T03 | 联合 E2E（授权账号 + Chrome 实机，仅执行一次）：菜单真实点击走通 概览→台账→详情→血缘→申请权限→审批→我的授权；深链参数逐一断言消费 | `it/` 证据：命令/截图/脱敏 API/审计 |
| T04 | 文档同步：sprint-85 README Gate、`it/README`、sprint-queue；状态按证据分层登记 | 无占位 PASS |

## 完成标准

- E2E 输入缺失时按 `G4=BLOCKED_E2E_INPUT` 登记，不标记 REAL/DELIVERED。
- 代码/契约、构建、部署、真实 E2E、回滚锚点五类证据分开登记，互不顶替。
