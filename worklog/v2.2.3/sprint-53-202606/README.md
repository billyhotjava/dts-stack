# Sprint-53: dts-metrics 默认退役与指标路由收敛

**时间**: 2026-06  
**状态**: DONE  
**类型**: Retirement / Menu Route Convergence / Frontend-first Migration  
**目标**: 将旧 `dts-metrics` 服务从默认产品入口、默认运行面和默认构建链路中退役，把指标与语义能力收敛到 v2.2.3 现有平台页面，保留必要旧链接兼容和可回滚证据。

## 背景

Sprint-52 已把指标工作台与 6 个 Semantic 页面落到 `dts-platform-webapp` 的 `/modeling/metric-workbench` 与 `/modeling/semantic/*` 路由。当前用户已开始把门户菜单中的“指标与语义”从 BI 应用下移出，转到数据开发下的“指标建模”。下一步不能直接删除 `source/dts-metrics`，因为历史上它仍承载过 `/api/metrics/*`、`/metrics/*`、旧 iframe 路由、构建脚本和 compose 默认服务。

本 sprint 的原则是先完成“默认退役”：用户菜单不再进入旧服务、旧链接兼容跳到平台新页面、默认 compose/build/init 不再启动或构建 `dts-metrics`。只有当 Sprint-54 黄金链路和 Sprint-55 可视化指标完全接住关键能力后，才进入物理删除模块。

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 |
|----|---------|--------|---------|------|
| F0 | 退役边界与能力盘点 | P0 | 3 | DONE |
| F1 | 菜单角色路由收敛 | P0 | 4 | DONE |
| F2 | 默认运行面退役 | P0 | 4 | DONE |
| F3 | 平台语义指标接管 | P0 | 4 | DONE |
| F4 | 验证收尾 | P0 | 3 | DONE |

## 完成标准

- [x] 门户菜单、默认角色、locale、静态路由和动态 resolver 对指标入口只有一套平台内 canonical 路由。
- [x] `/bi-apps/metrics/*`、`/modeling/semantic-center/*`、`/bi/semantic-modeling` 不再 iframe 旧服务；兼容跳转到平台新页面并保留 query/hash。
- [x] 默认 `docker-compose-app.yml`、`init.sh`、`imgversion*.conf`、`builds/dts-build.sh` 不再把 `dts-metrics` 作为默认启动/构建项。
- [x] 平台新页面接住 Sprint-52 已知缺口：业务对象表映射编辑、发布链路失败态、黄金链路 `MODEL_READY` 路由。
- [x] 保留 `dts-metrics` 物理代码目录和 legacy/rollback 说明，不做高风险大删除。
- [x] source-contract、TypeScript、构建/compose 渲染检查和 sprint IT 证据完整。

## 关键约束

- 不删除用户当前未提交的 `portal-menu-seed.json` 调整；本 sprint 以该方向为输入继续收敛。
- 不触碰 `addax-env-runner.jar`。
- 不新增 `/v2` 路由；不新增平行指标中心菜单。
- Chrome 95 兼容：新增 UI 或样式仍禁 `oklch`、`:has()`、container query。
- 后端 API 只补平台页面接管所需的最小缺口，不为退役本身做泛化重构。

## 后续依赖

- Sprint-54：数据源 -> 数据连接 -> 数据资产 -> 数据质量黄金线打通。
- Sprint-55：可视化指标完善，并在平台能力完全接住后评估 `source/dts-metrics` 物理删除。

## 资产

- 退役矩阵: `assets/dts-metrics-retirement-matrix.md`
- 页面能力矩阵: `assets/page-capability-matrix.md`
- API/运行面迁移清单: `assets/runtime-api-migration-register.md`
- 集成测试计划: `it/README.md`
