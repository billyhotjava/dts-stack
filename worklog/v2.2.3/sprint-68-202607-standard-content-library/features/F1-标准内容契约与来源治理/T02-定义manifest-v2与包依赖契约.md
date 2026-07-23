# T02：定义 manifest v2 与包依赖契约

**优先级**：P0  
**状态**：READY  
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

- [ ] v1 兼容样例可读取。
- [ ] v2 有效包通过 schema 和依赖校验。
- [ ] 缺依赖、循环、版本回退、checksum 错误稳定失败。

## 完成标准

- [ ] 包版本和依赖不再依赖目录名或人工安装顺序。
- [ ] manifest 字段语义、错误码和兼容策略写入权威契约。
