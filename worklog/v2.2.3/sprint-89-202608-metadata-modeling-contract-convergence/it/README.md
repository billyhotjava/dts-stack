# Sprint-89 集成验收计划

**当前状态**: AUTOMATED_PASS / REAL_INPUT_BLOCKED（自动化影响集通过；真实登录、Chrome 95、部署与现场来源样本未提供）

| IT | 旅程 | 核心断言 | 当前状态 |
|---|---|---|---|
| IT-01 | CATALOG_TABLE 身份解析 | table locator 可解析父 dataset asset key；编译物理位置与分类 subject 一致 | PASS_AUTOMATED |
| IT-02 | 采集消失/重现 | v1 同步 → 表消失 → v2 重现，三层 ID 不变且无 routine DELETE | PASS_AUTOMATED |
| IT-03 | drift 分级 | add nullable、remove unused、remove used、type change、nullable tighten、comment-only 分级正确 | PASS_AUTOMATED |
| IT-04 | 建模影响与重确认 | compatible 可继续且提示；breaking/missing 阻断；修复并重确认后恢复 | PASS_AUTOMATED |
| IT-05 | 租户/权限/分类 | 跨租户、无目录权限、部门不匹配均不泄露；分类继承使用 canonical asset key | PASS_AUTOMATED |
| IT-06 | 来源盘点 UI | 真实菜单进入现有数仓规划来源页；四态、diff、重确认、排除均可用 | BLOCKED_INPUT |
| IT-07 | 回滚/兼容 | 旧 locator fixture 可读；回滚后无数据丢失；若有 migration 完成 dry-run/rollback | PASS_AUTOMATED_NO_MIGRATION |
| IT-08 | 全纵向链 | collect v1 → catalog → plan confirm → reverse/import → compile/publish/materialize → collect v2 → policy/处置 | BLOCKED_INPUT_REAL_CHAIN |

## 自动化执行证据（2026-08-10）

- 采集稳定身份：`CatalogColumnSyncServiceTest,SourceReferenceResolverAdapterTest,RoutineCatalogSyncSourceContractTest,JdbcClassificationLifecycleIT`，15 tests，0 failures/errors/skipped。
- drift 分级：`SchemaDriftDetectorTest,SchemaDriftDetailsReaderTest,ModelSpecRepositoryIT#classifiesReferencedAndUnreferencedCatalogColumnsFromCurrentModelSpecs`，18 tests，0 failures/errors/skipped。
- 集中影响集：resolver、repository、sync、drift、来源盘点、compiler、classification、materialization 共 97 个唯一用例；首次 80 通过，17 个旧 stage-gate 夹具失败；夹具补齐既有业务过程/主题域引用后，定向重跑 31/31 通过，最终影响集 97/97。
- 前端：`modelingImportContextService.sprint89.test.ts` 1/1 通过；`pnpm build` 成功。
- 本轮无数据库 migration；旧三参数来源读取和未分页客户端兼容行为有自动化断言。未执行部署或真实浏览器操作。

## 补充自动化证据（2026-08-11：旧目录资产治理）

- 后端：`CatalogAssetPortalServicePermissionParityTest,CatalogAssetPortalTagFilterTest` 共 28 tests，0 failures/errors/skipped；覆盖普通可读旧资产、拒绝越权读取、未分类旧资产治理入口、最小详情防泄露、映射资产权限和标签身份。
- 前端：`DatasetDetailPage.source-contract.test.ts` 10/10 通过；`pnpm build`（`LEGACY_BROWSER_BUILD=1`）成功。
- 本地 mock-API 浏览器冒烟：`/catalog/datasets/{legacyId}` 在 1366×768 和 768×900 均可打开统一详情并进入“治理责任”；保存请求命中 `PATCH /api/catalog/assets-v2/{legacyId}/governance`；窄屏标题宽 227px、状态卡宽 200px、无横向溢出；console/page/request failure 均为 0。
- 截图生成于本地临时目录：`/tmp/sprint89-legacy-asset-detail-1366x768.png`、`/tmp/sprint89-legacy-asset-governance-768x900.png`。该证据使用本机构建和测试拦截数据，不替代部署环境、真实账号、真实旧资产或现场 Chrome 95 验收。

