# T02: Compose 冒烟与样例数据

**优先级**: P1  
**状态**: READY  
**依赖**: F1, F2, F3, F4, F5

## 目标

用 compose 环境完成 OpenMetadata 端到端冒烟，留存可复现样例数据和证据。

## 范围

- OpenMetadata server API 冒烟。
- ingestion 容器或脚本运行。
- 数据库接入任务样例。
- OpenMetadata 中 service/table/pipeline/lineage 检查。
- 平台 catalog 查询结果检查。

## 完成标准

- [ ] 样例任务能产生 OpenMetadata 可见元数据。
- [ ] 平台能查询到对应元数据或展示合理回退。
- [ ] 失败和成功日志都留存证据。
- [ ] 冒烟步骤可重复执行。
