# F2：构建运行与版本绑定

**优先级**：P0
**状态**：READY

## 目标

把静态 artifact 生成、真实 dbt 执行和模型实现归属分层记录，保证每次构建均可定位到候选、环境、selector、target 和精确模型版本。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 拆分静态编译产物和外部构建证据 | P0 | READY | F1-T04 |
| T02 | 绑定 dbt run、selector、target、revision 与 checksum | P0 | READY | T01 |
| T03 | 收敛 implementation ownership 与漂移 | P0 | READY | T02、Sprint-67 F3 |
| T04 | 构建失败与精确修复闭环 | P0 | READY | T02、T03 |

## 完成标准

- [ ] SQL/SCHEMA/TEST/DOC artifact 与 dbt compile/build/test 运行分表意、分状态展示。
- [ ] 服务端校验外部 run 的 selector、target、候选版本和真实终态。
- [ ] 一个候选条目只有一个有效实现 owner，文件路径或 checksum 漂移可被检测。
- [ ] 构建失败可回到精确模型、文件和错误位置修复，并以新 run 重试。

