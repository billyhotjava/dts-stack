# FACT 事实形态夹具矩阵

| factShape | 合法 timeSemantics | 核心字段 | 结果 |
|---|---|---|---|
| TRANSACTION | EVENT_TIME | event_time (`role=TIME`) | PASS |
| PERIODIC_SNAPSHOT | SNAPSHOT_DATE / PERIOD | snapshot_date 或 period (`role=TIME`) | PASS |
| ACCUMULATING_SNAPSHOT | MILESTONE_DATES | 多个里程碑时间字段 (`role=TIME`) | PASS |
| PERIODIC_SNAPSHOT | EVENT_TIME | event_time | `MODEL_SPEC_FACT_TIME_SHAPE_MISMATCH` |
| 任意 | 引用非 TIME 字段 | 普通属性字段 | `MODEL_SPEC_TIME_FIELD_INVALID` |

业务活动在所有夹具中均可为空；来源仍是 FACT 的稳定必填引用。
