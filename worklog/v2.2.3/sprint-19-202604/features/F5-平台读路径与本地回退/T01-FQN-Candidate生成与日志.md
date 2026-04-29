# T01: FQN Candidate 生成与日志

**优先级**: P0  
**状态**: READY  
**依赖**: F1

## 目标

修复并测试 `dts-platform` 查询 OpenMetadata 时的 FQN candidate 生成。

## 范围

- 处理坏 pattern、空 pattern 和缺失占位符。
- 支持 schema 可选和多候选 FQN。
- 在 debug 或诊断日志中输出候选 FQN，不输出敏感信息。
- 覆盖 table/detail/lineage/quality 共用路径。

## 完成标准

- [ ] `{service` 这类坏 pattern 不会继续生成错误请求。
- [ ] FQN candidate 单测覆盖主要命名场景。
- [ ] 未命中时能看到尝试过哪些 candidate。
- [ ] 行为与 ingestion 写路径一致。
