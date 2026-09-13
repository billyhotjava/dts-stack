# F5：集中验证与交付证据

**优先级**：P0
**状态**：IMPLEMENTATION_DONE（T01/T02/T04 完成；T03 E2E 待授权账号实机执行，BLOCKED_E2E_INPUT）

## 证据登记（2026-08-09）

| 类别 | 证据 | 结果 |
|---|---|---|
| 代码/契约 | `node --test` 全契约 93/100（7 项失败全为基线既有：seed 结构过期×1、台账行数超限×1、Sprint-45/49/72 断言过期×4、F5-T04 security 深链过期×1）；Vitest 15/15；biome/tsc 全绿 | PASS |
| 构建 | `LEGACY_BROWSER_BUILD=1 pnpm build` exit 0（本地）；`dts-build.sh --image dts-platform-webapp` 成功 | PASS |
| 部署 | compose 重建 v223-dts-platform-webapp-1；data-modeling/catalog/assets/ledger/search/lineage/graph/approval/asset-detail 全 200；深链 `?unclassified=1`、`?action=new&assetType=dataset`、`?direction=BOTH&depth=3` 200 | PASS |
| 真实 E2E | 一次性联合验证 Runbook 见 `e2e-runbook.md`（需授权账号+Chrome 实机） | BLOCKED_E2E_INPUT |
| 回滚锚点 | 当前镜像 digest `sha256:2442012bd2526389…`；回滚目标 `sha256:e1c63c07d97c1bc62…` | 已登记 |

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
