# F5 状态、来源与准入实施基线

日期：2026-09-09。开发 SHA e48531600；运行 platform/webapp 标签 s104-mat-fa920a05ba10。只读 PostgreSQL 采样：模型 DRAFT=16、PUBLISHED=3、ARCHIVED=5；实现 ACTIVE/DESIGNER_GENERATED/PHYSICAL_ASSET=4，ACTIVE/DBT_MANAGED/GENERATED=19。状态不是有效 pin 的替代。

采样命令通过部署实例 docker exec psql 执行 SELECT status,count(*) GROUP BY status 及实现 status/ownership/input_mode 聚合，无数据修改。已提交实现与 modeling_dbt_implementation_draft 分表；作者编辑草稿不撤销既有实现。

## 冻结规则

- 同方案逻辑设计可引用草稿；实现准入检查当前设计/实现完整 pin、权限、分层与循环；跨方案固定修订要求发布。物化与发布保持各阶段策略。
- 核心数据复用 ModelSpecRepository、ModelLifecycleRepository、ModelSpecReader；可见域复用 ModelSpecDomainReadAccessPort。新投影短 REPEATABLE_READ 读取事务，200 项/2000 节点/32 层/3 秒保护预算；实际性能待 Chrome 触发后观测，不宣称已达标。
- 字段契约：固定模型修订字段；源表由已确认 binding 经现有 resolver/catalog 读取；GENERATED 使用原规则。joins、mapping、filter、aggregation 使用来源字段；groupBy/deduplicateBy 使用输出字段。多源映射要求限定别名；不新增表达式语法。
- 来源身份与实际 inputs 顺序一致；维度逻辑引用不自动算实现输入。移除来源必须处理所有引用，不能同名静默改绑。
- 暂存保留编辑与诊断；提交重新校验且字段错误为 422 MODEL_IMPLEMENTATION_SOURCE_FIELDS_INVALID。权限/owner CAS 不弱化；旧 pin 不自动升级。
- 归档正常入口的引用保护保留；预检独立检查被钉修订 ARCHIVED；未覆盖路径不提前豁免。

## 证据边界

本文件为只读数据/源码契约冻结，不是新增功能验收。A/B/C 草稿样本、并发、20/200 候选性能、Chrome 95 在统一构建后由 F5-T06 留存真实证据。无新增代码级测试，无直接修改数据库。

## 统一构建前静态检查

2026-09-09：F5 各功能源码完成首轮实现，7 个变更前端文件通过 Biome check，git diff --check 通过。GitNexus 影响检查限于建模、来源读取及工作台；部分新增符号不在旧索引中，已按实际 diff 核对。未增加或扩展代码级测试；正式编译、镜像部署与 Chrome 验收尚待执行。
