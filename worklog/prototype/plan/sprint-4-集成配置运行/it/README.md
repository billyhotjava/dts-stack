# Sprint-4 集成测试（IT）

**状态**: READY
**范围**: 阶段② 集成 · 配置/运行/双视图 + dbt 隐藏生成映射的端到端可走通验证。
**前置**: S3 画布内核可用；`VITE_USE_MOCK=1`；构建走 legacy（chrome>=95）。

> 原型为纯前端 + mock，IT 以组件/交互级集成测试为主（Vitest + Testing Library 渲染 + 交互，service 走 mock fixtures）。运行/预览/日志/历史/dbt 映射均断言 mock 链路，不接真后端。

## IT 用例 → Feature/Task 覆盖矩阵

| IT | 场景 | 覆盖 |
|----|------|------|
| IT-01 | 选中画布节点 → 抽屉打开并显示对应类型表单；切换选中 → 内容切换；取消选中 → 关闭 | F1-T01 |
| IT-02 | 五种节点类型分别渲染正确配置表单，字段下拉来自上游 schema mock | F1-T02 |
| IT-03 | 编辑去重主键/策略、join 条件、聚合分组、过滤条件、字段映射 → 不可变写回，画布卡片同步 | F1-T02 |
| IT-04 | 表单必填校验 fail-fast（如 join 缺 ON 条件），错误信息友好 | F1-T02 |
| IT-05 | 点击"预览 20 行" → `transformService.previewNode` 返回 20 行，`CompactTable` 渲染（10 条/页，2 页） | F1-T03 |
| IT-06 | 预览列/行随节点 config 变化（去重/聚合后形状变化）；加载/空/错误态显式 | F1-T03 |
| IT-07 | dock 展开/收起 + 三 tab（▶运行/⏱调度/📜日志）切换 | F2-T01 |
| IT-08 | ▶运行 → mock 进度推进 + 状态点（运行中→成功/失败）；日志流式追加并滚底；失败路径显式错误 + 定时器清理 | F2-T01 |
| IT-09 | 运行历史 `CompactTable`（10 条/页，切条数回第 1 页）；列覆盖运行号/触发/状态/时间/耗时/行数/操作 | F2-T02 |
| IT-10 | 历史行"查看日志" → 跳 LogTab 载入对应运行日志；新运行完成后历史刷新 | F2-T02 / F2-T01 |
| IT-11 | `画布 | 列表` 切换即时生效，无数据丢失/复制；视图状态写入 URL，刷新保持 | F3-T01 |
| IT-12 | 列表视图 `CompactTable`（10 条/页）列收编现网 Transform/Orchestration；与画布同源 | F3-T02 |
| IT-13 | 列表行"打开画布" → 切回画布并定位对应作业；两视图编辑相互可见 | F3-T02 / F3-T01 |
| IT-14 | 给定样例作业，`generateDbtModels` 产自洽 `DbtModel[]`（name/sql/refs/sources/materialization/columns），引用关系与画布连线一致 | F4-T01 |
| IT-15 | `transformService.getGeneratedDbt(jobId)` 返回 `Result<DbtModel[]>`；纯函数同输入同输出（快照）；用户界面无 dbt 暴露 | F4-T01 |
| IT-16 | 端到端走通：进入阶段② → 画布搭"PLM 订单+ERP 客户→去重/连接→ODS 宽表" → 配置抽屉 → 预览 → 运行 → 看历史/日志 → 切列表 → 底层已生成 dbt 映射 | F1+F2+F3+F4（黄金主线 ② 段） |

## 约束断言（贯穿用例）

- [ ] **Chrome 95**：抽屉/dock/切换布局无 `oklch`/`:has()`/容器查询/subgrid；legacy 构建通过。
- [ ] **mock 契约**：所有 service 调用返回 `Promise<Result<T>>`（`transformService` / `orchestrationService`）；分页返回 `Page<T>` 信封；`VITE_USE_MOCK=1`。
- [ ] **不可变**：节点 config / 视图状态 / 运行状态均不可变更新，无就地 mutation。
- [ ] **dbt 隐藏**：除 `getGeneratedDbt` mock 出口外，无任何用户界面暴露 dbt。
- [ ] **分页统一**：所有 `CompactTable` 默认 10 条/页，切条数重置第 1 页，`pageNum` 基准显式一致。

## 退出标准

- [ ] IT-01 ~ IT-16 全部通过。
- [ ] 约束断言全部满足。
- [ ] 阶段② 黄金主线段在 `VITE_USE_MOCK=1` 下端到端可点可走（IT-16）。
- [ ] F4 产出的 `DbtModel[]` 契约就绪，S6 可直接消费。
