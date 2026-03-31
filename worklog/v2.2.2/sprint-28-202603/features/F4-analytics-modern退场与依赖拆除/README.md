# F4: analytics modern退场与依赖拆除

**优先级**: P0  
**状态**: READY

## 目标

把 `dts-analytics-webapp/modern` 从“仍参与运行时、构建和测试链路的活跃前端”推进为“可控退场对象”，并在满足门禁后彻底删除。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 盘点modern运行时、构建与测试依赖 | P0 | READY | - |
| T02 | 切断platform对modern UI入口与代理依赖 | P0 | READY | T01,F1/T02 |
| T03 | 清理compose、build、test对modern webapp的依赖 | P0 | READY | T01,T02 |
| T04 | 删除modern前端代码并更新发布兼容清单 | P1 | READY | T01,T02,T03 |

## 完成标准

- [ ] 明确 `modern` 当前仍被哪些 runtime/build/test 链路依赖
- [ ] analytics UI 的唯一入口切换到 platform
- [ ] `modern` 服务从 compose/build/test 中可拆除
- [ ] 删除 `modern` 前端后，保留的仅是 `dts-analytics` 后端服务
