# F6: 验收与回归

**优先级**: P0
**状态**: DONE
**目标**: 保证 Sprint-25 的 IA、菜单、页面拆分和血缘收敛不破坏现有功能。

## 验收项

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | 菜单 JSON 校验 |
| T02 | DONE | `source/dts-platform-webapp` 执行 `pnpm build` |
| T03 | DONE | 验证新菜单入口和旧兼容路由 |
| T04 | DONE | 验证语义指标中心各子页面可访问 |
| T05 | DONE | 验证血缘与影响分析各任务页可访问 |
| T06 | DONE | 截图留存四中心菜单和关键页面 |
| T07 | DONE | 检查未引入 mock/sample/demo/示例 数据 |

## 验证命令

```bash
jq empty source/dts-admin/src/main/resources/config/data/portal-menu-seed.json source/dts-admin/src/main/resources/config/data/role-menu-defaults.json
pnpm build
rg -n "mock|sample|demo|示例" <changed-files>
worklog/v2.2.3/sprint-25-202605/it/scripts/menu-ia-smoke.sh
worklog/v2.2.3/sprint-25-202605/it/scripts/semantic-metrics-smoke.sh
worklog/v2.2.3/sprint-25-202605/it/scripts/lineage-workbench-smoke.sh
DTS_WEBAPP_URL=http://127.0.0.1:3001 worklog/v2.2.3/sprint-25-202605/it/scripts/ui-screenshot-smoke.sh
```

## 验收证据目录

- `worklog/v2.2.3/sprint-25-202605/it/evidence/`
- `worklog/v2.2.3/sprint-25-202605/it/evidence/20260501-local/menu-ia/`
- `worklog/v2.2.3/sprint-25-202605/it/evidence/20260501-local/semantic-metrics/`
- `worklog/v2.2.3/sprint-25-202605/it/evidence/20260501-local/lineage-workbench/`
- `worklog/v2.2.3/sprint-25-202605/it/evidence/20260501-local/ui-screenshots/`
