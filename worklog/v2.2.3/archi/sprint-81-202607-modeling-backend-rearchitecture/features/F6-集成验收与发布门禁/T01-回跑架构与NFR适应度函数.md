# T01：回跑架构与 NFR 适应度函数

**优先级**：P0
**状态**：PLANNED
**依赖**：F1～F5 全部编码完成

## 目标

一次集中执行架构、性能、并发、outbox、执行网关、安全和删除适应度函数，阻断任何散文式质量承诺。

## 技术设计（Contract-first）

- **输入契约**：固定 commit、目标镜像、数据库快照、`assets/nfr-budget.md` 每一行。
- **输出契约**：`FitnessResult {id,command,environment,threshold,actual,pass,evidence}`；任何适用项缺 evidence 视为 fail。
- **执行集**：ArchUnit、unit/IT、Testcontainers、query count/EXPLAIN、50 VU API、10k outbox、fault injection、secret scan、schema/source contract。
- **错误路径**：flaky/infra error 不转 PASS；复现后修复并整批重跑受影响集合。
- **范围**：不跑无关模块全仓 E2E；最终浏览器旅程留给 T03。

## 影响范围

只读/测试执行与证据；发现缺陷回到所属 Feature 修复，不在本 Task 隐式改架构。

## 验证

- [ ] nfr-budget 每行有 command、actual、threshold、result。
- [ ] 客户量级相关预算使用 F0/T02 最大租户数据或脱敏代表集。
- [ ] failures/waivers 明确且任一 P0 fail 导致 NO_GO。

## Definition of Done

- [ ] 所有适用适应度函数 PASS。
- [ ] GAP/N/A 均有理由和用户批准；P0 不允许豁免。
- [ ] 证据入 IT-01～IT-06 对应目录。
