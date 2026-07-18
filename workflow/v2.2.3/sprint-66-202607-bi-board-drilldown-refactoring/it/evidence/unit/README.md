# 单元与组件测试证据

执行时间：2026-07-18。

| 检查 | 结果 | 覆盖 |
|---|---|---|
| Node `node --test` | `18/18` PASS | 通用/旧 Card 契约、五数据源、继承/上卷、Schema、设计器 source-contract |
| Vitest | `6 files / 41 tests` PASS | hook、防重复推进、动作、UI-01 至 UI-11 事件矩阵、drill-view |
| `pnpm exec tsc --noEmit` | PASS | TypeScript 合同 |
| Biome（8 个新增文件） | PASS | 新增运行时、测试和 Chrome 95 配置 |
| `git diff --check` | PASS | 空白符与补丁完整性 |

UI 事件矩阵验证 ECharts 只接受 `componentType=series` 且具有真实 `dataIndex/name/value/data` 的数据项；图例、坐标轴、geo roam、时间轴和空白 payload 均返回 `null`。查询失败恢复由 Chrome 95 自动化实跑。

仓库现有文件全量 Biome 仍有历史格式和 a11y 基线诊断；本 Sprint 未做大范围格式化，详见 [最终 review](../review/README.md)。
