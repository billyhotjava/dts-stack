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
