# F5: 规划升维闭环验证

**优先级**: P0
**状态**: READY

## 目标
证明业务过程、分层、grain、矩阵四类新对象在链路中不丢失、不假绿。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 新契约链路source-contract | P0 | READY | F1-F3 |
| T02 | 门禁与红线组合回归 | P0 | READY | F2/T03,F3/T02 |
| T03 | Sprint IT证据与构建回归 | P0 | READY | T01/T02 |

## 完成标准
- [ ] processId/grain/layerFlow 全链路契约测试。
- [ ] 门禁组合矩阵（含红线违规）无假成功。
- [ ] 全部命令级证据入 it/README.md。
