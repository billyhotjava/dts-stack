# 领域画像（Gate G0）

**勘察日期**：2026-08-20  **来源**：本地运行库、现网 UI 用词、Sprint-94 固定契约  
**结论**：可据此实现；客户容量与 Chrome 95 仍需目标环境校准。

## 统一语言

| 术语 | 定义 | 禁用/不等于 |
|---|---|---|
| 字段货架 | 横轴、纵轴及视觉属性承载的治理维度/度量 | raw SQL、任意物理列 |
| 分析 | 一份钉定 QueryDataset version/checksum 的 `AnalysisQuerySpec` | 看板、旧 Question |
| 派生指标 | 只引用已批准字段和函数白名单的计算字段 | 任意表达式、脚本 |
| 联动 | 一个分析组件的选择作为指定目标组件的筛选条件 | 全局无差别拼接 SQL |
| 发布 | 校验后生成不可变 revision 与受众快照 | 保存草稿 |
| 导出 | 以 EXPORT 权限重新执行已保存分析并生成受密级封印的 CSV/XLSX | 浏览器直接导出未授权预览数据 |

## 业务不变量

1. 只有 PUBLISHED QueryDataset 契约可创建分析，checksum 不一致 fail-closed。
2. UI 不接收 raw SQL/MBQL；计算只能通过 `AnalysisQuerySpecValidator`。
3. 保存、发布、消费和导出均复用同一 Analysis ID/spec，不产生影子卡片。
4. 发布钉定数据集与分析 revision；看板只加入 PUBLISHED analysis revision。
5. 导出必须单独具有 `export` 权限并保留 actor、asset、queryId、outcome、密级封印。
6. classification 与业务标签分离。

## 真实数据画像

| 指标 | 实测值 | 命令/来源 |
|---|---:|---|
| QueryDataset asset | 25 | dts_platform 只读 SQL |
| PUBLISHED QueryDataset version | 25 | dts_platform 只读 SQL |
| Analytics Card | 3 | dts_analytics 只读 SQL |
| governed analysis | 2（1 PUBLISHED、1 DRAFT） | dts_analytics 只读 SQL |
| 非归档 dashboard | 2 | dts_analytics 只读 SQL |
| dashboard card binding | 2 | dts_analytics 只读 SQL |

以上是本地功能样本，不用于承诺客户 P95、并发或最大数据量。设计继续使用 Sprint-94 已有 10,000 行交互上限、每用户/部门/全局查询预算。

## 外部边界与合规

| 边界 | 我方契约 | 失败降级 |
|---|---|---|
| Platform QueryDataset | published version + immutable checksum | 409/502，禁止切 latest |
| Platform Permission | CARD READ/EDIT/EXPORT | 401/403，按钮不可假成功 |
| Classification export seal | effectiveLevel + snapshotId | 403/409，禁止生成文件 |
| Chrome 95 | 目标现场浏览器 | 无 executable 时只记录 GAP |
