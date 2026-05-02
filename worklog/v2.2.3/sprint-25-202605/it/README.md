# Sprint-25 IT / Verification

## 目标

验证 Sprint-25 的信息架构收敛、页面拆分、血缘工作台收敛不破坏现有平台。

## 验证范围

- 菜单 JSON 可解析。
- 新根菜单 `语义与指标中心` 可见。
- 数据治理中心不再暴露指标主入口。
- 数据开发中心不再暴露业务指标配置主入口。
- 血缘入口命名为 `血缘与影响分析`。
- 语义指标各子路由可访问。
- 血缘各子任务页可访问。
- 旧兼容路由仍能打开。

## 验证脚本

已补齐以下本地 smoke 脚本:

- `it/scripts/menu-ia-smoke.sh`
- `it/scripts/semantic-metrics-smoke.sh`
- `it/scripts/lineage-workbench-smoke.sh`
- `it/scripts/ui-screenshot-smoke.sh`

## 本地执行

```bash
DTS_SMOKE_OUT=worklog/v2.2.3/sprint-25-202605/it/evidence/20260501-local/menu-ia \
  bash worklog/v2.2.3/sprint-25-202605/it/scripts/menu-ia-smoke.sh

DTS_SMOKE_OUT=worklog/v2.2.3/sprint-25-202605/it/evidence/20260501-local/semantic-metrics \
  bash worklog/v2.2.3/sprint-25-202605/it/scripts/semantic-metrics-smoke.sh

DTS_SMOKE_OUT=worklog/v2.2.3/sprint-25-202605/it/evidence/20260501-local/lineage-workbench \
  bash worklog/v2.2.3/sprint-25-202605/it/scripts/lineage-workbench-smoke.sh

DTS_WEBAPP_URL=http://127.0.0.1:3001 \
DTS_SMOKE_OUT=worklog/v2.2.3/sprint-25-202605/it/evidence/20260501-local/ui-screenshots \
  bash worklog/v2.2.3/sprint-25-202605/it/scripts/ui-screenshot-smoke.sh
```

截图脚本只拦截本地 `/api/**` 后端请求，返回空列表或成功状态，用于验证前端 IA 和页面标题，不写入业务 mock 数据。

## 证据

本轮证据:

- `it/evidence/20260501-local/menu-ia/`
- `it/evidence/20260501-local/semantic-metrics/`
- `it/evidence/20260501-local/lineage-workbench/`
- `it/evidence/20260501-local/ui-screenshots/`
