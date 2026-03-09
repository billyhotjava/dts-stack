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
- `biz/full/quarantine` suite 收口执行结果

## 验收

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `python3 tests/run_suite.py --suite web-e2e-full --dry-run`
- `python3 tests/run_suite.py --suite biz-e2e --dry-run`
- `python3 tests/run_suite.py --suite web-e2e-quarantine --include-optional --dry-run`
- `bash tests/run_gates.sh --gate pr --dry-run`
- `pnpm --dir tests/web-e2e exec playwright test --list`

## 当前进度

- 已完成：
  - 四组 suite dry-run
  - 三端 build 校验
  - `pnpm --dir tests/web-e2e exec playwright test --list`
  - `python3 tests/run_suite.py --suite web-e2e-core --fail-fast`
  - `python3 tests/run_suite.py --suite biz-e2e --fail-fast`
  - `python3 tests/run_suite.py --suite web-e2e-full --fail-fast`
  - `python3 tests/run_suite.py --suite web-e2e-quarantine --include-optional --fail-fast`

## 风险

- 受限执行环境可能拦截本地 `localhost` 端口绑定；需要允许 Playwright 附带的本地 mock auth server 与前端 dev server 正常监听
