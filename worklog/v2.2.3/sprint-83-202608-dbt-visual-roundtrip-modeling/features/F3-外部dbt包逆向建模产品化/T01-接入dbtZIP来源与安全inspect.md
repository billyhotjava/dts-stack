# T01：接入 dbt ZIP 来源模式与安全 inspect

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F0/T02、F1/T02

## 目标

在逆向向导中明确选择“dbt 项目包”，上传 ZIP 并调用现有 archive inspect，展示真实项目信息和安全/兼容问题。

## Contract-first

- **UI**：来源类型=`DATABASE|DBT_ARCHIVE`；本 Task 只实现 DBT_ARCHIVE。
- **输入**：multipart `archive`；不得附带 profiles/credentials。
- **输出**：现有 `ModelPackageJson`，包括 package/project checksum、models/sources/technicalNodes/issues。
- **错误路径**：超限 413、恶意/无效 ZIP 400、不支持结构 422、越权 403；临时文件必须清理。
- **复用**：`inspectDbtModelArchive`、SafeZipExtractor、DbtModelArchiveInspectService。

## 验证

- [ ] FX-01～03 成功/阻断符合契约；FX-05 全部拒绝且无残留。
- [ ] 前端不再从 `DISCOVERED_MODELS` 产生 dbt 结果。

## Definition of Done

- [ ] inspect 无模型 SQL 执行、无在线下载、无业务落库。
