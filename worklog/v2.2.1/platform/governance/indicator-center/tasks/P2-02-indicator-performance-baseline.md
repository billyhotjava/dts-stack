# P2-02 指标性能基线

`status`: `completed`  
`priority`: `P2`

## 目标

建立指标中心核心接口性能基线，明确容量边界与优化点。

## 范围

- 列表查询（含筛选）
- 预检/预览接口
- 版本与引用查询

## 子任务

1. 定义压测样本与阈值（p95/p99/non200）。
2. 运行基线脚本并产出报告。
3. 输出索引与缓存优化建议。

## 验收标准

- 输出可复用的性能基线报告。
- 关键瓶颈有明确优化建议。

## 当前完成

- 基线脚本已落地：
  - `worklog/v2.2.1/platform/governance/indicator-center/scripts/run-indicator-http-benchmark.sh`
- 脚本已支持两种认证：
  - 直接 `TOKEN`
  - `USERNAME/PASSWORD` 自动登录取 token
- 输出内容已定义：
  - `raw/p2-02-indicator-http-times-*.csv`
  - `raw/p2-02-indicator-http-summary-*.csv`
  - `report/p2-02-indicator-performance-latest.md`
- 已完成重建后复测（`opadmin`，`iterations=100`）：
  - `p95` 全部低于阈值（1500ms）。
  - `non200=0`，`indicators/ops/*` 路由可用。
  - 最新报告：`report/p2-02-indicator-performance-latest.md`。

## 风险与回滚

- 风险：测试数据规模与生产不一致。  
- 回滚：在预生产追加实测并修正阈值。
