# T05: 最终统一 review/test 协议

**优先级**: P0
**状态**: DONE
**依赖**: T04

## 目标

落实“中间不编译，全部任务完成后统一 review 和测试”的执行协议。

## 技术设计

- 每个任务完成时只做源码级检查、差异检查和必要静态搜索。
- 最终统一执行后端测试、前端 build、镜像构建和容器重建。
- 记录未执行中间编译的风险和补救策略。

## 影响范围

- Sprint-31A IT
- Sprint-31 IT
- Sprint-32 IT
- final review report

## 验证

- [x] 最终测试清单覆盖所有模块。
- [x] 中间阶段不声称 build passed。

## 完成标准

- [x] 交付结束时有一份统一验收报告。

## 证据

- `worklog/v2.2.3/sprint-31a-202605/assets/final-review-test-protocol.md`
