# T01: `mvn -pl dts-platform compile`

**优先级**: P0
**状态**: DONE
**依赖**: F1-F4

## 目标

执行 `mvn -pl dts-platform -am compile`（不跑测试、不打镜像），验证 Sprint-31A + Sprint-31B 累计的 dts-platform 改动跨模块编译通过。

## 背景

Sprint-31A 阶段做了 `CatalogAssetIdentityResolver` 7 参构造器变更、`AssetPermissionInternalResource` 新增 row-filter repository 依赖、`IndicatorService` / `ModelingSqlModelService` 注入 `CodeAssetGrantWriter`。Sprint-31B 进一步重构构造器注入、新增多个 service / repository / endpoint，调用方众多但从未编译验证。

## 技术设计

1. 命令：
   ```bash
   cd /opt/prod/s10/v2.2.3/source
   ./mvnw -pl dts-platform -am -DskipTests compile 2>&1 | tee /tmp/dts-platform-compile.log
   ```
2. 若出现 error：
   - 收集到 `it/evidence/cheap-compile/dts-platform-{date}.log`
   - 移交 F5/T04 修复
3. 成功后写一份 evidence `it/evidence/cheap-compile/dts-platform-{date}.md`：
   ```
   命令: ...
   退出码: 0
   耗时: ...
   warnings 数量: ...
   ```

## 影响范围

- 仅执行编译；不修改代码（除非走 T04）。
- evidence: `worklog/v2.2.3/sprint-31b-202605/it/evidence/cheap-compile/`

## 验证

- [x] exit code 0
- [x] 无 ERROR
- [x] WARNING 列表归档

## 完成标准

- [x] platform 编译通过。
- [x] evidence 文档存在：`worklog/v2.2.3/sprint-31b-202605/it/evidence/cheap-compile/dts-platform-2026-05-18.md`。
