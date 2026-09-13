# Sprint-52 集成测试计划

**状态**: DONE（验收通过）
**执行时间**: 2026-06-29 12:53 UTC

## 验证项

| 项目 | 命令 | 预期结果 | 实际结果 |
|------|------|---------|---------|
| TypeScript | `pnpm exec tsc --noEmit` | 0 errors | PASS — 0 errors (exit 0) |
| source-contract (metricWorkbench) | `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts` | pass≥5, fail=0 | PASS — 5/5 pass |
| source-contract (dataDevelopmentWorkbench) | `node --test src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts` | pass≥5, fail=0 | PASS — 5/5 pass (10/10 total) |
| 生产构建 | `pnpm build` | built in Xs | PASS — built in 2m 6s |
| Chrome 95 关键字 | `grep -rn "oklch\|:has(\|@container" src/pages/modeling/metric-workbench/ ...` | 0 匹配 | PASS — 0 matches |
| 壳替换验证 | `grep -l "window.location.replace\|SemanticModelingCenterPage" src/pages/modeling/Semantic*.tsx` | 0 文件 | PASS — 0 files matched |
| 路由访问 | 浏览器访问 `/modeling/metric-workbench` | 三栏画布渲染 | 待人工验证 |

## IT 证据区

```
执行时间: 2026-06-29 12:53 UTC
工作目录: /opt/prod/s10/v2.2.3/source/dts-platform-webapp/

[1] TypeScript check
  命令: pnpm exec tsc --noEmit 2>&1 | tail -10
  输出: (无输出)
  结论: EXIT 0 — 0 errors ✔

[2] Chrome 95 关键字扫描
  命令: grep -rn "oklch\|:has(\|@container" src/pages/modeling/metric-workbench/ src/pages/modeling/Semantic*.tsx src/pages/modeling/MetricWorkbenchPage.tsx 2>/dev/null | wc -l
  输出: 0
  结论: 0 violations ✔

[3] 壳替换验证（redirect shell check）
  命令: grep -l "window.location.replace\|SemanticModelingCenterPage" src/pages/modeling/Semantic*.tsx 2>/dev/null
  输出: (无输出)
  结论: 0 files matched ✔

[4] source-contract 测试
  metricWorkbench.source-contract.test.ts:
    ✔ MetricWorkbenchPage uses React Flow canvas and semantic API
    ✔ MetricCanvas uses @xyflow/react with custom nodes and edges
    ✔ all SemanticXxxPages are real implementations (no redirect shells)
    ✔ publish page calls dbt + BI registration + lineage in sequence
    ✔ runs page polls RUNNING status
    pass 5 / fail 0 / duration_ms 96.66

  dataDevelopmentWorkbench.source-contract.test.ts:
    ✔ dbt file browser is a file evidence surface and hands publishing back to SQL modeling
    ✔ metric workbench route is registered in static routes and dynamic resolver
    pass 5 / fail 0 / duration_ms 87.24

  合计: 10/10 pass ✔

[5] 生产构建
  命令: pnpm build 2>&1 | tail -15
  输出 (末尾):
    dist/assets/configureMonaco-B0a_rX4Y.js  3,312.60 kB │ gzip: 847.88 kB
    (!) Some chunks are larger than 1500 kB after minification.
    ✓ built in 2m 6s
  结论: 构建成功 ✔
  备注: configureMonaco chunk 3.3MB（Monaco Editor 预期大包，已有代码分割警告，非阻断）
```

## 遗留项 / 关注点

- `configureMonaco` chunk 3.3 MB（gzip 847 KB）超出 1500 kB 警告阈值。Monaco Editor 本身体积较大，属已知情况；建议后续 sprint 评估动态 import 分割。
- 浏览器路由访问（`/modeling/metric-workbench` 三栏画布渲染）需人工验证，未纳入自动化检查。
