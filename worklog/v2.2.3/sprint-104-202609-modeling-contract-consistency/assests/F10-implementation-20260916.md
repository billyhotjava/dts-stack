# F10 实施记录（2026-09-16）

**范围**：F10-T01 基线、T02 菜单排序迁移与种子同步、T03 实施引导与跨菜单直达。用户于 2026-09-16 授权编码。
**状态**：源码完成；构建测试目录的专项测试、正式构建交付、部署与真实页面验收分别记录，未执行的不宣称完成。

## 改动清单

| 任务 | 文件 | 内容 |
|---|---|---|
| T02 | `source/dts-admin/src/main/resources/config/liquibase/changelog/20260916_03_portal_menu_implementation_order.xml` | 按 titleKey 定位 15 个节点，只更新排序；校验唯一性与父节点；快照排序、修改人、修改时间与种子哈希；断言大屏管理行及绑定不变；回滚原样恢复，他人改动过则拒绝。编号 `20260916_02` 已被部门快照迁移占用，本迁移使用 `20260916_03` |
| T02 | `source/dts-admin/src/main/resources/config/liquibase/master.xml` | 登记于 `20260916_02` 之后 |
| T02 | `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json` | 只调整数组顺序；节点内容与父子关系逐项核对不变；`screens` 节点不变且仍紧跟 `consumption`。新 SHA-256 `9dc18d6b69d874144f400b0f35da43243bb8cb2e645cfaef16a7cda6bdb36a0f` |
| T02 | `source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/PortalMenuImplementationOrderLiquibaseTest.java` | 正向顺序、结构与绑定不变、大屏零改动、回滚逐字段恢复、定位键重复、父节点不符、快照已存在、迁移后人工调序拒绝回滚、master 顺序 |
| T02 | `source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/PortalMenuImplementationOrderSeedContractTest.java` | 种子根与子节点顺序、大屏节点内容、迁移常量等于种子文件哈希、迁移只改排序 |
| T03 | `source/dts-platform-webapp/src/components/journey/journeyContext.ts`、`journeyStageState.ts` | 阶段顺序改为规划、标准、集成、建模、指标、开发、服务、证据；规划以主题域为产出物、无前置；标准与集成不再要求数据源；建模仍要求标准草稿；相邻阶段下一步与文案同步 |
| T03 | `source/dts-platform-webapp/src/pages/workbench/DataManagementWorkbenchPage.tsx` | 引导说明与按钮顺序改为先规划、后集成 |
| T03 | `source/dts-platform-webapp/src/pages/data-modeling/prototype/modelDataManagementLink.ts` | 统一生成“去数据管理”地址；`focus=quality` 与 `returnTo`；返回地址只允许建模工作台 |
| T03 | `source/dts-platform-webapp/src/pages/data-modeling/prototype/ModelWizardFrame.tsx` | 建模完成页保留“去数据管理”，新增“配置质量规则”直达入口，两者都携带返回原模型的地址 |
| T03 | `source/dts-platform-webapp/src/pages/catalog/DataSearchPage.tsx`、`assets/ModelDataOperationsPanel.tsx` | 读取 `focus`、`returnTo`；带质量定位时自动展开一次质量配置；资产未登记时提示先登记；提供“返回模型” |
| T03 | 对应测试：`journeyStageState.test.ts`、`journeyContext.test.ts`、`ModelWizardCompletion.test.tsx`、`modelDataManagementLink.test.ts`、`ModelDataOperationsPanel.focus.test.tsx` | 顺序、门槛、下一步、直达参数、返回地址安全、展开与提示 |
| T01 | `it/scripts/f10-menu-preflight.sql`、`it/scripts/f10-menu-structure-snapshot.sql` | 只读预检与迁移前后结构快照 |

## 实施中的设计调整

- **可见性证明方式（K96）**：管理端按角色过滤菜单时不读取排序号，只取决于父子关系、删除标记、元数据、密级与角色绑定。迁移测试断言这些列与绑定逐行不变，现场用结构快照做迁移前后 diff，即可证明各角色可见页面不变，不再用 SQL 复刻过滤逻辑。
- **规划阶段门槛**：数仓规划页当前不向旅程参数写入主题域，因此不让标准与集成依赖主题域，避免阻断；规划阶段以主题域作为产出物，没有主题域时显示“待开始”而非“已完成”。
- **质量直达落点**：建模完成页的“去数据管理”本就落到资产目录的数据运维面板，本次新增“配置质量规则”并自动展开质量配置。历史交付对话框里的“查看资产”用于查看资产详情，意图不同，保持不变。
- **静态检查**：对改动文件做 Biome 基线对比，只修复本次引入的问题；`ModelWizardFrame.tsx` 等文件原有的排版告警未顺带重排。

## 基线（测试环境，只读）

- 预检：15 个定位键各命中 1 行，父节点全部正确；大屏管理为一级菜单，排序号 8，修改人 `screen-management-root-20260916`；库内种子哈希 `4c807a41…`，与迁移前种子文件一致。
- 迁移前结构快照：`it/evidence/f10-20260916/structure-before.txt`，菜单 96 行、绑定 125 行，SHA-256 `0edc96f322c439e30338c0927f53c7ec8df28c2fb669571c61fe15200b410a9f`。
- 客户现场：未执行，升级前需运行 `f10-menu-preflight.sql`。

## 验证状态

| 阶段 | 状态 |
|---|---|
| 开发目录静态检查 | Biome 对比通过（仅保留原有告警） |
| 构建测试目录专项测试 | 通过（`ac3eb0199`，明细见下） |
| 正式构建与交付包 | 未执行 |
| 容器部署与迁移 | 未执行 |
| 真实页面与 Chrome 95 | 未执行 |

## 构建测试目录专项测试记录（2026-09-16）

构建测试目录 `/data/dts-stack` 执行 `git pull --ff-only` 后 HEAD 为 `ac3eb01993e4a07debb6f65d5996981e81100308`。

| 范围 | 结果 |
|---|---|
| 前端 vitest：旅程、完成页、质量直达链接、数据运维面板聚焦、领导概览（10 个文件，`10e80ee07`） | 67/67 通过 |
| 前端 vitest 复跑：旅程、质量直达链接、数据运维面板聚焦（4 个文件，`ac3eb0199`） | 21/21 通过 |
| 菜单种子源码契约 `portalGoldenLineMenu`、`DataPortalPage`（node --test） | 11/11 通过 |
| dts-admin Maven（Testcontainers）：本次迁移与种子契约、既有菜单契约、一级菜单排序、大屏分离、规划与旧策略菜单迁移，共 7 个类 | 43/43 通过，BUILD SUCCESS |

- **首轮发现的契约冲突**：`10e80ee07` 首轮跑时，`PortalMenuSeedDefaultsContractTest` 和 `portalGoldenLineMenu` 仍固化 F10 之前的顺序：资产目录在数仓规划之前、建模概览排第一、复合指标排第一。这是本次有意调整的结果，已在 `ac3eb0199` 按新顺序更新断言，复跑通过。
- **既有失败（与 F10 无关）**：与改动页面相关的 35 个源码契约文件中，另有 11 条失败。在 F10 之前的提交 `10d2af028` 上跑同一批测试，这 11 条的失败项和失败信息完全相同，涉及 JourneyContextBar 旧路由、Sprint45/49/82/93 资产页、ModelQualityEntry、prototypeReplacement、WorkbenchPersonalization、DataAssetPortalMenu。本次不处理。
