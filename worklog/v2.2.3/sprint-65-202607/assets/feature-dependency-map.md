# Sprint-65 Feature 与 Task 依赖图

## 1. Feature 级依赖

| Feature | 必须等待 | 可并行部分 | 交付给下游的契约 |
|---|---|---|---|
| F1 | 无 | 退役盘点与术语表可并行 | 所有权、canonical 决策、Feature Flag 策略 |
| F2 | F1-T01/T02 | schema 设计与 API 契约评审可并行 | planId、baseline、version/review、migration mapping |
| F3 | F2-T02/T03 | 业务与资产两个编辑区可并行 | READY 基线和来源业务映射 |
| F4 | F3-T04 | 架构策略与模型设计可局部并行 | ModelSpec、grain、fact shape、relations |
| F5 | F2-T03、F3-T04 | 投影后端与工作台 shell 可并行 | stage projection、primary blocker、deep links |
| F6 | F4-T02、F5-T01 | 模型中心 IA 与 dbt 回写可并行 | implementation ownership、artifact evidence |
| F7 | F1-T03；切换等待 F3-F6 | 资产登记和兼容测试可提前 | 新菜单、redirect、legacy freeze |
| F8 | F2-F7 | 契约测试夹具可提前 | 可复核交付证据与 Go/No-Go |

## 2. Task 关键路径

```text
F1-T01 -> F1-T02 -> F2-T01 -> F2-T02 -> F2-T03
                                      ├-> F3-T01 -> F3-T02/F3-T03 -> F3-T04
                                      │                              ├-> F4
                                      │                              └-> F5
                                      └-> F2-T04

F4-T02 -> F6-T01 -> F6-T02/F6-T03 -> F6-T04
F5-T01 -> F5-T02 -> F5-T03/F5-T04

F3 + F4 + F5 + F6 -> F7-T02 -> F7-T03 -> F7-T05 -> F8
                                      \-> F7-T04 -/
```

## 3. 不允许并行的切换

- canonical schema 未完成迁移演练前，不冻结旧计划写入；
- baseline API 未稳定前，不分别实现两套前端状态机；
- ModelSpec 所有权未稳定前，不开放 dbt 双向更新；
- stage projection 未提供真实状态前，不上线工作台完成徽标；
- 新菜单权限未验证前，不移除旧菜单 ID；
- Chrome 95 和回滚未通过前，不把旧入口默认关闭。

## 4. Definition of Ready

Task 开始前必须满足：

- 上游契约已合并且有测试，不是口头约定；
- 明确唯一数据所有者和写路径；
- 已运行 GitNexus impact 并登记风险；
- 受影响的旧资产已在退役登记表中；
- 有失败路径、迁移/兼容和验收样例；
- 测试数据使用通用夹具，PJM 只作为补充回归。
