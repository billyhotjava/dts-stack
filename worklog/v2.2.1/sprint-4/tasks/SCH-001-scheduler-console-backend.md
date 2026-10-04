# SCH-001: 统一调度控制台后端聚合接口

## 范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/scheduler/`
- 既有 `ops` / `etl` / Airflow 聚合入口

## 目标

- 为调度中心提供统一 overview / runs / actions 契约
- 不替换调度引擎，只做 platform 聚合与操作代理

## 交付

- `SchedulerResource`
- `SchedulerConsoleService`
- 统一 DTO：overview、run item、action result

## 验收

- 前端无需再通过 3 个页面拼装才能看懂调度状态
- 支持最小控制动作：pause / resume / retry 或明确的只读降级
- 后端错误能返回明确原因

## 当前进度

- 状态：TODO
- 备注：需要复用现有 `ops` 与 `etl` 数据，避免重新造状态源
