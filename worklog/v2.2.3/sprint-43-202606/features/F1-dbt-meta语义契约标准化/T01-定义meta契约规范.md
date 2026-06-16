# T01: 定义 meta 契约规范

**优先级**: P1
**状态**: READY
**依赖**: 无

## 目标
文档化 SP-2 的 dbt 语义富化 meta 契约，作为后续贯通的单一基准。

## 技术设计
- 列级 `meta.semantic_type`：枚举 `dimension | metric | time`（现有约定只用 dimension/metric，补 time）。
- 列级 `meta.standard_code`：绑定数据标准码（用于 DWD 标准码强制 + 标识符稳定）。
- model 级 `meta.grain`：`[col, ...]`（粒度键，用于 DWD 准入 + 建模 grain 同步）。
- 向后兼容：缺省即"无角色/无标准码"，不影响现有 dbt 编译与 OM 抓取。
- 落文档：`assets/` 或 `services/dts-dbt/README`，并与平台 catalog 契约字段命名对齐。

## 影响范围
- 文档（dbt 约定）；不改代码。

## 验证
- [ ] 契约枚举/字段命名与平台 `CatalogAssetColumnContract`（F2）拟加字段一致。

## 完成标准
- [ ] 规范文档完成、字段命名跨 dbt/catalog 对齐。
