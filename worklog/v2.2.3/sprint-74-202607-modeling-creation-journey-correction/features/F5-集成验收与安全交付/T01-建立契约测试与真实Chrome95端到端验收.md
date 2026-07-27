# T01：建立契约测试与真实 Chrome95 端到端验收

**优先级**：P0  
**状态**：DONE
**依赖**：F1～F4

## 目标

用同一批代表数据证明创建、逻辑、实现、纠错、治理和结果全链路。

## 技术设计（Contract-first）

- **输入契约**：F0 fixture、真实认证 storage state、IT-01～IT-12、NFR budget。
- **输出契约**：JUnit/Node/build/E2E 报告、截图、API transcript（脱敏）、SQL 计数和 P95。
- **数据流**：focused RED→GREEN → Feature 组合测试 → 一次后端组合 → 一次前端 build → 一次真实 E2E。
- **错误路径**：任何 gate 失败保留证据并停止 DONE；不反复跑无关全量测试。
- **复用点**：既有 source-contract、Spring tests、Chrome95 harness；不新建第二套 runner。
- **实现方案**：建立一条统一命令/清单执行全部 fitness；截图目录按 IT ID。

## UI 交互规格

覆盖空、加载、错误、成功；键盘选择类型卡；冲突恢复；结果 PARTIAL；不能只断言 DOM 存在。

## 影响范围

前后端 tests、E2E 脚本、`it/` 证据，不修改产品契约。

## 验证（RED→GREEN）

- [x] 先固化当前失败：默认 FACT、无 DESIGNED、全 gate 展示、dbt 错位
- [x] F1～F4 实现后对应测试转绿
- [x] production build 和 Chrome95 通过
- [x] NFR 所有 blocking GAP 转 PASS

## Definition of Done

- [x] 架构：契约/迁移/并发/兼容测试全绿
- [x] UI：IT-01～IT-12 真实证据
- [x] 切片：真实 Spring Security+PG+dbt+发布结果通过
