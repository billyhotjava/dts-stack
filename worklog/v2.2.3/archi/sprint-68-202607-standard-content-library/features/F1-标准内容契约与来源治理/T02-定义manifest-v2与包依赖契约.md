# T02：定义 manifest v2 与包依赖契约

**优先级**：P0  
**状态**：DONE
**依赖**：T01

## 目标

定义可独立发布和升级的包级元数据、依赖、版本、来源和完整性契约。

## 技术设计

- 定义 `schemaVersion/packageCode/packageVersion/category/industry/releasedAt/effectiveFrom`。
- 支持 `dependencies`、最低版本、`replaces`、弃用和版本单调性。
- 引用 `SOURCE-REGISTER.json`、许可证结论、签名和 SHA-256。
- 增加 schema 校验、依赖拓扑排序、循环/缺失/不兼容版本错误码。
- v1 classpath manifest 通过兼容适配读取，不让旧内置包立即失效。

## 影响范围

- 标准包 manifest JSON schema
- 标准包读取/预检服务
- 包目录 DTO/API
- 内容构建脚本与契约测试

## 验证

- [x] v1 兼容样例可读取。
- [x] v2 有效包通过 schema 和依赖校验。
- [x] 缺依赖、循环、版本回退、checksum 错误稳定失败。

## 完成标准

- [x] 包版本和依赖不再依赖目录名或人工安装顺序。
- [x] manifest 字段语义、错误码和兼容策略写入权威契约。

## 实现与证据

- 权威契约：[Manifest v2 契约](../../assets/manifest-v2-contract.md)。
- 后端实现：`StandardPackageManifestContract`、`StandardPackageImportService`、`StandardPackageBuiltinService`。
- 内置目录：4 个现有包已升级为 v2，使用真实 CSV SHA-256，`common-data-elements` 显式依赖 3 个公共码表包。
- 已安装版本从所有 `APPLIED` 记录读取；Sprint-57 历史记录按 legacy `1.0.0` 兼容。
- 定向回归：
  `./mvnw -Dtest=StandardPackageBuiltinServiceTest,StandardPackageImportServiceTest,StandardPackageManifestContractTest,StandardPackageApplyServiceTest test`
  —— 40 tests，0 failures，0 errors。
- Maven 仍报告仓库既有的 compiler plugin 重复声明、POI dependency convergence 和 JaCoCo 历史执行数据告警；均未导致本任务测试失败。
