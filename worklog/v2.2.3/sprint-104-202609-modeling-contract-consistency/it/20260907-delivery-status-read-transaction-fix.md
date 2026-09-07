# 交付状态读取 500：质量证据读取事务修复

## 现场证据

- 运行后端为 `fe2d2562aa36217653f89573750bcd151c4eef8c`。
- 模型 `6c632178-50b1-4d03-a268-7126ed2c54c1` 已成功提交：r4 / DBT_MANAGED，实现 v2 / ACTIVE，创作草稿 COMMITTED。
- 交付候选 `6eef96db-5f6f-484c-80ac-309e5b53384d` 于 2026-09-07 12:08:28 创建。Traefik 记录此前交付状态请求为 200，自 12:08:29 起返回 `DownstreamStatus=500 / OriginStatus=500`。
- 源码调用链：`ModelDeliveryStatusQueryService.get` → `CandidateQualityRuleContextService.context` → `CandidatePublicationEvidenceRepository.requireCurrent`。构建完成前不存在完整成功证据，仓储抛出异常；内层 REQUIRED 事务将外层查询标为 rollback-only，上层 catch 返回降级结果无法清除此标记。
- 工作台质量摘要的 `CandidateGovernanceQualityEvidenceService.evaluate` 也会捕获同类异常，存在相同事务问题。
- 现场候选当前 BUILD_FAILED；流水线记录为 DBT_SUCCEEDED / artifacts synchronized，调度终态 FAILED，错误码 `MODEL_DBT_AIRFLOW_UPSTREAM_FAILED`。该构建终态未修改，本次未启动任何重试。

## 修改

1. 质量配置查询复用治理质量服务的阶段判断，DRAFT、BUILDING、BUILD_FAILED、CANCELLED、STALE 阶段返回“需先完成构建”，不读取成功物理证据。
2. 质量配置只读上下文使用 `NOT_SUPPORTED` 暂停外层事务，证据读取在自身事务中结束；证据缺失可正常返回不可用状态。
3. 新增 `evaluateForRead`，只用于候选工作台质量摘要，采用相同事务隔离。保留原 `evaluate/evaluateLive/requirePublishable` 命令调用边界，发布仍强制校验质量证据。
4. 新增使用 Spring 真实事务代理、模拟 JDBC 连接的回归用例：构建前不访问成功证据；构建后缺证据时外层查询可提交；发布命令缺证据仍被拒绝。同步现有工作台用例的读取入口。

## 验证边界

- 已只读核对运行版本、代理访问日志、模型/候选/流水线记录，并完成源码调用与事务边界复核。
- GitNexus impact 覆盖质量上下文、质量摘要和工作台调用点，返回 LOW。`git diff --check` 通过。
- **未运行测试、编译、打包、部署、页面验收**，遵从用户手工执行要求。新增用例尚未执行。
- 本次只修改后端；在部署目录拉取并正式构建、重建 `dts-platform` 后验收。

## 手工验收

1. 重新打开上述模型第三步，交付状态读取成功，展示已有候选的构建失败状态及适用操作。
2. 新建候选和构建执行期间，交付状态持续可读；未构建阶段不要求配置成功输出的质量规则。
3. 构建完成后可读取质量配置；证据不可用时保留阶段状态和阻断原因，不能因此把交付查询变成 500，也不能误报质量通过。
4. 缺少必需质量证据时发布仍被阻断。已有 BUILD_FAILED 记录按真实构建原因手工处理和重试，不作为本次状态读取修复的成功证明。
