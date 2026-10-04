# Sprint-4 集成测试说明

本 Sprint 的验证目标不是“所有平台能力一次性做深”，而是确认占位页已被真实页面/真实接口替代，并且可以进入现有 Playwright 回归体系。

## 验证层次

### 1. 后端单测 / 集成测试

按工作流分别验证：

- Visualization 聚合结果不再是硬编码 demo payload
- IAM 分类同步具备开始、状态、失败、重试闭环
- Scheduler 控制台接口可以返回 overview / runs / actions
- API tryInvoke 走真实代理，而不是 sample schema
- Workbench 返回真实趋势序列

建议命令：

```bash
cd source/dts-platform
./mvnw test
```

### 2. 前端编译验证

```bash
pnpm -C source/dts-platform-webapp build
```

### 3. Web 自动化验证

本 Sprint 完成后，应在既有 `tests/web-e2e` 基线中补这些场景：

- workbench homepage
- visualization entry / reports open
- scheduler console
- API service test invoke
- IAM sync console

建议命令：

```bash
python3 tests/run_suite.py --suite web-e2e-core --fail-fast
python3 tests/run_suite.py --suite biz-e2e --fail-fast
```

### 4. 页面信息架构验证

需要确认：

- `AnalyticsPage` 不再只是 redirect shell
- `ReportsManagePage` 与 `ReportsPage` 边界清晰
- `QualityReportPage` 命名与实际页面语义一致

## 失败工件

前端和 Playwright 改动都应确保可以产出：

- 页面错误提示
- 后端审计/异常日志
- Playwright screenshot / trace / video

## 完成标准

- 本 Sprint README 中 11 个 task 全部有明确交付
- platform 关键占位页已转为真实功能页
- 新能力都能进入现有 Playwright 回归体系
