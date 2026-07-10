# F2: 阶段真实性校验

**优先级**: P0
**状态**: READY

## 目标
阶段完成态从"URL 带了参数"升级为"参数指向的对象真实存在"，手改 URL 不再能把阶段变绿。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | artifact 校验契约与 mock 数据源 | P0 | READY | - |
| T02 | 阶段状态机接入校验结果 | P0 | READY | T01 |
| T03 | 工作台与上下文条呈现校验状态 | P1 | READY | T02 |

## 完成标准
- [ ] 每类 artifact 参数有校验器：valid / invalid / unknown（无校验能力）三态。
- [ ] invalid 参数使阶段 blocked 并给出"清除并重新选择"恢复动作；unknown 显示"待确认"而非 done。
- [ ] 校验数据源可注入：mock 实现先行，真实 API 缺口以 apiName 标注。
- [ ] source-contract 覆盖三态与状态机联动。
