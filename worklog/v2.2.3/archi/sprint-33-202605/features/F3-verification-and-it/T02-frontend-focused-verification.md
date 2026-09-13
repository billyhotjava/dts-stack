# T02: 前端 source-level test 与 build

**优先级**: P0
**状态**: DONE
**依赖**: F2

## 目标

验证角色编辑页重构后的 API 契约、成员表格行为和生产构建。

## 验证

- [x] `./node_modules/.bin/vitest run src/admin/views/role-detail.assignment-table.source-contract.test.ts` from `source/dts-admin-webapp`
- [x] `pnpm build` from `source/dts-admin-webapp`

## 完成标准

- [x] 命令输出和结论写入 `it/evidence/frontend-focused-20260519.md`。
