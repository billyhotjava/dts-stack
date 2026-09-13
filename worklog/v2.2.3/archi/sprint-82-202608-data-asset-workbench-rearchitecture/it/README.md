# Sprint-82 集中验证记录

## 1. 结论

代码实现、源契约、组件规则测试、Chrome 95 目标前端构建和 mock-API UI E2E 已完成，Sprint 代码交付状态为 `DONE`。真实认证/API/PostgreSQL 联动在认证前置阻断，作为部署后现场 smoke 单独补证。

本轮未编译、重启或部署容器，未写入标签、资产绑定或其他业务元数据。

## 2. 已通过

| 验证项 | 结果 | 证据 |
|---|---:|---|
| 资产地图/台账 source-contract | 21/21 PASS | 覆盖地图概要化、旧标签入口迁移、台账标签工作区、单治理入口和既有详情能力可达 |
| 标签与治理工作台组件测试 | 29/29 PASS | 覆盖任务推荐、标签关联/解绑回调、能力错误显式提示 |
| mock-API UI E2E | 3/3 PASS | 地图下钻与单治理入口、标签关联回查、768px 窄屏；页面错误和控制台错误均为 0 |
| 前端生产构建 | PASS | `pnpm build`；TypeScript 与 Vite legacy 构建成功，目标为 Chrome 95 |
| 后端聚合器目标测试 | 7/7 PASS | `CatalogAssetOverviewAggregatorTest`，包含标签覆盖率聚合 |
| 格式与补丁检查 | PASS | Biome 定向格式化；`git diff --check` |

前端定向测试文件：

- `AssetOverviewPage.source-contract.test.ts`
- `DatasetsPage.remediation.source-contract.test.ts`
- `Sprint82AssetWorkbench.source-contract.test.ts`
- `AssetGovernanceWorkbenchDrawer.test.ts`
- `TagManagementTab.test.tsx`
- `AssetTagPanel.test.tsx`
- `GovernedAssetTagPanel.test.tsx`

## 3. 与本 Sprint 无关的既有失败

后端完整 Maven `test` 生命周期在 `testCompile` 被并行数据质量重构阻断：4 个质量测试缺少 `QualityAuditRecorder`。绕过该无关 testCompile 阻断、直接运行目标 Surefire 后：

- `CatalogAssetOverviewAggregatorTest`：7/7 PASS。
- `CatalogAssetPortalStatsTest`：14 个测试中有 4 个既有失败，落在未修改的 `CatalogAssetPortalService` 可见性/分页总数与截断基线；本 Sprint 未扩展修复范围。

## 4. 浏览器验收

计划旅程：

1. 资产地图查看标签覆盖并下钻台账。
2. 台账每行只出现“治理资产”。
3. 打开治理工作台，查看当前任务、完整档案和数据标签。
4. 桌面 `1366×768` 与窄屏 `768×900` 检查溢出、控制台和资产 API 错误。

mock-API UI E2E 使用系统 Chrome 通过 3/3，并发现、修复了 768px 下资产范围侧栏和指标卡布局被挤压的问题。验收命令：

2026-08-01 18:16 架构复核重跑：3/3 PASS，耗时 14.8 秒；F1～F4、19 个 Task 均已在执行前确认 DONE。

```bash
E2E_BASE_URL=http://127.0.0.1:4182 \
PLAYWRIGHT_EXECUTABLE_PATH=/usr/bin/google-chrome \
pnpm exec playwright test e2e/sprint82-data-asset-workbench.mock.spec.ts \
  --config playwright.sprint82-mock.config.ts --project=chromium
```

真实认证 UI E2E 仍在全局登录前置终止：

- 仓库 `.env.development` 的代理目标为 `https://platform.dts.local`，当前主机无法解析该域名，Vite 返回 500。
- 当前运行容器 Traefik 路由实际绑定 `bi.yuzhicloud.com`；切换到实际认证入口后，仓库默认 E2E 身份返回 401。
- 因未获得有效测试身份且运行库没有标签样本，本轮不伪造真实联动通过、不读取现场凭据、不写入业务数据。

待补条件：提供可用的只读测试身份或完成测试环境凭据配置后，执行：

```bash
E2E_BASE_URL=http://127.0.0.1:4182 pnpm exec playwright test \
  e2e/sprint82-data-asset-workbench.spec.ts --project=chromium
```

## 5. 发布边界与回退

- 未执行 Docker/Compose 构建、容器重启或部署。
- 未执行数据库迁移。
- 前端可按本 Sprint 变更文件回退；后端仅扩展资产概要响应的标签覆盖字段，旧调用方不依赖新增字段。
- 部署后补齐真实认证 UI E2E，并验证真实标签绑定/解绑与 404 修复链路；该结果与已通过的 mock-API UI E2E 分开记录。
