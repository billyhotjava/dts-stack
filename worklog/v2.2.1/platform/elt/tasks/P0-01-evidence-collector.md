# P0-01 自动采集脚本（小时级）

## 状态
- `done-x86-verified`

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
- 最新有效样本：`20260214T120042Z`（168h 窗口，strict 模式），成功采集到非空数据（总任务 3、成功 2、失败 1）。

## 验收标准
- 首次执行可产出小时维度统计，不因单个数据源不可达而整体失败。

## 回滚点
- 不写业务库，仅写 worklog 目录；删除 `platform/raw` 即可回滚。
