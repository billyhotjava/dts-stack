# Sprint-26 IT / Verification

## 目标

验证 Sprint-26 的模块所有权迁移、真实拆页和 DWD/DWS/ADS 约束不破坏 Sprint-25 的菜单 IA。

## 脚本计划

- `scripts/metrics-module-smoke.sh`: 验证 `/metrics/*` 归属 `pages/metrics/**`。
- `scripts/semantic-real-page-smoke.sh`: 验证语义入口不再依赖 modeling 主实现，所有 metrics semantic 子页都是真实页面。
- `scripts/lineage-real-page-smoke.sh`: 验证血缘子页不再只是 LineagePage wrapper。
- `scripts/semantic-contract-smoke.sh`: 验证 DWD 输入、DWS/ADS 输出、旧入口兼容和前序 smoke 全部可执行。

## 证据目录

- `it/evidence/YYYYMMDD-local/`

## 已执行

- `20260502-local/metrics-module`: 指标模块所有权 smoke 通过，覆盖 `/metrics/*` 路由归属与治理旧页面 wrapper 约束。
- `20260502-local/semantic-real-page`: 语义入口真实页面 smoke 通过，覆盖旧 modeling wrapper、overview、subjects、objects、metrics、models、publish、runs 独立 API 读取。
- `20260502-local/lineage-real-page`: 血缘入口真实页面 smoke 通过，覆盖影响分析、图谱、字段血缘、导入、快照对比独立页面和旧入口兼容约束。
- `20260502-local/semantic-contract`: 契约 smoke 通过，覆盖 DWD 输入判断、DWS/ADS 输出判断、旧入口兼容和前序 smoke 可执行。
- `source/dts-platform-webapp`: `pnpm build` 通过。
