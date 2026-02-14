# P0-01 自动采集脚本（小时级）

## 状态
- `done-first-pass`

## 范围
- 提供可执行脚本，按小时采集任务成功率、耗时、404 异常、失败分类。
- 输出原始数据到 `platform/raw`，并回填首轮报告。

## 依赖
- 本机可访问 Docker 与 Postgres（或配置外部连接参数）。

## 交付物
- `platform/scripts/collect-evidence.sh`
- `platform/raw/*.csv`
- `platform/raw/*.txt`
- `platform/first-run-report.md`

## 本轮进展
- 已支持参数：`--hours`、`--tz`、`--mode`、`--arch`、`--write-md`。
- 已兼容旧库（无 `failure_category` 字段时自动降级）。
- 已完成实跑并产出 raw + 报告。

## 验收标准
- 首次执行可产出小时维度统计，不因单个数据源不可达而整体失败。

## 回滚点
- 不写业务库，仅写 worklog 目录；删除 `platform/raw` 即可回滚。
