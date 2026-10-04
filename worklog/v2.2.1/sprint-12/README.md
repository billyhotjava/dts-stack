# Sprint-12：逻辑建模执行按钮统一

## 目标

统一 `逻辑建模` 页面上的执行入口，消除“顶部工具栏”和“流水线按钮”并存造成的语义冲突，并按方案 1 将主流程与辅助动作分层。

## 范围

### 前端
- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/ModelPipeline.tsx`

### 验证
- `source/dts-platform-webapp` 页面级回归
- 与 `dbt compile/test/build` 入口一致性校验

## 结论

- 顶部工具栏保留为唯一执行入口
- 顶部主流程统一为：`编译 / 测试 / 上线`
- `提交变更 / 同步模型 / 文档 / 回退` 合并进 `更多`
- 底部 Git 面板不再提供第二套内联提交入口
- 中部流水线保留为状态展示，不再承载执行动作
- `上线` 继续统一到 `dbt build`
- Airflow 动态 DAG 已补齐 `dag_run.conf.vars -> dbt --vars` 透传，避免专题绑定变量在执行链中丢失

## 文档

- [design.md](./design.md)
- [plan.md](./plan.md)
