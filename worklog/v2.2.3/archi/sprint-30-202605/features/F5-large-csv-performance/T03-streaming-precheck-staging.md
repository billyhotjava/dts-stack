# T03: 流式 CSV 预检与暂存写入

**优先级**: P0  
**状态**: READY  
**依赖**: T02

## 目标

重构 CSV 预检链路，使 100MB-500MB / 百万行文件不会因为全量读入内存或 100,000 行上限而失败。

## 技术设计

核心方向：

- `CsvParseService` 拆分为 header/sample 解析和流式行迭代。
- 类型推断只抽样前 N 行，可配置，默认 10,000 行以内。
- `parse` 不再返回全量 `rows`；改为边读边写暂存表或使用 PostgreSQL `COPY`。
- 暂存表错误摘要只保存错误行和统计，不把所有错误 CSV 一次性构造到内存。
- 行数上限改为配置项，正式默认不低于 1,000,000。

## 影响范围

- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/CsvParseService.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/StagingTableService.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionPreCheckResource.java`
- 相关 DTO 和单测

## 验证

- [ ] 生成 100MB CSV，预检通过且 JVM 内存稳定。
- [ ] 生成 500MB 或 1,000,000 行 CSV，预检不触发 100,000 行限制。
- [ ] schema 推断结果稳定。
- [ ] 错误摘要分页返回，不一次性导出超大结果。

## 完成标准

- [ ] CSV 预检实现内存有界。
- [ ] 100MB-500MB 文件能进入暂存表或明确进入异步入湖路径。
