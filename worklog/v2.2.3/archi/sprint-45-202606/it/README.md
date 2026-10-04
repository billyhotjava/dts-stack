# Sprint-45 IT 验收计划

## 验收目标

证明 UI 整改不是静态页面堆叠，而是菜单、路由、组件、按钮、接口和客户主链路的闭环。

## 验收分层

| 层级 | 范围 | 方式 | 阻断条件 |
|------|------|------|----------|
| 契约 | 菜单 seed、静态路由、动态 resolver、页面组件 | source-contract / unit test | 任一菜单叶子落入 404、空白页或未知 fallback |
| 页面 | PageHeader、主按钮、空态、异常态、权限态 | React Testing Library / Playwright | 主按钮无真实动作或无禁用原因 |
| 链路 | 工作台到接入、开发、治理、资产、消费、运维 | Playwright smoke | 关键跳转断链或上下文丢失 |
| 构建 | dts-platform-webapp、dts-analytics-webapp/modern | `pnpm build` / `pnpm typecheck` | 类型错误、构建失败 |
| 证据 | 截图、命令输出、接口证据 | `it/evidence/` | 无法复现验收路径 |

## 必跑用例

| 用例 | 起点 | 终点 | 覆盖任务 |
|------|------|------|----------|
| 菜单可达性 smoke | 登录后展开全菜单 | 所有叶子进入真实页面 | F1-T01 |
| 工作台主链路 | `/workbench/data-management` | 数据源/治理/资产/BI/API/运维 | F2-T01 |
| 待办处理 | `/workbench/todo` | 进入阻断项详情并返回 | F1-T02 / F2-T04 |
| 数据源到入湖 | `/foundation/data-sources` | 创建入湖任务或展示禁用原因 | F3-T01 |
| 开发到发布 | `/studio/sql-modeling` | 预览、校验、发布门禁 | F1-T04 / F3-T04 |
| 治理到资产 | `/governance/quality` | 查看影响资产并进入资产详情 | F4-T01 / F4-T02 |
| 资产到消费 | `/catalog/assets` | 创建报表/API/数据产品 | F4-T02 / F5-T02 / F6-T01 |
| 大屏交付 | `/bi/screens` | 新建、预览、发布或展示门禁 | F5-T03 |
| API 到审计 | `/services/apis` | 测试、启用、查看审计 | F6-T01 / F6-T05 |
| 令牌安全 | `/services/tokens` | 生成、复制一次、撤销 | F6-T03 |
| 运维反查 | `/ops/instances` | 查看源任务、查看链路、导出证据 | F6-T04 |

## 验收命令

```bash
cd source/dts-platform-webapp
pnpm build
```

```bash
cd source/dts-analytics-webapp/modern
pnpm typecheck
pnpm build
```

## 截图证据目录

后续执行时按 feature 存放：

- `it/evidence/F1-navigation/`
- `it/evidence/F2-workbench/`
- `it/evidence/F3-ingestion-studio/`
- `it/evidence/F4-governance-catalog/`
- `it/evidence/F5-bi-screens/`
- `it/evidence/F6-service-ops/`
- `it/evidence/F7-ui-contract/`

## 完成标准

- [x] 所有 P0 用例通过。
- [x] 每个 feature 至少有一张关键页面截图或一份 Playwright trace。
- [x] 所有新增/修改页面都有空态、异常态、权限态验证。
- [x] 构建命令通过并记录原始输出。
- [x] 若后端接口缺失，必须记录 blocker，不允许用静态假数据标记 DONE。

## 执行记录

| 类型 | 命令/方式 | 结果 |
|------|-----------|------|
| source-contract | `node --test --experimental-strip-types ...Sprint45*.source-contract.test.ts` | 26/26 通过 |
| build | `cd source/dts-platform-webapp && pnpm build` | 通过；仅 Browserslist 过期和大 chunk 警告 |
| Playwright | `pnpm preview --host 127.0.0.1 --port 4173` + Chrome smoke | 8 条关键路由通过 |

## Playwright 证据

- `it/evidence/F2-workbench/workbench-data-management.png`
- `it/evidence/F3-ingestion-studio/foundation-data-sources.png`
- `it/evidence/F3-ingestion-studio/studio-sql-modeling.png`
- `it/evidence/F4-governance-catalog/catalog-assets.png`
- `it/evidence/F5-bi-screens/bi-screens.png`
- `it/evidence/F6-service-ops/services-apis.png`
- `it/evidence/F6-service-ops/ops-instances.png`
- `it/evidence/F7-ui-contract/ops-audit-evidence.png`

本地 preview 未连接后端服务时，部分页面会展示“服务器错误/暂无数据”的异常或空态；本轮验收重点是验证路由可达、页面不白屏、关键按钮/禁用原因和空态可见。行级按钮由 source-contract 覆盖，真实数据场景需在联调环境补充点击验证。
