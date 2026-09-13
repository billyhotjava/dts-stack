# Sprint-91 集成验收索引

本目录只接收真实执行证据。规划阶段列出验收编号，不预填“已通过”。

| ID | 验收旅程 | 关联 Task | 证据要求 |
|---|---|---|---|
| IT-00 | **编译产物与 4 文件 canonical bundle 实测**（探针 A/B/C） | F0/T02 | 制品类型/路径/checksum + validate/freeze/restore + compile 门禁断言；证据不得含 SQL 正文 |
| IT-01 | DESIGNER 的 TECHNICAL 只读能力可用；同一模型 URL 在 visual/code 间切换，刷新与后退可恢复，旧 `open=advanced` 可兼容 | F1/T03、F1/T01、F1/T02 | evaluator 决策矩阵单测 + route test + 浏览器录屏/截图 |
| IT-02 | DESIGNER 模型代码模式只读预览（3 文件、stg 可辨识），切换前后 revision/ownership 不变 | F2/T01、F2/T03 | API 响应 + DB 断言 + UI 成功态 |
| IT-03 | DESIGNER 显式接管为 DBT；**接管后制品类型 = `{SQL,SCHEMA,CONFIG}` 且立即 compile 成功、selector 不变**；并发/重复请求安全，首个草稿可建 | F2/T02、F2/T03 | service/REST IT + DB 制品查询 + compile 结果 + 审计记录 |
| IT-04 | DBT 模型可视化只读投影；投影不可信时展示具体原因；**页面无回切入口（含置灰态）** | F3/T02 | UI 四态 + 组件树断言 |
| IT-05 | Monaco 多文件保存、校验、诊断定位、ETag 冲突恢复 | F4/T01、F4/T02 | Vitest + 浏览器证据 |
| IT-06 | DESIGNER 实现经统一候选链完成物化/发布 | F5/T01 | release IT + model/implementation/dependency/plan checksum |
| IT-07 | DBT 实现（原生 + 接管而来）经相同候选链完成物化/发布 | F5/T01 | release IT + model/implementation/dependency/plan checksum ×2 |
| IT-08 | Chrome 95、空/加载/错误/成功、旧深链、**建模页首屏不含 monaco** 集中回归 | F4/T01、F5/T02 | Chrome95 smoke + console/network 清单 + build 产物体积 |
| IT-09 | **只读账号在两种模式下的完整四态**：visual 只读、code 显示需权限空态、无接管/保存/提交、直调维护 API 403 | F1/T03、F2/T03、F5/T02 | 浏览器截图 + MockMvc 403 断言 |
| IT-10 | **全链路样本登记**：已有含数据 ODS + DWD DIM/FACT + DWS + ADS，所有引用固定修订 | F0/T03 | UI 操作截图 + 不含 SQL/密码的 ID/revision/checksum/source binding 清单；ODS 行数前后不变 |
| IT-11 | **无 ZIP 手工建模链**：UI 声明依赖并逐表提交 dbt；declared/parsed 全部 MATCHED | F6/T01～T03、F5/T01 | 浏览器 journey + dependency snapshot/checksum + artifact/compile 证据 |
| IT-12 | **可视化生成链**：字段映射/过滤/关联/聚合形成四层 implementation | F7/T01～T03、F5/T01 | designer settings + golden compile + dependency/target identity 断言 |
| IT-13 | **单表物化依赖计划**：请求 ADS，缺失上游 BUILD、精确已验证上游 REUSE、阻断项可解释 | F8/T01～T03 | preview/candidate checksum + ordered entries + 物理表字段/行数 + observation |
| IT-14 | **批量物化**：多选模型合并依赖闭包、去重、按拓扑执行 | F8/T01～T03 | UI 多选证据 + 单一 batch correlation + per-model attempt/dispatch/observation |
| IT-15 | **三种 authoring 等价性**：DESIGNER、手工 DBT、ZIP apply 共用身份/依赖/候选 | F6/T01、F7/T03、F5/T01 | dependency graph、dbt uniqueId、selector、CatalogAssetKey 对账；允许 artifact checksum 不同 |
| IT-16 | **二次物化与治理交接**：新执行历史、不复制模型/资产，资产/元数据/血缘/质量/发布证据连续 | F8/T03、F5/T01；Sprint-93 | 执行前后数量对账 + correlation 查询 + retry/rollback 证据 |

> IT-00 是接管相关验收的前置；IT-10 是 F6～F8 的共同数据前置。若 IT-10 未固定同一批样本 ID/revision/checksum，各 Feature 不得用自己的临时链代替。

**执行纪律**：编码期间只跑对应单元/契约测试；全部编码结束后执行一次集中 build 与 E2E，失败仅做针对性重跑。IT-11～IT-16 的主链必须通过 Chrome 手工操作，ZIP/API/数据库脚本不能替代模型创建；数据库只用于只读核验物理结果和审计证据。