## 补充自动化证据（2026-08-12：数据资产目录简化重构）

- 页面组件与辅助函数测试 12/12 通过；目录职责、历史入口兼容、表格分页、批量动作、详情跳转和移除行内工作台的聚焦契约 18/18 通过。
- `LEGACY_BROWSER_BUILD=1 pnpm build` 成功，完成 TypeScript 检查并生成兼容构建；既有动态/静态混合导入、caniuse 数据陈旧和大分块告警仍存在，本次未新增构建错误。
- 本地 mock-API Playwright 冒烟 1/1 通过：`/catalog/search` 默认以空筛选加载全部可见资产，仅保留紧凑搜索模块、可勾选 `CompactTable`、批量归域/标签关联与分页；无资产名片、治理指标、统计投影、行内治理/权限动作和抽屉工作台。选择批量归域目标与资产后动作可用，点击资产名称进入 `/catalog/datasets/{id}`。
- 1366×768 与 768×900 均无页面级横向溢出，筛选控件不越界，console/page/request failure 和未处理 API 路径均为 0。截图：`/tmp/sprint89-data-asset-directory-1366x768.png`、`/tmp/sprint89-data-asset-directory-768x900.png`。该证据基于本机构建、系统 Chrome 和拦截数据，不替代部署环境、真实账号/资产或现场 Chrome 95 验收。

## IT-03 测试矩阵

| 变化 | 预期级别 | 预期门禁 |
|---|---|---|
| 新增 nullable 字段 | COMPATIBLE | 既有模型可继续，来源显示待确认 |
| 删除未被模型引用字段 | COMPATIBLE | 既有模型可继续，新导入不可选 |
| 删除已引用字段 | BREAKING | compile/publish/materialize 阻断 |
| varchar 扩宽 | COMPATIBLE | 既有模型可继续 |
| varchar 缩窄/类型族变化 | BREAKING 或 REVIEW_REQUIRED | 默认禁止发布 |
| nullable true → false | BREAKING | 默认禁止发布 |
| 仅 comment/tags/owner 变化 | 不改变 schema version | 不触发结构门禁 |
| 疑似 rename（remove+add） | REVIEW_REQUIRED | 人工确认前禁止发布 |

## IT-06 浏览器步骤

1. 使用授权账号从真实菜单进入「数据建模 > 数仓规划」。
2. 选择现有 plan，进入 `view=baseline&tab=sources`，不得直接 URL 代替菜单点击。
3. 验证 CURRENT、COMPATIBLE、BREAKING/MISSING、UNKNOWN 四态文案与可用动作。
4. 查看 diff；确认未展示无权限来源的名称或字段。
5. 对 compatible 来源重确认；刷新后 confirmedVersion 与 currentVersion 一致。
6. 对 breaking 来源先验证按钮被门禁，再修复映射并重确认。
7. 进入反向建模、编译、发布/物化，核对同一 binding/version 证据。
8. Chrome 95 检查布局、抽屉/弹窗、console 和 network；截图与脱敏请求证据写入本目录。

## 证据要求

- 后端：测试类、用例数、通过结果、关键 SQL/状态断言；不得只写“测试通过”。
- API：脱敏的 request path、status、reason code、tenant/permission 边界；不得记录 token。
- UI：真实菜单点击路径、四态截图、console/network 错误摘要、Chrome 版本。
- 数据：前后 ID/fingerprint/status 对照，只记录技术标识或脱敏值。
- 发布：镜像 digest、健康检查、回滚锚点和 rehearsal 结果。

## G4 判定

IT-01～08 按适用范围全部有真实证据、B01～B04 关闭、集中构建与 Chrome 95 通过后，Sprint 才能从 IN_PROGRESS 进入 DONE/DELIVERED。任何未运行项不得以源码检查替代真实验收。
