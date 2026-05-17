# T03: webapp `pnpm tsc --noEmit`

**优先级**: P0
**状态**: READY
**依赖**: F1-F4

## 目标

对 dts-platform-webapp 做 TypeScript-only check，确保前端调用的 platform / metrics API 类型契约没有漂移。

## 背景

webapp 通过 OpenAPI / 手写 type 调用 platform internal API。Sprint-31A 与 Sprint-31B 改了 `PolicyResponse` shape、增加 `/v1/` endpoint、`PermissionDecision` 加 `reasonCode` 字段。前端类型如果没同步更新，运行时才会发现，应在 cheap stage 拦下。

## 技术设计

1. 命令：
   ```bash
   cd /opt/prod/s10/v2.2.3/source/dts-platform-webapp
   pnpm install --frozen-lockfile
   pnpm tsc --noEmit 2>&1 | tee /tmp/dts-platform-webapp-tsc.log
   ```
2. 若 OpenAPI 生成 type 与后端 contract 不一致：
   - 重新生成 type（`pnpm generate:api-types` 或等价命令）
   - 收集修改作为 T04 输入
3. evidence 落 `it/evidence/cheap-compile/dts-platform-webapp-{date}.md`。

## 影响范围

- 仅 typecheck；不打包、不启动 dev server。

## 验证

- [ ] exit code 0
- [ ] OpenAPI 同步任务（如有）执行成功

## 完成标准

- [ ] webapp typecheck 通过。
- [ ] evidence 文档存在。
