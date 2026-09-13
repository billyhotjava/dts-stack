# JDBC / 文件 / API 接入能力矩阵

**Sprint**: Sprint-31
**Feature**: F1/T03
**状态**: DONE

## 能力矩阵

| 接入类型 | 当前验收等级 | 可进入黄金链路 | 说明 |
|---|---|---|---|
| JDBC | P0 主线 | 是 | 已具备数据源登记、schema discover、ODS 预检、dbt source/model 和 Catalog 资产链路 |
| 文件 CSV/Excel | P1 受限 | 部分 | 可上传和解析，但大文件性能、字段类型推断、断点恢复和质量门禁仍需 F2/F7 补齐 |
| API/JSON | P1 受限 | 部分 | 可形成同步任务草案，但分页、认证 provider、schema drift 和 ODS 契约仍需 F2 补齐 |

## 页面表达要求

- Connector 页面必须清楚标识“正式支持 / 预览能力 / 需配置后可用”。
- 文件/API 不能用和 JDBC 相同的验收口径承诺企业级生产能力。
- 所有接入类型最终都必须产生 platform asset contract，才能进入指标/BI 消费。

## 后续依赖

- Sprint-31 F2 负责补 Connector Center 的产品化边界。
- Sprint-31 F7 负责文件/API 性能准入和超时边界。
