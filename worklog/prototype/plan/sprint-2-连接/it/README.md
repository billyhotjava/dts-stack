# Sprint-2 集成测试（IT）· ① 连接

**状态**: READY
**范围**: 阶段①「连接」全部 8 个 Task 的端到端验证。纯前端原型，全部基于 `VITE_USE_MOCK=1` 的 mock 数据，不接真后端。

## 验证目标

验证"配好数据源 → 至少一个源已连通 → 阶段① 点亮 `✓`"的黄金主线入口闭环，并确认五个 foundation 页面（数据源/连接器/驱动/接入变更/调度）在新 IA 阶段① slot 内全部归位、可路由、接 mock。

## 端到端验证项

| IT | 场景 | 覆盖 Task | 期望 | 证据位置 |
|----|------|-----------|------|----------|
| IT-01 | 数据源列表加载 | F1-T01 | 阶段①进入数据源页，CompactTable 默认 10 条/页，状态列渲染正确，≥2 条样例（PLM 订单/ERP 客户） | `it/evidence/IT-01-datasources-list.png` |
| IT-02 | 分页约定 | F1-T01, F2-T01, F2-T02, F3-T01, F3-T02 | 切换每页条数后列表刷新并回到第 1 页（全部 CompactTable 一致） | `it/evidence/IT-02-pagination.png` |
| IT-03 | 数据源详情 | F1-T02 | 从列表「详情」进入正确展示，密钥不明文回显，错误/空态显式 | `it/evidence/IT-03-datasource-detail.png` |
| IT-04 | 新建数据源 | F1-T03, F1-T01 | modal 新建提交成功，列表新增一行并刷新，校验生效 | `it/evidence/IT-04-create-datasource.png` |
| IT-05 | 编辑数据源 | F1-T03 | 编辑回填非敏感字段，密钥留空不覆盖，提交走不可变更新 | `it/evidence/IT-05-edit-datasource.png` |
| IT-06 | 连通测试成功态 | F1-T04 | 触发测试 → 测试中 → 成功（绿点+信息），连通状态回写列表/详情 | `it/evidence/IT-06-conn-test-success.png` |
| IT-07 | 连通测试失败态 | F1-T04 | mock 返回失败 → 红点 + 错误原因可展开，状态不误判为已连通 | `it/evidence/IT-07-conn-test-fail.png` |
| IT-08 | 阶段①状态派生 | F1-T04 | ≥1 源已连通后，左轨阶段① 状态点变为 `✓`（已连通≥1源） | `it/evidence/IT-08-stage-complete.png` |
| IT-09 | 连接器注册列表 | F2-T01 | `ConnectorRegistryPage` 列表 + 类型图标 + 状态渲染，接 `connectorsService` | `it/evidence/IT-09-connectors.png` |
| IT-10 | JDBC 驱动列表 | F2-T02 | `JdbcDriversPage` 列表渲染，上传占位 mock 有成功/失败反馈 | `it/evidence/IT-10-jdbc-drivers.png` |
| IT-11 | 接入变更列表 + 筛选 | F3-T01 | `AccessChangesPage` 三态状态列，筛选写入 URL 查询参数 | `it/evidence/IT-11-access-changes.png` |
| IT-12 | 任务调度 + 关联跳转 | F3-T02, F1-T02 | `TaskSchedulingPage` 列表，运行状态点统一，关联数据源列可跳详情 | `it/evidence/IT-12-task-scheduling.png` |
| IT-13 | mock 开关与重置 | 全部 | `VITE_USE_MOCK=1` 默认开，"重置样例数据"可恢复初始 fixtures | `it/evidence/IT-13-mock-reset.png` |
| IT-14 | Chrome 95 兼容 | 全部 | legacy 构建产物在 Chrome 95 加载无报错；无 oklch/`:has()`/容器查询/subgrid | `it/evidence/IT-14-chrome95.md`（构建日志 + 兼容核查清单） |

## 证据组织

- 截图证据统一放 `worklog/prototype/plan/sprint-2-连接/it/evidence/`，命名 `IT-{NN}-{场景}.png`。
- 文字类证据（Chrome 95 兼容核查、mock 契约对照）放同目录 `.md`，命名同前缀。
- 每条 IT 通过后在本表对应行勾选并补证据链接；未通过保留失败截图 + 复现步骤。

## 关联 mock service 清单

| Service | 来源 | 覆盖页面 |
|---------|------|----------|
| `dataSourcesService` | 复刻现网 `src/api/services/dataSourcesService.ts`（`ConnectionTestResult`/`InfraDataSource`/`DataSourceUpsertPayload`） | F1 全部 |
| `connectorsService` | 复刻现网 `src/api/services/connectorsService.ts` | F2-T01 |
| `jdbcDriversService` | 复刻现网 `src/api/services/jdbcDriversService.ts` | F2-T02 |
| `accessChangesService` | 新增轻量 mock（对齐现网契约风格） | F3-T01 |
| `taskSchedulingService` | 新增轻量 mock（对齐现网契约风格） | F3-T02 |

## 退出标准

- [ ] IT-01 ~ IT-14 全部通过并附证据。
- [ ] 黄金主线入口闭环（IT-04 → IT-06 → IT-08）连贯可走。
- [ ] 五个 foundation 页面全部在阶段① slot 内归位、接 mock、命名对齐现网。
- [ ] Chrome 95 兼容核查（IT-14）通过。
