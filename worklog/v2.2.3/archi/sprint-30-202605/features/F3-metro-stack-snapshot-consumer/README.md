# F3: metro-stack 真实快照消费

**优先级**: P0  
**状态**: IN_PROGRESS

## 目标

把 metro-stack 从 demo contract 切换为真实 CSV snapshot package 消费，保留演示模式但不作为默认主链路。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 契约校验支持正式 CSV | P0 | DONE | F1 |
| T02 | 后端导入 snapshot package 并触发训练 | P0 | READY | T01, F2 |
| T03 | 前端展示真实契约与导入状态 | P0 | READY | T02 |

## 完成标准

- [x] metro-stack 可以校验 DTS 导出的 `manifest/schema/quality/lineage/data.csv`。
- [ ] `data.csv` 字段顺序、窗口参数、特征族能进入训练流程。
- [ ] UI 默认展示真实快照，demo 模式降级为显式按钮或开发开关。
