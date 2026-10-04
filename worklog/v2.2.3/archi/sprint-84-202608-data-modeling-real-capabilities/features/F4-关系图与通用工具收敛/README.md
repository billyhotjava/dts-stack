# F4：关系图与通用工具收敛

**优先级**：P1  
**状态**：CODE_COMPLETE（最终 Review、构建、部署与 E2E 待 F5）

## 目标

关系图只展示真实依赖并可下钻；工具页只保留有真实 owner 的流程入口和运行历史。

| Task | 状态 |
|---|---|
| T01 组合真实关系投影 | CODE_COMPLETE |
| T02 收敛真实工具入口与历史 | CODE_COMPLETE |
| T03 删除全局占位控件与完成七态 | CODE_COMPLETE |

## 约束

不建关系图库；不建万能工具执行/导入导出台账；无 owner 控件物理删除。

## 当前编码证据（2026-08-03）

- 关系图由 WarehousePlan 当前投影组合，只展示有真实关系证据的节点和边；不在浏览器猜测 Catalog lineage。
- 通用工具只保留 canonical owner 深链；演示运行历史、无 owner 导出和占位按钮已物理删除。
- 代码和契约测试已完成；当前 bundle 尚未完成最终 Review、构建、部署及真实浏览器 E2E。
