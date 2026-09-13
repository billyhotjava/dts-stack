# Chrome 95 真实验收

**浏览器**：Chromium 95.0.4638.0  
**结果**：3 passed，0 failed  
**后端**：真实部署 API/PostgreSQL；没有 `page.route` mock

覆盖：

1. 创建时无默认类型，四类业务目的均有适合/不适合/例子；
2. 逻辑设计、数据实现、发布结果各自只有一个 owner；
3. 数据实现拥有目标物理名/装载配置与高级 dbt 入口；
4. 发布结果展示真实 compile evidence，不伪造 physical asset，且无 dbt 编辑入口；
5. 改型向导先 preview 再确认，明确追加 revision；
6. 390px 窄屏无水平溢出；
7. console error、page error、request failure 均为 0。

截图：

- `it-01-02-create-purpose-cards-chromium95.png`
- `it-06-07-data-implementation-chromium95.png`
- `it-08-release-result-chromium95.png`
- `it-09-reclassification-wizard-chromium95.png`
- `it-12-release-result-chromium95-narrow.png`

运行时需由操作者在本机提供 Chrome 路径和临时 Cookie jar；二者不进入仓库。
