# WE-014

## 标题

完成 dry-run、构建与 Playwright 首轮执行验证。

## 范围

- `tests/run_suite.py`
- `tests/run_gates.sh`
- `tests/web-e2e/`
- 三端 WebApp 构建入口

## 目标

- 以真实命令确认 suite discoverability、三端构建与 Playwright 发现状态
- 尽可能完成首轮核心 suite 执行，并记录残余阻塞

## 交付

- dry-run 结果
- gate dry-run 结果
- 三端 build 结果
- Playwright list 结果
- 核心 suite 首轮执行结果

## 验收

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `python3 tests/run_suite.py --suite web-e2e-full --dry-run`
- `python3 tests/run_suite.py --suite biz-e2e --dry-run`
- `bash tests/run_gates.sh --gate pr --dry-run`
- `pnpm --dir tests/web-e2e exec playwright test --list`

## 当前进度

- 进行中：
  - dry-run 已恢复
  - 其余验证待执行

## 风险

- 当前分支与 `v2.5.0` 页面结构不完全一致，真实浏览器执行可能暴露额外适配缺口
