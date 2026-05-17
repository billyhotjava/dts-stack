# Sprint-31A 集成测试计划

## 测试策略

按用户约束，Sprint-31A、Sprint-31、Sprint-32 实现过程中不做完整中间编译、镜像构建和容器重建。所有完整测试、构建和容器重建统一放到三段任务完成后的最终 review/test 阶段。

例外：针对架构评审追补项 RX 的运行时 enforcement，允许执行 focused contract/unit tests，用于证明代码级 guardrail 已生效；该类证据不等同于最终全量 build / Docker / live IT 通过。

本文件先定义最终验收清单和证据目录。

## 最终验收链路

```text
数据源
  -> ODS / dbt model
  -> Catalog asset identity
  -> governance status
  -> lineage
  -> asset_grant permission check
  -> platform asset API
  -> dts-metrics read-only asset contract
  -> BI / analytics consumption
```

## Evidence 目录

| 场景 | 证据目录 | 状态 |
|---|---|---|
| 资产身份和生命周期 | `it/evidence/asset-identity/` | READY |
| 治理字段和缺口识别 | `it/evidence/governance-contract/` | READY |
| 血缘统一写入 | `it/evidence/lineage-provenance/` | READY |
| 权限和密级一致性 | `it/evidence/permission-classification/` | READY |
| 资产门户体验 | `it/evidence/asset-portal/` | READY |
| 兼容和迁移 dry-run | `it/evidence/migration-compatibility/` | READY |
| 最终统一 build/test | `it/evidence/final-review-test/` | RX_FOCUSED_TESTS_RECORDED |

## 最终测试命令占位

完成 Sprint-31A、Sprint-31、Sprint-32 后统一执行：

```text
# 后端 focused tests
cd source/dts-platform && npm run backend:unit:test
cd source/dts-metrics && ./mvnw test

# 前端 build/type check
cd source/dts-platform-webapp && pnpm build

# 镜像构建
./builds/dts-build.sh --image dts-admin dts-admin-webapp dts-ingestion dts-platform dts-platform-webapp dts-analytics dts-metrics

# 容器重建
docker compose -f docker-compose-app.yml up -d --force-recreate --no-deps dts-platform dts-platform-webapp dts-analytics dts-metrics
```

## 当前阶段禁止项

- 不在每个任务后运行完整 `mvn test` / `pnpm build` / `docker build`。
- 不做中间容器重建。
- 不把 smoke 结果伪造为已通过。
- 不把文档 READY 误标为代码 DONE。
