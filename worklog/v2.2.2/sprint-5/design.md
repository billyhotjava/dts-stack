# Sprint-5 设计：Platform 后端重构输入基线

## 背景

`source/dts-platform` 当前正在演进 dbt/Airflow 触发链、产出表重建链和回滚后的级联重建链。功能方向是对的，但当前实现已经暴露出两类问题：

- 运行时错误传播不一致，导致排障信息失真
- 服务实现、控制器契约和测试基线没有同步收口

这类问题如果继续叠加，会让后续平台联调越来越依赖人工经验，而不是依赖稳定的接口契约和测试反馈。

## 本 Sprint 要解决什么

本 Sprint 不直接改实现，而是把 review 结果沉淀成后续重构的输入：

- 哪些问题已经确认
- 为什么是高优先级
- 后续重构应该按什么顺序推进
- 什么叫“重构完成”

## 已确认的问题面

### 1. DAG ready 检查语义失真

`AirflowClient.getDag()` 当前把非 `404` 的 Airflow 错误也折叠成“空结果”，上层 `EtlResource.waitForDagRegistration()` 会继续等待并最终报“DAG 尚未注册”。这会把真实的 `401/403/500` 误诊成“调度器没注册 DAG”。

### 2. 重建产出表语义已变化，但契约没有一起收口

`DbtOutputRelationService.prepareRebuild()` 从“先 DROP 再 build”切到了“依赖 dbt full-refresh”，但：

- 旧测试还按 DROP 语义断言
- `EtlResource` 的相关测试也没有跟着更新 mock 行为
- 重建链的接口返回字段虽然还在，但其业务含义已经变化

### 3. 回滚后 full-refresh 触发失败会被吞掉

`RollbackCascadeService` 和 `RollbackProxyResource` 都会吞掉异常。最终结果是：级联清理执行了，但 dbt rebuild 没真正触发，接口仍然可能返回成功，造成“业务层回滚了，分析层没补齐”的半完成状态。

### 4. 当前改动没有经过最小回归面收口

本轮实测命令：

```bash
cd source/dts-platform
mvn -Dtest=DbtOutputRelationServiceTest,DbtDagServiceTest,EtlResourceTest test
```

结果：

- `DbtDagServiceTest` 通过
- `DbtOutputRelationServiceTest` 失败
- `EtlResourceTest` 5 个 error

所以后续重构必须先恢复这组最小回归面。

## 设计原则

### 原则 1：错误要原样上抛，不能被改写成别的问题

Airflow 返回什么错误，上层至少要能区分：

- DAG 不存在
- DAG 存在但不可用
- Airflow 鉴权失败
- Airflow 服务异常

### 原则 2：服务语义变化时，测试和接口说明必须一起变

尤其是：

- `prepareRebuild`
- `rebuildDbtOutputRelation`
- `checkDagReady`
- 回滚后 `triggerDbtFullRefresh`

### 原则 3：对外成功必须意味着关键副作用已经真正发生

如果回滚场景要求触发 dbt rebuild，那么“成功”至少要表示：

- 已成功提交 Airflow 触发请求
- 或明确告诉调用方“主流程成功，但 rebuild 未触发”

不能无条件吞掉。

## 后续重构建议顺序

### Phase 1：先恢复错误语义和测试基线

- 收口 `getDag` / `waitForDagRegistration`
- 修复 `DbtOutputRelationServiceTest`
- 修复 `EtlResourceTest`

### Phase 2：再收口回滚链的成功/失败契约

- 明确 `triggerDbtFullRefresh` 的失败传播方式
- 明确 `RollbackProxyResource` 的返回策略
- 增加回滚触发链测试

### Phase 3：最后整理接口文案和状态展示

- 统一 502 文案
- 去掉硬编码“30 秒”文案，改为配置驱动
- 清理容易误导的日志与 message

## 非目标

- 不在本 Sprint 内改 `platform-webapp`
- 不在本 Sprint 内改 `dts-ingestion`
- 不做 Airflow/Compose 级别的部署改造
- 不扩展新的 dbt 功能，只收口当前语义
