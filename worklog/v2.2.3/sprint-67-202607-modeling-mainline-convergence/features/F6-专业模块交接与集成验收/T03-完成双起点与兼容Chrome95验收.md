# T03：完成双起点、兼容与 Chrome 95 验收

**优先级**：P0
**状态**：DONE
**依赖**：F6-T01、F6-T02、F4-T04

## 目标与用户结果

用真实浏览器证明业务目标起点和资产起点都能不经过业务对象完成闭环，并覆盖旧深链、权限、失败恢复和窄屏。

## 范围与不做

- 范围：IT README 三条旅程、四类表、页面状态、Chrome 95 和跨行业夹具。
- 不做：不以 mocked screenshot 代替真实 API/数据库证据，不只测试顺利路径。

## 输入条件

- F1-F5 自动化和迁移验证通过；
- 测试环境有治理管理员/建模用户同一人、只读用户和跨租户用户；
- 提供中性维度、事务事实、周期快照、累积快照及存量表/dbt 夹具。

## 输出产物

- Playwright/Chrome95 测试结果；
- desktop/narrow 截图或视频；
- API/DB evidence IDs；
- 失败恢复、权限和旧深链日志。

## 详细设计

1. Journey A 从 BUSINESS_FIRST 创建计划、分类、维度、FACT、标准、实现、发布、指标。
2. Journey B 从 ASSET_FIRST 选择表/dbt，确认候选四类表并完成发布。
3. Journey C 访问旧 object route，分别验证自动映射、NEEDS_CLASSIFICATION、无权限和旧写拒绝。
4. 单独验证 DIMENSION 无 activityRef、FACT 有/无可选 activityRef 均可保存，核心门禁仍为粒度/来源。
5. 模拟 API 失败、revision 冲突、来源失效、标准漂移、发布部分失败和刷新/后退。
6. Chrome 95 在 1280×720 与 390×844 验证主动作、Tabs、表单和返回链。

## 影响范围

- `source/dts-platform-webapp/e2e/**`
- Chrome 95 skill/scripts/config
- Sprint-67 fixtures/evidence index
- test users/permissions and disposable test data

## 异常与清理

- 测试失败保留截图、trace、API response 和 DB IDs。
- 测试数据使用独立前缀并提供可重复清理，不能删除共享客户数据。

## 实施与测试设计

1. 先把旅程拆为可复用登录/计划/模型/发布 helpers。
2. 按 A/B/C 顺序实现并在现代浏览器调通。
3. 用真实 Chrome 95 运行，不以 Chromium 版本替代。
4. 回归窄屏、权限和恢复用例，索引所有证据。

## 验证证据

- `it/evidence/chrome95/journey-a/`
- `it/evidence/chrome95/journey-b/`
- `it/evidence/chrome95/journey-c/`
- `it/evidence/runtime/e2e-record-ids.json`

## 2026-07-20 验收结果

- Chrome `95.0.4638.0` 使用真实 `opadmin` 会话、部署 API 和 PostgreSQL，新增 A/B/C 三场景并 3/3 通过；未使用 API route mock。
- Journey A 的 BUSINESS_FIRST 计划已完成维度、FACT、字段单位、实现接管、编译、测试、评审、发布、三项专业模块注册和稳定指标版本回绑；当前模型为 PUBLISHED r3。
- Journey B 的 ASSET_FIRST 计划真实保存 DIMENSION/FACT/SUMMARY/APPLICATION 四类记录，FACT r2 完成同一发布门禁与三项注册；所有记录均无 objectId/processId。
- Journey C 真实旧深链进入 `NEEDS_CLASSIFICATION` 恢复页；旧 POST 返回 410、退役头和 successor Link。退出门禁真实返回 `NO-DROP`，不会误报物理退役。
- 运行入口已保存真实 run 记录，但部署属性明确返回 `RUNTIME_DISABLED`；这只证明运行证据与精确修复路径，不声称外部 Airflow/dbt 提交成功。
- 权限边界通过真实 Spring Security filter chain 验证：`ROLE_EMPLOYEE` 可读取 canonical ModelSpec，但创建 ModelSpec 和 migration dry-run 均返回 403；未伪造生产只读账号。
- 跨租户、自动映射、checksum、幂等重放和迁移投影通过 PostgreSQL Testcontainers + Spring 事务集成测试，夹具使用随机租户并在事务结束时回滚，不污染部署数据。
- 来源失效、标准漂移、revision 冲突和发布部分失败/重试由后端门禁测试覆盖；Chrome 95 的精确计划恢复通过 API 拦截注入验证 UI 恢复，不能解读为真实后端故障。
- desktop/narrow 的真实 A/B/C 截图、真实 API/DB ID 与上述边界测试共同组成验收证据，真实性说明见 `it/evidence/backend-contract/f6-t03-boundaries.txt`。

## 完成标准

- [x] 三条旅程及关键边界通过；真实部署、Spring/Testcontainers 和 UI 故障注入证据分层报告。
- [x] 主线不进入业务对象页面/API。
- [x] 权限、失败、刷新、后退和窄屏可恢复。
- [x] 证据能关联真实 API/DB 记录。
