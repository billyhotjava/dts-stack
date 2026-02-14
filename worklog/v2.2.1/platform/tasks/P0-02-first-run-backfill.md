# P0-02 首轮实测回填（2~4h）

## 状态
- `done-first-pass`

## 范围
- 用 P0-01 脚本执行采集，回填模板并输出首轮报告。

## 依赖
- `P0-01` 完成。

## 交付物
- `platform/first-run-report.md`
- 更新 `platform/stability-24h.md`（首轮数据区）
- `platform/scripts/backfill-first-run.sh`

## 本轮进展
- 已新增自动回填脚本：`platform/scripts/backfill-first-run.sh`。
- 已执行：
  - `bash worklog/v2.2.1/platform/scripts/backfill-first-run.sh --hours 168 --mode normal --arch x86_64 --tz Asia/Shanghai`
- 已产出非空样本：总任务 3、成功 2、失败 1、成功率 66.67%。
- 已自动追加回填记录到：`platform/stability-24h.md`。

## 验收标准
- 报告中有：成功率、P95、404 次数、Top 失败分类。
- `stability-24h.md` 有可追溯的回填条目与 raw 文件路径。

## 回滚点
- 删除 `platform/first-run-report.md` 与对应 `platform/raw/*` 采样文件。
