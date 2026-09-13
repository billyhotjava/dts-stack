# T01: Stream/Mapping 提取器

**优先级**: P0
**状态**: DONE
**依赖**: F2

## 目标

从接入任务真实上下文提取 source/target stream，替换当前传入空列表导致血缘跳过的问题。

## 范围

- 读取 reader/writer config、tableMapping、schema snapshot 或 ODS mapping。
- 支持单表和多表任务。
- 处理文件接入场景的逻辑源资源名。
- 输出可用于 OpenMetadata lineage 的 source/target 表描述。

## 完成标准

- [ ] 数据库多表接入能生成多组 source/target stream。
- [ ] 空 mapping 时返回明确原因而不是静默成功。
- [ ] 文件接入暂不支持时有显式 unsupported 状态。
- [ ] 相关日志包含 task id 和 execution id。
