# Sprint-92 聚焦自动化证据（2026-08-20）

**结论**：实现层聚焦自动化通过；Sprint 仍为 `E2E_PENDING`。  
**执行边界**：遵循“所有 Feature 完成后再做一次 E2E”的约定，本证据不包含真实浏览器操作、实时数据迁移、部署、容器重启或回滚演练。

## 1. 后端统一创作回归

在 `source/dts-platform` 执行聚焦 Maven 测试，覆盖：

- authoring snapshot/decoder/projection/draft/facade/resource；
- canonical ModelSpec 校验、CAS、幂等、强 ETag 和文件回传；
- `FULL/PARTIAL/NONE`、歧义列、unmanaged bundle 改写围栏；
- migration preview/apply/rollback 和 Liquibase/repository 契约；
- representation/capability、body-limit 与 security 负向路径。

**结果**：164 个用例通过，退出码 0；仅有 SLF4J multiple-provider 环境告警。

## 2. 既有生命周期无分支回归

聚焦回归 release candidate、model lifecycle、materialization plan/dispatch/start、publication/rollback commit 及 catalog serving。

**结果**：实际生成 9 个 Surefire 报告，共 89 个用例通过，退出码 0。`WarehousePlanDownstreamEvidenceAdapterTest` 源文件存在，但本次未生成对应编译/报告，因此不计入 9 类/89 用例。

## 3. 后端格式检查

`./mvnw -q -Dskip.frontend.build=true -Dskip.frontend.test=true spotless:check`

**结果**：通过，退出码 0。

## 4. 前端聚焦回归

在 `source/dts-platform-webapp` 执行 8 个 Sprint-92 相关测试文件：

- `dbtImplementationDraftApi.source-contract`；
- `Sprint91DualMode.source-contract`；
- `AdvancedDbtWorkspace.test`；
- `ModelingWorkbenchEditor.test`；
- `ModelVisualTransformationFields.test`；
- `modelWorkbenchService.test`；
- `modelingWorkbenchMode.test`；
- `workbenchEditorAccess.test`。

**结果**：8 文件/88 用例通过，退出码 0。

`prototypeReplacement.source-contract.test.ts` 的 Sprint-92 工作台编排用例单独通过（1 passed / 6 skipped）。全文件运行时 6 passed / 1 failed，唯一失败是并行修改中的 `MetricsPage.expressionSql` 断言，不属于 Sprint-92 变更范围，未通过修改该共享文件规避。

## 5. 类型、格式与生产构建

- `pnpm exec tsc --noEmit`：通过。
- Sprint-92 所属 14 个前端文件定向 Biome check：通过。
- `pnpm build`：通过，10,592 个模块转换完成，Vite 用时约 1 分 36 秒，退出码 0。

构建仅报告既有的 Browserslist 数据过旧、`StandardPackageActions` 同时静态/动态导入和大 chunk 告警，无编译错误。

## 6. 待集中验收

1. 刷新 G0 health/schema/login/Chrome95 与三类真实样本。
2. 按 `it/README.md` 一次性执行 IT-01～IT-11，失败只定向重跑断点。
3. 在受控副本上执行 migration dry-run/apply/rollback/re-apply。
4. 执行分阶段发布与回滚演练，再决定 Sprint `DONE`。
