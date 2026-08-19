# F3：受控导出与集中验收

**优先级**：P0  **状态**：READY

## 目标与契约

已保存分析可在 EXPORT 权限、查询预算和密级封印下导出 CSV/XLSX，并完成本 Sprint 集中构建与页面验收。

| API | 响应 |
|---|---|
| `POST /bi/api/analysis/{id}/query/csv` | `text/csv;charset=utf-8` + attachment |
| `POST /bi/api/analysis/{id}/query/xlsx` | XLSX + attachment |

401/403/404/409/429/504 均不得生成伪文件；响应携带 `X-DTS-Classification` 与 snapshot。

## Task

| ID | Task | 状态 |
|---|---|---|
| T01 | Analysis 受控 CSV/XLSX 导出 | READY |
| T02 | 构建、部署与 Chrome 页面验收 | READY |
