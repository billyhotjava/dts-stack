# Sprint-34 Loop 5 Evidence: 审计展示与支撑资源降噪补强

## Scope

- 修复审计中心“模块名称”列误用 `sourceSystemText` 的展示问题，避免 platform 记录统一显示成“业务管理”。
- 修复 raw actionCode 摘要覆盖 DB catalog 中文动作名的问题，避免 `CATALOG_DOMAIN_TREE` 等英文码直接出现在“操作内容”。
- 将大屏字体和图片 GET 资源纳入 platform fallback 支撑查询降噪，避免查看大屏时误记“查看screen-fonts”。

## Root Cause

- 前端表格列 `模块名称` 取的是 `sourceSystemText`，该字段表达来源系统，不是业务模块。
- `AuditV2Service` 与 `AuditEntryViewMapper` 之前优先展示请求 summary；当 summary 是原始 actionCode 时，会覆盖 DB catalog 中已治理的中文 operationName。
- `AuditLoggingFilter` 的 supplementary GET 规则未覆盖 `/api/infra/screen-fonts` 与 `/api/infra/screen-images`，大屏查看过程中的资源加载被当成人工查看动作。

## Verification

| Command | Result | Note |
|---------|--------|------|
| `./mvnw -q -pl dts-platform -Dtest=AuditLoggingFilterTest test` | PASS | 覆盖 screen-fonts/screen-images GET 不再记录 fallback audit |
| `./mvnw -q -pl dts-admin -Dtest=AuditEntryViewMapperTest,AuditV2ServiceTest test` | PASS | 覆盖 raw actionCode 展示/入库摘要优先使用 DB catalog 中文动作名 |
| `pnpm exec vitest run src/admin/views/audit-center.source-contract.test.ts` | PASS | 覆盖审计中心模块列取 `module` 而非 `sourceSystemText` |
| `pnpm build` | PASS | dts-admin-webapp 生产构建通过；仅存在既有 Vite chunk 与 Browserslist 警告 |
| `git diff --check` | PASS | 无空白错误 |

## Notes

- 本轮只排除大屏字体/图片的 GET 支撑资源；上传字体/图片仍保留为人工写操作候选。
- 本地 `target/generated-sources` 曾残留 root 生成的元模型目录，已挪开生成产物后重跑验证；未修改业务源码。
