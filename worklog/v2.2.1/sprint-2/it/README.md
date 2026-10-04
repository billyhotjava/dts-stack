# Sprint-2 IT

## 目的

本目录记录 `Sprint-2` 的 Web E2E 集成验证入口、命令、工件位置与已知限制。当前目标不是覆盖全部真实后端，而是确认三端 Playwright 自动化在 `customer/2.2.1` 上可发现、可执行、可继续扩展。

## 验证入口

### Suite dry-run

```bash
python3 tests/run_suite.py --suite web-e2e-core --dry-run
python3 tests/run_suite.py --suite web-e2e-full --dry-run
python3 tests/run_suite.py --suite biz-e2e --dry-run
python3 tests/run_suite.py --suite web-e2e-quarantine --include-optional --dry-run
```

### Gate dry-run

```bash
bash tests/run_gates.sh --gate pr --dry-run
bash tests/run_gates.sh --gate nightly --dry-run
```

### 三端构建

```bash
pnpm -C source/dts-platform-webapp build
pnpm -C source/dts-admin-webapp build
pnpm -C source/dts-analytics-webapp/modern build
```

### Playwright 发现与执行

```bash
pnpm install --frozen-lockfile --dir tests/web-e2e
pnpm --dir tests/web-e2e exec playwright test --list
python3 tests/run_suite.py --suite web-e2e-core --fail-fast
python3 tests/run_suite.py --suite biz-e2e --fail-fast
python3 tests/run_suite.py --suite web-e2e-full --fail-fast
python3 tests/run_suite.py --suite web-e2e-quarantine --include-optional --fail-fast
```

## Suite 说明

- `web-e2e-core`
  - 5 条核心用例，覆盖 auth、platform、analytics、admin、selector contract
- `biz-e2e`
  - 5 条业务流用例，覆盖采集、建模、analytics 发布、RBAC/HITL、治理修复
- `web-e2e-full`
  - `core + biz` 的汇总回归入口
- `web-e2e-quarantine`
  - 隔离中的可选用例，默认不进入核心验收

## 工件位置

- `tests/reports/`
  - suite 级 summary、gate 级 summary、runner 产物
- `tests/web-e2e/reports/`
  - Playwright HTML report 与 artifacts

## 已知限制

- 当前 suite 仍以 mock-first 为主，不能等同于真实后端黑盒 E2E
- 如果本机缺少 Playwright browser binary，需要额外安装浏览器依赖
- 如果执行环境限制本地 `localhost` 端口绑定，需要放开本地监听权限，否则 auth mock server 无法启动
- 当前仓库存在与本 Sprint 无关的用户改动，验证时需避免误回滚
