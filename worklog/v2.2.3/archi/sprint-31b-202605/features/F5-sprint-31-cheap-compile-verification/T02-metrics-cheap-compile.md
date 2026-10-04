# T02: `mvn -pl dts-metrics compile`

**优先级**: P0
**状态**: DONE
**依赖**: F1-F4

## 目标

执行 `mvn -pl dts-metrics -am compile`，验证 `PlatformContractClient` 新增 `RlsPolicyResult` / `MaskedColumn` / publish 路径调用、`MetricArtifactGenerationService` 与 `MetricSqlGenerator` 调用契约一致。

## 背景

dts-metrics 通过 internal HTTP 契约依赖 dts-platform，contract 漂移不会编译报错，但内部 service 之间的方法签名变化（`dbtModelSql` 新增 `RlsPolicyResult` 参数、`MetricSqlGenerator` 拆出等）会立刻打破调用方。

## 技术设计

1. 命令：
   ```bash
   cd /opt/prod/s10/v2.2.3/source
   ./mvnw -pl dts-metrics -am -DskipTests compile 2>&1 | tee /tmp/dts-metrics-compile.log
   ```
2. 同 T01 处理 error / warning。
3. evidence 落 `it/evidence/cheap-compile/dts-metrics-{date}.md`。

## 影响范围

- 仅编译。

## 验证

- [x] exit code 0
- [x] 与 platform 共享 contract 字段无 type mismatch

## 完成标准

- [x] metrics 编译通过。
- [x] evidence 文档存在：`worklog/v2.2.3/sprint-31b-202605/it/evidence/cheap-compile/dts-metrics-2026-05-18.md`。
