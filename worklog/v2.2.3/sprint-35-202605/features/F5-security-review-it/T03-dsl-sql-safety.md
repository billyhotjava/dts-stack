# T03: DSL/SQL 安全与注入防护

**优先级**: P0
**状态**: READY
**依赖**: F3,F4

## 目标

禁止默认任意 SQL，确保指标公式、过滤条件和生成 SQL 都来自受控 DSL 和 platform schema contract。

## 技术设计

- DSL 支持 sum、count、count_distinct、avg、min、max、count_if、sum_if、ratio、case_when、date_trunc。
- 字段引用必须来自 visual asset schema contract。
- 过滤条件只允许白名单操作符和值类型。
- ratio 分母为 0 返回 null 或显式诊断，不能静默给 0。
- SQL 生成器使用 identifier quoting 和方言适配，不拼接用户输入为 SQL 片段。

## 影响范围

- `source/dts-metrics` formula parser / SQL generator
- `source/dts-metrics-webapp` formula editor
- `source/dts-metrics/src/test/**`

## 验证

- [ ] raw SQL 字段被拒绝。
- [ ] 未登记字段引用被拒绝。
- [ ] SQL injection 字符串不能进入生成 SQL。

## 完成标准

- [ ] 合作方或普通用户不能绕过 DSL 直接注入 SQL。
