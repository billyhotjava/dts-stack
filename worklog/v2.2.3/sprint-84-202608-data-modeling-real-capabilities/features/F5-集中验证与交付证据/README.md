# F5：集中验证与交付证据

**优先级**：P0  
**状态**：IN_PROGRESS（T01 已完成，待部署与 T02 联合 E2E）

| Task | 状态 |
|---|---|
| T01 聚焦测试、模块构建与独立 Review | DONE |
| T02 Sprint-83/84 联合 E2E 与证据收口 | READY（待部署后执行） |

F1～F4 与 Sprint-83 F2～F5 已完成代码冻结；只有部署目标镜像后才进入 T02，不以 mock、组件测试或构建结果替代真实 E2E。

## T01 冻结证据

- 前端 Vitest：30 个测试文件、122 个测试通过；Node 契约测试：36/36 通过。
- 后端聚焦测试：87/87 通过，其中真实 PostgreSQL 严格审计回滚 4/4、共享 ModelSpec 回归 57/57。
- Chrome 95 目标生产构建通过；Java、TypeScript、安全与总代码 Review 均 PASS，无未关闭的 CRITICAL/HIGH/MEDIUM finding。
- GitNexus `detect_changes(scope=all)`：154 个 changed symbol、32 个文件、0 个受影响流程、LOW；结果包含用户已有的 `dts-ingestion`、Sprint-83 与 sprint queue 改动，未纳入本 Sprint 修改范围。
