# T04: 现场冒烟 Runbook

**优先级**: P1  
**状态**: READY  
**依赖**: T01, T02, T03

## 目标

提供现场可执行的 OpenMetadata 采集冒烟步骤，覆盖容器、API、脚本、平台查询。

## 范围

- `docker compose ps` 检查。
- OpenMetadata `/api/v1/system/version` 或等价 API 检查。
- ingestion 脚本运行和日志检查。
- catalog 详情、血缘、质量页面验证。
- 失败排查表。

## 完成标准

- [ ] runbook 不依赖开发者口头说明。
- [ ] 每一步有预期结果和异常处理。
- [ ] 证据文件位置写入 `it/README.md`。
- [ ] 可被现场运维重复执行。
