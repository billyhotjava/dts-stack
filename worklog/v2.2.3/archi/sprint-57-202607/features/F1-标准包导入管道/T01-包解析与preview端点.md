# T01: 包解析与 preview 端点

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

实现 `POST /api/modeling/standard-packages/import/preview`：接收 multipart zip，解析 5 个 CSV，做跨文件引用校验，落 run 记录（PENDING），返回校验报告。

## 技术设计

**新增文件**：
- `service/modeling/StandardPackageImportService.java` — 核心服务：解压（限制条目数/单文件大小，防 zip bomb）、按文件名路由到解析器、跨文件校验、组装报告
- `service/modeling/StandardPackageParseResult.java` — 五组行记录 + 错误集合的载体
- `web/rest/StandardPackageResource.java` — `@RequestMapping("/api/modeling/standard-packages")`
- `domain/modeling/StandardPackageImportRun.java` + repository + liquibase changelog `20260702_01_standard_package_import_run.xml` — run 表：id, package_name, source(UPLOAD/BUILTIN), status(PENDING/APPLIED/ROLLED_BACK/FAILED), summary_json, payload_json(暂存解析结果), created_by, created_date

**复用**：
- CSV 解析：`CsvUtils.parseCsvLine` / `stripBom`（MetadataStandardImportService 同款）
- 数据元行校验：抽取 `MetadataStandardImportService` 的 ALLOWED_TYPES / TYPE_WITH_SIZE / SecurityLevelCatalog 校验为可复用方法
- 码表校验语义对齐 `ReferenceCodeService.previewStructuredImport`

**校验规则**：见 sprint README「跨文件校验规则」。

**报告结构**（ApiResponse envelope）：
```json
{ "runId": "...", "files": [ { "file": "02-data-elements.csv",
  "toCreate": 12, "toUpdate": 3, "errors": [ { "row": 5, "field": "data_type", "message": "不支持的类型 XML" } ] } ],
  "blocking": false }
```

## 影响范围

dts-platform 后端新增，不改既有端点；liquibase 新表。

## 验证

- [ ] source-contract：新增测试锁定端点路径、5 文件名路由、报告字段
- [ ] 单测：合法包全绿；缺表头/引用悬空/类型非法/zip bomb 各返回对应错误
- [ ] curl 上传模板 zip（空数据）→ 返回 0 新增 0 错误

## 完成标准

- [ ] preview 幂等（不写业务表），run 记录 PENDING 持久化
- [ ] 校验报告含行号与中文原因
