# Sprint-69 Feature/Task 依赖图

```text
F1-T01 冻结输入输出与完成语义
  └─> F1-T02 ReleaseCandidate/Entry 契约
        └─> F1-T03 CAS、幂等与状态机
              └─> F1-T04 候选读模型与 API

F1-T02
  ├─> F2-T01 静态产物与外部构建证据拆分
  │     └─> F2-T02 dbt run 精确绑定
  │            └─> F2-T04 构建失败与修复闭环
  ├─> F2-T03 ownership 与漂移
  └─> F3-T01 QualityRun/CheckResult 契约
        └─> F3-T02 默认规则包
              └─> F3-T03 规则级结果验证
                    └─> F3-T04 质量门禁与修复路由

F1-T03 + F2-T04 + F3-T04
  └─> F4-T01 显式审核状态迁移
        └─> F4-T02 职责分离与权限
              └─> F4-T03 发布与注册重试
                    └─> F4-T04 回滚、审计与 StageProjection

F1-T01
  └─> F5-T01 UX/视图模型契约
        └─> F5-T02 计划交付工作台壳层
F2-T04 + F3-T04 + F5-T02
  └─> F5-T03 构建与质量证据详情
F4-T04 + F5-T03
  └─> F5-T04 审核发布回滚交互
        └─> F5-T05 SQL/dbt 页面拆分与上下文锁定

F1-F4
  └─> F6-T01 后端/API/安全回归
        └─> F6-T02 PostgreSQL canonical 全链路
F5 + F6-T02 + Sprint-67 四层模型稳定接口
  └─> F6-T03 真实 dbt 与 Chrome95 旅程
        └─> F6-T04 最终构建、回滚与 Go/No-Go
```

## 并行规则

- F2 与 F3 可在 F1-T02 完成后并行。
- F5-T01/T02 可在后端写 API 完成前推进，但只能使用冻结契约和只读 fixture。
- F6-T01 的单元/API 测试随 F1-F4 增量落地，不等到最后集中补测。
- DIMENSION/SCD2 真实 E2E 等待 Sprint-67 四层模型接口稳定；其他模型类型不等待。

## 禁止提前

- 未完成 F1-T03 前，不写候选 mutation UI。
- 未完成 F2-T02 前，不把任意 externalRunId 记为当前模型通过。
- 未完成 F3-T03 前，不用一个 TEST 布尔值宣称质量通过。
- 未完成 F4-T02 前，不开放批准和发布按钮。
- 未完成 F6-T02/T03 前，不将 StageProjection 第六步的绿色状态作为 Sprint DONE 证据。
