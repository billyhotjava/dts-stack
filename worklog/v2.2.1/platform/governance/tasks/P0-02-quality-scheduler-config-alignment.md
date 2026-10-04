# P0-02 质量调度配置键对齐

`status`: `done`
`priority`: `P0`

## 目标

收敛调度配置前缀，避免因配置键不一致导致巡检计划不触发。

## 范围

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/QualityTaskScheduler.java`、`source/dts-platform/src/main/resources/config/application.yml`。

## 子任务

1. 统一调度配置命名到 `dts.platform.governance.*`。
2. 兼容历史配置键读取（过渡期）。
3. 补充启动日志，打印生效调度参数与来源。

## 验收标准

- normal/dev/legacy 三模式下调度参数可正确读取。
- 巡检任务按配置周期触发。
- 旧配置在过渡期可用并有弃用告警。

## 完成记录

1. 调度键统一为 `dts.platform.governance.quality.task-scheduler-delay-ms`。
2. 保留旧键回退读取：`dts.governance.quality.taskSchedulerDelayMs`。
3. 启动时输出生效 delay；当仅使用旧键时输出弃用告警。
4. 配置入口已加入 `source/dts-platform/src/main/resources/config/application.yml` 并支持环境变量 `DTS_GOVERNANCE_QUALITY_TASK_SCHEDULER_DELAY_MS`。

## 风险与回滚

- 风险：线上环境仍使用旧键。
- 回滚：保留双读策略并加告警。
