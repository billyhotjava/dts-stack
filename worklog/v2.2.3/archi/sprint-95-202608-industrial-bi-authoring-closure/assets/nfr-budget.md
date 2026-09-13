# 非功能预算（Gate G1）

| 维度 | 预算 | 可执行适应度函数 | Task |
|---|---|---|---|
| 交互查询 | limit 默认 5000、最大 10000；自动预览防抖 ≥400ms；新请求取消旧请求 | validator/gateway test + fake timer/unit test | F1/T01 |
| 货架 | 维度/度量只能来自 pinned contract；重复拖入幂等 | reducer 参数化 test | F1/T01 |
| 计算 | derived ≤20、filter ≤50、order ≤10；非法函数/字段 4xx | 现有 validator + UI error test | F1/T02 |
| 图表真实性 | canonical 只展示真实 renderer；未知/历史 type 明确降级表格 | source-contract + renderer test | F1/T01 |
| 文件规模 | 修改后的 owner ≤800 行；新组件 ≤800 行 | `wc -l` 守卫 | F1/F2 |
| 看板查询 | 单看板最多 50 卡；并行请求上限 4；陈旧 generation 不写回 | helper unit test + E2E request count | F2/T01 |
| 联动 | 只向配置的目标卡发送筛选；来源卡不自筛；清除恢复全部 | hook unit test + Playwright | F2/T01 |
| 导出 | 仅已发布 Analysis；CSV/XLSX 最多受现有 10000 行分析预算约束；流式输出 | JUnit response/header/content test | F3/T01 |
| 权限/审计 | 未登录 401、无 EXPORT 403；密级拒绝 403/缺失 409；audit 非未分类 | filter/resource test + 运行 API | F3/T01/T02 |
| 兼容性 | 无新增依赖、无 `:has`/container query/dvh/structuredClone；legacy build 通过 | package diff + source scan + `pnpm build` | F3/T02 |
| 可访问性 | 拖拽之外提供按钮/选择器；发布/导出 disabled 有原因 | Playwright keyboard/disabled assertions | F1/F3 |

客户容量未知的延迟/并发结论保持“待校准”，不得由本地 25 个数据集外推。
