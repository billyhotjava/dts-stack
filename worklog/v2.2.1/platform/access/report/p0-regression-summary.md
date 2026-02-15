# P0 回归摘要（数据接入中心）

- 数据文件：
  - p0-access-metrics-20260214T150758Z.csv
- 生成时间（UTC）：20260214T150758Z
- 环境：normal / x86_64
- 采样窗口：近 24h

## 指标

| 指标 | 值 |
| --- | --- |
| 触发总数 | 0 |
| 触发成功 | 0 |
| 触发失败 | 0 |
| DAG 404 | 0 |
| TaskLog 404 | 0 |
| 结论 | OBSERVED |
| 备注 | init-pass |

## 判定规则

- PASS: 触发总数 > 0 且 DAG 404=0 且 TaskLog 404=0 且 触发失败=0。
- FAIL: DAG 404>0 或 TaskLog 404>0。
- OBSERVED: 其他情况（例如样本不足）。

## 采样来源

- Ingestion 日志：/opt/prod/s10/dts-stack/logs/dts-ingestion/app.log
