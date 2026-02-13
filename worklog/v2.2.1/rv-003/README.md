# V221-RV-003 验证证据索引

## 目的
将 v2.2.1 的 P3 能力验证从“口头结论”转为可审计证据，支持现场（x86 / ARM，legacy / normal / dev）统一回填。

## 证据清单
- `worklog/v2.2.1/rv-003/stability-24h.md`
  - 24 小时稳定性压测与巡检记录。
- `worklog/v2.2.1/rv-003/addax-airbyte-semantic-compare.md`
  - Addax（当前）与 Airbyte（K8s 目标）在全量/增量语义的一致性对照。
- `worklog/v2.2.1/rv-003/isolation-lineage-regression.md`
  - 跨项目隔离与血缘影响回归结果。

## 使用方式
1. 先填写环境与版本信息。
2. 按模板逐项执行并记录“命令/输入/输出摘要/结论”。
3. 任何失败项必须给出“根因、修复、复测结果”。
