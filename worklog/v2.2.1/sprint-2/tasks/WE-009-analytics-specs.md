# WE-009

## 标题

迁移 Analytics core/biz spec 与 mock 支撑。

## 范围

- `tests/web-e2e/specs/core/analytics-ai-query.spec.ts`
- `tests/web-e2e/specs/biz/analytics-publish-flow.spec.ts`
- `tests/web-e2e/support/analytics-app-mock.ts`

## 目标

- 恢复 Analytics AI 查询与发布业务流
- 让三端迁移范围完整闭环

## 交付

- Analytics core spec
- Analytics biz spec
- 对应 mock support

## 验收

- `python3 tests/run_suite.py --suite web-e2e-full --dry-run`
- `pnpm --dir tests/web-e2e exec playwright test specs/core/analytics-ai-query.spec.ts --list`

## 当前进度

- 已完成上述文件迁移

## 风险

- analytics 业务流对 screen designer、dashboard 等页面对象依赖更强，回归面更大
