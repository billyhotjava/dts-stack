# T01: 当前容量评估与风险固化

**优先级**: P0  
**状态**: DONE  
**依赖**: 无

## 目标

明确 DTS 当前对 100MB-500MB / 百万行 CSV 的支撑边界，并把性能要求写入 Sprint-30。

## 评估结论

当前 DTS 对大 CSV 的支撑是“不完整支撑”：

- 文件上传链路存在 25MB、50MB/60MB、200MB 三套限制，不满足 500MB 文件。
- 文件预检链路存在 100,000 行硬上限，不满足几十万到百万行。
- 预检解析会全量读取 CSV 到内存，不适合作为百万行正式链路。
- 暂存表写入使用内存中的全量 `rows` 再分批 `batchUpdate`，百万行存在内存、事务和耗时风险。
- Addax CSV reader 已映射到 `txtfilereader`，但需要端到端压测证明它能承接大文件入湖，并和预检/质量/快照导出协同。

## 证据

- `source/dts-ingestion/src/main/resources/application.yml`：multipart `max-file-size=50MB`、`max-request-size=60MB`。
- `source/dts-platform-webapp/nginx/default.conf`：`client_max_body_size 25m`。
- `source/dts-platform/src/main/resources/config/application.yml`：`DATA_STANDARD_MAX_FILE_SIZE` 默认 `209715200`。
- `source/dts-ingestion/.../CsvParseService.java`：`MAX_ROWS=100_000`，并通过 `readRecords()` 构造全量 `List<List<String>>`。
- `source/dts-ingestion/.../StagingTableService.java`：`bulkInsert()` 接收全量 `List<List<String>> rows`。
- `source/dts-ingestion/.../AddaxJobService.java`：CSV source type 映射为 `txtfilereader`。

## 验证

- [x] 检查上传大小限制。
- [x] 检查 CSV 预检行数限制。
- [x] 检查预检内存模型。
- [x] 检查 Addax CSV reader 映射。

## 完成标准

- [x] 明确当前不能承诺 500MB/百万行完整链路。
- [x] 性能目标写入 Sprint-30。
