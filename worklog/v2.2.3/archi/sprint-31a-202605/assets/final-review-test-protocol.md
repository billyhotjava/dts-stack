# 最终统一 Review / Test 协议

**Sprint**: Sprint-31A
**Feature**: F6/T05
**状态**: DONE

## 执行约束

用户已明确要求：Sprint-31A、Sprint-31、Sprint-32 中间阶段不做编译、镜像构建和容器重建，所有 review、测试、build 和部署验证在三段任务完成后统一执行。

## 中间阶段允许

- `git diff --check`
- `rg` / `sed` / `find` 静态检查
- GitNexus impact / detect changes
- 文档状态更新
- 小范围源码契约测试文件补充，但不运行完整测试

## 中间阶段禁止

- `mvn test`
- `npm run backend:unit:test`
- `pnpm build`
- `docker build`
- `builds/dts-build.sh`
- `docker compose up --force-recreate`
- 把未执行的测试写成已通过

## 最终统一验证清单

| 模块 | 命令 / 验证 | 目标 |
|---|---|---|
| dts-platform | `cd source/dts-platform && npm run backend:unit:test` | 资产事实源、权限、血缘、capability 测试通过 |
| dts-analytics | `cd source/dts-analytics && npm run backend:unit:test` | analytics 权限只读 fallback 和 platform check 通过 |
| dts-metrics | `cd source/dts-metrics && ./mvnw test` | metrics platform client / pack 校验通过 |
| platform-webapp | `cd source/dts-platform-webapp && pnpm build` | 资产门户、metrics 入口和 analytics 合并产物可构建 |
| 镜像 | `./builds/dts-build.sh --image dts-admin dts-admin-webapp dts-ingestion dts-platform dts-platform-webapp dts-analytics dts-metrics` | 目标镜像可构建 |
| 容器 | `docker compose -f docker-compose-app.yml up -d --force-recreate --no-deps ...` | 只重建受影响容器 |

## 最终接口 Smoke

```text
GET /api/capabilities
GET /api/internal/capabilities
GET /api/catalog/assets-v2
GET /api/catalog/assets-v2/{id}/contract
GET /api/catalog/assets-v2/{id}/schema-contract
GET /api/catalog/assets-v2/governance-gaps
GET /api/catalog/assets-v2/lineage-failures
GET /api/catalog/assets-v2/migration/dry-run
POST /api/internal/asset-permission/check
GET /metrics/semantic/metrics
```

## 风险记录

- 本阶段没有运行编译，最终可能暴露 Java 构造器注入、TypeScript 类型或测试 mock 细节问题。
- 缺密级默认拒绝是高影响行为，最终 IT 必须覆盖历史资产缺字段场景。
- `/api/semantic/**` 兼容代理仍属于 Sprint-32，不能在 Sprint-31A 宣称完成。
