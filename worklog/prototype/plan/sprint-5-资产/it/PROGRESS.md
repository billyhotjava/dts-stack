# Sprint-5 资产 · 实现进度（原型）

**状态**: DONE（2026-06-23）

| Feature | 状态 | 说明 |
|---|---|---|
| F1 目录与搜索 | ✅ DONE | 资产目录(搜索 + CompactTable: 分层/类型/状态/行数/质量分/归口部门) + 数据集详情抽屉(概览/字段) + 数据产品 tab |
| F2 血缘 | ✅ DONE | 只读血缘 DAG(reactflow)：源/模型/资产/指标 配色；全链路 PLM.订单→stg→int←ERP.客户→ods_sales_wide→指标 |
| F3 质量与权属 | ✅ DONE | 部门级质量 tab(通过/预警/失败 统计 + 规则表) + 数据集详情内 质量/权属(归口部门 + 跨部门授权) |

## 部门为主模型落地
- 资产**归口部门**：数据集/数据产品/质量/授权均按 `departmentId` 过滤。
- 跨部门取数 → 权属 tab 的授权列表（如质量月报授权给销售处只读）。
- 草稿 vs 已发布：销售处 ods_sales_wide 为草稿（资产阶段仍待办，与部门 metrics 一致）；质量处 2 个已发布。

## 验证（Playwright + 构建）
| 项 | 结果 | 证据 |
|---|---|---|
| 资产目录 + 搜索 + 归口部门列 | PASS | `it/catalog.jpeg` |
| 数据集详情(概览/字段/血缘/质量/权属) | PASS | `it/lineage.jpeg` |
| 血缘 DAG 全链路渲染(source→model→dataset→metric) | PASS | 同上 |
| tsc + chrome95 构建 | PASS | 产物零 oklch/:has/容器查询/subgrid（含 reactflow） |

## 备注
- 血缘用已装 reactflow 渲染（避免再引 G6；现网用 G6，回植可替换）。
- 列级血缘/影响分析/diff/导入 为后续细化项；当前为表级血缘。
- MCP 浏览器截图表面中途卡死，关闭重开后恢复（与应用无关）。
