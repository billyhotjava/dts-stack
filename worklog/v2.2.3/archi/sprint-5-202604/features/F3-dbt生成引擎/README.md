# F3: dbt 生成引擎

**优先级**: P0
**状态**: READY

## 目标
根据 GovIndicatorDefinition 元数据自动生成 dbt SQL + schema.yml，写入 models 目录，复用现有编译/执行链路。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | Jinja2 SQL 模板集（5类） | P0 | READY | F1/T02 |
| T02 | DbtIndicatorGenerator 核心服务 | P0 | READY | T01 |
| T03 | 衍生指标拓扑排序 + 依赖解析 | P0 | READY | T02 |
| T04 | schema.yml 自动生成 + 阈值测试 | P0 | READY | T02 |
| T05 | 生成→编译→执行 API 端点 | P0 | READY | T02, T04 |

## 完成标准
- [ ] 5 类 SQL 模板覆盖所有 aggregation_type
- [ ] 衍生指标依赖正确排序（ref 引用正确）
- [ ] schema.yml 含阈值测试规则
- [ ] API 一键生成+编译+执行成功
