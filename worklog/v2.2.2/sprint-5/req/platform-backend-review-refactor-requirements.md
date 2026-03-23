# Platform Backend Review Refactor Requirements

## 需求背景

本材料来自对 `source/dts-platform` 当前变更集的代码审查，目标不是新增功能，而是为下一阶段重构提供清晰、可验证的输入。

## 审查范围

- 模块：`source/dts-platform`
- 分支：`v2.2.2`
- 基线：`b8cb30b65`
- 重点文件：
  - `src/main/java/com/yuzhi/dts/platform/service/etl/AirflowClient.java`
  - `src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
  - `src/main/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationService.java`
  - `src/main/java/com/yuzhi/dts/platform/service/etl/RollbackCascadeService.java`
  - `src/main/java/com/yuzhi/dts/platform/web/rest/RollbackProxyResource.java`

## 证据基线

本轮审查实际执行了最小回归命令：

```bash
cd source/dts-platform
mvn -Dtest=DbtOutputRelationServiceTest,DbtDagServiceTest,EtlResourceTest test
```

结果摘要：

- `DbtDagServiceTest`：PASS
- `DbtOutputRelationServiceTest`：FAIL
- `EtlResourceTest`：5 errors

说明当前实现、测试和对外契约没有同步收口。

## 已确认问题

### 问题 1：Airflow 真实错误被误报成“DAG 尚未注册”

当前链路：

- `AirflowClient.getDag()` 对非 `404` 的 Airflow 错误仅返回空
- `EtlResource.waitForDagRegistration()` 把空结果统一当成“DAG 未注册”

后果：

- `401/403/500` 会被误诊成“DAG 尚未注册”
- 用户会白等一个 ready wait 周期
- 排障方向被带偏

### 问题 2：产出表重建链的语义已经变更，但测试和返回契约未同步

当前链路：

- `DbtOutputRelationService.prepareRebuild()` 已从“先 DROP 再 build”改为“依赖 dbt full-refresh”
- 旧测试仍断言 DROP 已执行
- `EtlResource` 相关测试也仍按旧行为 mock

后果：

- 测试基线失效
- `executed/message/dropExecuted` 等字段的真实含义变得模糊
- 后续调用方难以判断“是否真的做了重建前动作”

### 问题 3：回滚后 full-refresh 触发失败会被吞掉

当前链路：

- `RollbackCascadeService.triggerDbtFullRefresh()` 吞掉触发异常
- `RollbackProxyResource` 又在外层再吞一次

后果：

- 回滚主流程即使触发 rebuild 失败，也可能整体返回成功
- 现场会留下“业务层已回滚，但分析层未重建”的不一致状态

### 问题 4：最小回归面已经是红的

这是当前最直接的工程风险：

- 实现已经变化
- 单测没有同步
- 后续继续叠加改动时，很难判断是新问题还是旧问题未清

## 重构必须满足的要求

### R1：错误传播要保真

- `404` 和 `401/403/500` 必须区分
- “DAG 未注册”只允许用于真正的 not found 场景
- Airflow 返回的真实失败信息应能到达调用层

### R2：重建产出表的服务语义、接口语义和测试语义必须统一

- 明确 `prepareRebuild()` 的真实动作定义
- 明确 `executed` 字段是否仍表示“已执行数据库动作”
- 明确 `rebuildDbtOutputRelation` 的 payload 中哪些字段代表“计划执行”，哪些代表“已执行”

### R3：回滚后的 rebuild 触发不能静默失败

- 要么失败即失败
- 要么返回部分成功状态
- 但不能把关键副作用失败伪装成完全成功

### R4：最小回归面必须恢复为绿灯

至少恢复并纳入门禁：

- `DbtDagServiceTest`
- `DbtOutputRelationServiceTest`
- `EtlResourceTest`

### R5：文案和配置要一致

- ready wait 文案不能硬编码写死
- 如果等待时间来自配置，文案和行为都要跟配置一致

## 非目标

- 本轮不扩展新的 dbt 功能
- 本轮不改 Airflow 部署架构
- 本轮不处理 `platform-webapp` 的页面交互
- 本轮不顺带处理无关模块的历史技术债

## 验收标准

### 功能验收

- 能正确区分 DAG not found 和 Airflow 服务错误
- 重建产出表接口返回的字段语义明确且稳定
- 回滚后 rebuild 触发失败时，调用方能够明确感知

### 测试验收

运行：

```bash
cd source/dts-platform
mvn -Dtest=DbtDagServiceTest,DbtOutputRelationServiceTest,EtlResourceTest test
```

期望：

- 全部通过

### 交付验收

- 重构后的行为说明回填到本目录
- 新旧契约差异记录到实现说明中
