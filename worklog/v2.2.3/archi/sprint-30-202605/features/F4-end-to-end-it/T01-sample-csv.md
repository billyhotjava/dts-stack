# T01: 准备运营商 CSV 样例与验收数据

**优先级**: P0  
**状态**: READY  
**依赖**: F1

## 目标

准备一份符合现场信号系统语义的 CSV 样例，用于 DTS 入湖、dbt 建模和 metro-stack 训练验证。

## 技术设计

样例字段至少覆盖：

- 设备字段：`equipment_id`、`line_code`、`station_code`
- 时间字段：`event_time`
- 信号测点：电流、电压、温度、动作次数、状态码
- 专家经验：规则命中、风险等级、故障模式、专家置信度
- 数据质量：缺失/异常模拟行

## 影响范围

- `worklog/v2.2.3/sprint-30-202605/assets/`
- `worklog/v2.2.3/thales/v1/dbt_model/ods_ddl/ods_seed_demo_data.sql`

## 验证

- [ ] CSV 可被 DTS `CsvParseService` 解析。
- [ ] 样例中有正常、告警、专家规则命中三类窗口。

## 完成标准

- [ ] 样例数据能支撑端到端 smoke。
