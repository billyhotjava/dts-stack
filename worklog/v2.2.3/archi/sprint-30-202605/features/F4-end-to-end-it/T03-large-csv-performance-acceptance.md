# T03: 大 CSV 性能验收

**优先级**: P0  
**状态**: READY  
**依赖**: F5

## 目标

在端到端验收中补充大 CSV 性能证据，避免只用小样例证明功能链路。

## 技术设计

验收至少覆盖：

- 100MB CSV 样例。
- 500MB 或 1,000,000 行 CSV 样例。
- DTS 上传/入湖、预检或异步质量校验、dbt run、snapshot export、metro-stack validate。

## 影响范围

- `worklog/v2.2.3/sprint-30-202605/it/evidence/large-csv/`

## 验证

- [ ] 记录文件大小、行数、hash。
- [ ] 记录每个阶段耗时和是否成功。
- [ ] 记录失败阈值和已知限制。

## 完成标准

- [ ] 大 CSV 验收失败时 Sprint-30 不得标记 DONE。
