# G0 交付基线 — Sprint-90

**状态**: GAP（规划期未启动运行实例，三项待验证）
**判定**: 依赖运行实例的 Feature（F2/T03、F3/T02、F5）在 B1-B3 关闭前保持 `DRAFT`/`BLOCKED_INPUT`。

## 基线项

| ID | 基线项 | 状态 | 说明 |
|----|--------|------|------|
| B1 | 可运行实例 + 登录 | GAP | 规划期本机未起 `dts-platform-webapp` dev server，后端 18082 未监听；治理员账号未取得 |
| B2 | 血缘样本数据 | GAP | 需要至少一条完整链路：外部源 → Addax 任务 → ODS 表 → dbt 模型 → 下游表，且含 ≥1 条字段血缘、≥1 条 `KNOWN_UNVERIFIED` 边 |
| B3 | Chrome 95 兼容验收环境 | GAP | 项目浏览器下限为 Chrome 95；G6 图谱页与新增抽屉需在该版本验证 |
| B4 | 迁移可在干净库执行 | PENDING | `20260811_01_catalog_lineage_verification_actor.xml` 编写后需 dry-run |
| B5 | 血缘写权限 | PENDING | 治理员角色是否含 `CATALOG_MAINTAINERS`（开放问题 Q4），无则需 dts-admin 侧补授权 |

## 验收路径（B1-B3 关闭后按此走查）

1. 登录 → 数据治理 > 数据地图与资产 > 血缘与影响分析 > 影响分析
2. 选择 B2 样本链路的下游数据集 → 节点数 ≥ 4、边数 ≥ 3
3. 切「字段血缘」→ 至少 1 条 `PARSED` 或 `INFERRED` 关系
4. 切「血缘图谱」→ G6 渲染无报错，导出 SVG 成功
5. 「血缘导入」→ 同步 Addax 血缘 → 返回结构化结果
6. 登记一条人工血缘 → 影响分析中可见 → 核验为 VERIFIED → 软失效 → 时间旅行仍可查到

## 阻塞与责任

| 阻塞项 | 需要谁提供 | 影响 Task |
|---|---|---|
| 授权账号 + 运行实例 | 环境负责人 | F2/T03、F3/T02、F5/T01、F5/T02 |
| 血缘样本链路 | 数据/ETL 负责人 | F5/T01 |
| Chrome 95 环境 | 测试负责人 | F5/T02 |
| 现网血缘规模统计（开放问题 Q1） | DBA | F4/T04、`assets/nfr-budget.md` |
