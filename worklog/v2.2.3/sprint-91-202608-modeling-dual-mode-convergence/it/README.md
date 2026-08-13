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
| IT-06 | DESIGNER 实现经统一候选链完成物化/发布 | F5/T01 | release IT + 候选 checksum |
| IT-07 | DBT 实现（原生 + 接管而来）经相同候选链完成物化/发布 | F5/T01 | release IT + 候选 checksum ×2 |
| IT-08 | Chrome 95、空/加载/错误/成功、旧深链、**建模页首屏不含 monaco** 集中回归 | F4/T01、F5/T02 | Chrome95 smoke + console/network 清单 + build 产物体积 |
| IT-09 | **只读账号在两种模式下的完整四态**：visual 只读、code 显示需权限空态、无接管/保存/提交、直调维护 API 403 | F1/T03、F2/T03、F5/T02 | 浏览器截图 + MockMvc 403 断言 |

> IT-00 是接管相关验收的前置。它若不能证明 4 文件 bundle 可验证、可冻结、可恢复、可编译，则 F2 不得进入生产实现。

**执行纪律**：编码期间只跑对应单元/契约测试；全部编码结束后执行一次集中 build 与 E2E，失败仅做针对性重跑。
