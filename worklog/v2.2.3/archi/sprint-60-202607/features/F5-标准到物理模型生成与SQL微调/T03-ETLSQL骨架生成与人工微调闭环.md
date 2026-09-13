# T03: ETL/SQL 骨架生成与人工微调闭环

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

允许系统生成 ETL/SQL 骨架，并允许开发者微调转换逻辑，但保存和发布必须重新校验模型规格、字段标准、血缘和质量规则。

## 技术设计

SQL 骨架至少包含：

- 来源表或 `source()` 引用。
- 字段选择、别名、类型转换。
- 码表映射或标准码值转换占位。
- 主键、分区、抽取批次、源系统字段。
- 增量过滤条件占位。
- DWD/DWS/ADS 的 join、deduplicate、aggregate 占位。

人工微调后的反校验：

| 校验 | 阻断条件 |
|------|----------|
| 字段契约 | 输出字段缺失、类型不一致、主键/分区缺失 |
| 标准契约 | 标准字段未绑定数据元、码表字段无公共码表 |
| 血缘契约 | 引用未登记来源，输出字段无法追踪输入字段 |
| 质量契约 | 必需 dbt tests 或质量规则缺失 |

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/LowCodeDevelopmentPage.tsx`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`

## 验证

- [ ] 生成 SQL 后手工删除标准字段，保存时返回 blocker。
- [ ] 修改字段别名后，系统能提示字段契约 drift。
- [ ] SQL 引用未登记来源时，发布阻断。
- [ ] 通过校验的 SQL 可以进入开发环境发布。

## 完成标准

- [ ] SQL 微调不会绕开模型规格。
- [ ] 微调结果能回写血缘、标准绑定和质量校验状态。
- [ ] UI 能区分警告和阻断。
