# P3-01 指标中心 E2E 回归门禁

`status`: `completed`  
`priority`: `P3`

## 目标

将指标中心主链路纳入自动化门禁，保障发布稳定性。

## 范围

- 指标 CRUD
- 校验/预检/发布流程
- 版本与引用回归

## 子任务

1. 编排 E2E 脚本（API-first，可被页面任务复用）。
2. 输出 latest 报告到 worklog。
3. 接入发布门禁（strict 模式）。

## 验收标准

- 一键执行可得到 PASS/FAIL 结论。
- 失败项可定位到接口和阶段。

## 当前完成

- 已落地脚本：`worklog/v2.2.1/platform/governance/indicator-center/scripts/run-indicator-e2e-gate.sh`
- 脚本已支持两种认证：
  - 直接 `TOKEN`
  - `USERNAME/PASSWORD` 自动登录取 token
- 已覆盖链路：
  - 指标列表
  - 指标创建/更新/删除
  - 版本与引用查询
  - 校验/计算预览/发布预检
- 已输出产物：
  - `raw/p3-01-indicator-e2e-*.csv`
  - `raw/p3-01-indicator-e2e-summary-*.txt`
  - `report/p3-01-indicator-e2e-latest.md`
- 删除链路修复：
  - 将版本/引用清理改为显式 `@Modifying + @Query` 删除，并在删除主记录前 `flush`。
- `--strict` 实测结果（2026-02-23）：
  - `overall=PASS`
  - `required_failed=0`
  - 最新报告：`report/p3-01-indicator-e2e-latest.md`

## 风险与回滚

- 风险：环境依赖（token/DB）导致误报。  
- 回滚：拆分为 smoke + strict 两套策略。
