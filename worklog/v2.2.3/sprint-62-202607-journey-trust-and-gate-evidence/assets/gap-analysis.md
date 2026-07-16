# Sprint-62 缺口分析与对标结论

## 对标输入

- 阿里云 DataWorks：工作空间为容器的持久化向导/流程、对象真实状态驱动的阶段完成、发布前冒烟与门禁。
- dbt：声明式定义 + manifest 单一事实源；`dbt build` 的 compile→test→materialize 门禁语义；结构化 run results。
- 参考文章（bilibili 小中台）：获取被验证码拦截，未纳入；对标以 DataWorks 与 dbt 为准。
- 客户现实：无正规开发人员，离线 Excel 为主，习惯菜单导航，验收要可打印可盖章的材料。

## Sprint-61 已建立（不重复做）

- URL 承载旅程上下文（journey + 7 artifact 参数透传）— 选型正确，保留。
- 声明式 8 阶段状态机（requiredParams/artifactParam/gap/blocker/nextAction）。
- JourneyContextBar 低侵入接入 8 页面（每页 +2 行）。
- 验收包模型（markdown/json 导出）与 source-contract 测试体系。

## 四个缺口 → 四个 Feature

| # | 缺口 | 视角 | Feature |
|---|------|------|---------|
| 1 | 旅程无实例：刷新/关标签断链，不可恢复 | DataWorks | F1 |
| 2 | done=URL 有参数，手改 URL 即变绿 | 真实性/DataWorks | F2 |
| 3 | 门禁证据是跳转链接，非结构化结果 | dbt | F3 |
| 4 | 菜单直达无旅程感知；验收包不可打印 | 客户无开发 | F4 |

## 设计决策

- 持久化先 localStorage（key 版本化），后端旅程实例 API 以 blocker 形式标注缺口，不伪装。
- 真实性校验采用注入式校验器（同步纯函数内核 + 可选异步数据源），保证 source-contract 可测、mock 可换真实 API。
- 门禁四项 checks：落标覆盖率（对应 dbt 的 schema 约束）、编译（compile）、测试（test）、运行（run results）；聚合 verdict pass/warn/fail。
- "加入旅程"提示会话内可关闭，不打扰常规菜单用户。
