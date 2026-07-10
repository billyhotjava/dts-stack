# T04: API 数据源测试与运行配置一致性

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

让新建数据源测试、已保存数据源测试和实际 API 入湖执行读取同一份完整运行时配置，避免测试结果与保存后的行为不一致。

## 技术设计

- 扩展 `ApiSourceConfigNormalizer`，从 `api`、`readerConfig` 等兼容节点提升运行时策略和资源配置到引擎读取的顶层。
- 已保存数据源的连接测试复用同一归一化结果。
- 新建数据源测试提交完整 source config，而不是只提交局部 `readerConfig`。
- 保留既有鉴权 Ref、ODS 原始落地和资源归一化行为。

## 影响范围

- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/api/ApiSourceConfigNormalizer.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/ApiConnectorContractResource.java`
- `source/dts-platform-webapp/src/pages/foundation/DataSourceFormModal.tsx`
- API normalizer、连接测试和前端 source-contract 测试

## 验证

- [x] normalizer 测试覆盖嵌套 `path/resources/requestPolicy/rateLimit/tls` 提升。
- [x] 已保存数据源连接测试覆盖真实 API mock 和策略配置。
- [x] 新建表单 source-contract 覆盖完整 source config 传递。
- [x] ingestion/platform/frontend 聚焦测试通过。
- [x] 重建镜像后完成一次容器内 API 连接测试。

## 完成标准

- [x] 新建测试、已保存测试、实际 HTTP 执行对同一配置得到一致运行行为。
- [x] 连接失败能返回可操作的错误类别和建议。
- [x] 证据写入 Sprint-61 `it/README.md`。
