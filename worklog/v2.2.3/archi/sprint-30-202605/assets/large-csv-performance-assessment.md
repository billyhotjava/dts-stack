# Large CSV Performance Assessment

**日期**: 2026-05-14  
**结论**: 当前 DTS 不能直接承诺稳定支撑 100MB-500MB / 百万行 CSV 的完整预检入湖链路，需要在 Sprint-30 增加 P0 性能准入工作。

## 目标规模

- 常见文件大小：100MB-500MB。
- 常见行数：几十万到 1,000,000 行。
- 正式版验收：至少 100MB 样例通过；正式交付前 500MB 或 1,000,000 行样例通过。

## 当前瓶颈

| 环节 | 当前状态 | 风险 |
|---|---|---|
| Web Nginx | `client_max_body_size 25m` | 100MB 文件会被网关拦截 |
| ingestion multipart | `max-file-size 50MB` / `max-request-size 60MB` | 100MB-500MB 文件无法上传 |
| platform 附件限制 | 默认 200MB | 与 ingestion / Nginx 不一致 |
| CSV 预检 | `MAX_ROWS=100_000` | 几十万到百万行会被拒绝 |
| CSV 解析 | `readRecords()` 全量读入 `List<List<String>>` | 大文件 JVM 内存风险 |
| 暂存写入 | `bulkInsert(..., List<List<String>> rows)` | 依赖全量内存，百万行耗时和事务风险 |
| Addax CSV 入湖 | `csv` 映射 `txtfilereader` | 有批处理潜力，但缺大文件实测 |

## 产品判断

- “上传保存文件”这一步目前也不满足 100MB-500MB，因为网关和 ingestion 限制太低。
- “预检修复”这一步明确不支持百万行，因为有 100,000 行上限和全量内存模型。
- “批量入湖”理论上应走 Addax `txtfilereader`，但需要压测证明，不应在没有证据时对客户承诺。
- “训练快照导出”必须坚持流式写 `data.csv`，不能 `queryForList` 后一次性写。

## Sprint-30 决策

新增 F5 `大 CSV 性能准入`，作为正式版本准入条件。F5 完成前，Sprint-30 只能说明“功能链路可运行”，不能说明“客户现场规模可稳定运行”。
