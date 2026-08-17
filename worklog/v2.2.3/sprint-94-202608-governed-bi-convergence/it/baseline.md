# 交付基线（Gate G0）

**执行日期**：2026-08-17
**范围**：只读健康检查、路由可达性、运行库画像、聚焦测试可执行性和 GitNexus 索引状态。
**结论**：运行实例健康；Sprint-93 登录证据可复用步骤和账号，但尚未在 Sprint-94 当前实例复验，不能直接记 PASS。A1～A4、Chrome 95、目标环境观测来源、可重复后端测试和 fresh impact 均未关闭。Sprint 状态 BLOCKED；F0/T02 READY 用于当前实例复验和本地基线，F0/T03 保持 BLOCKED。

**2026-08-17 修订**：Sprint-93 基线证明相邻 Sprint 曾以 `xiezm` 登录并访问受保护 API，可降低复验成本，但 delivery baseline 要求当前 Sprint 实际重跑。P3 将“历史证据”和“当前结论”分开，避免继承旧 PASS。

## P1. 容器与服务健康

| 探针 | 结果 | 判定 |
|---|---|---|
| `v223-dts-platform-webapp-1` | Up | PASS |
| dts-analytics | healthy；容器内 `/api/health` 返回 `status=UP`、appDatabase=UP | PASS |
| dts-platform | healthy；容器内 `/management/health` 返回 `UP` | PASS |
| dts-ingestion / dts-admin / PostgreSQL / Keycloak / Kafka / Traefik | 运行/健康 | PASS |

说明：容器健康仅证明进程与直接健康端点，不证明 BI 用户旅程。

## P2. 代理与页面可达性

| URL | 未登录结果 | 判定 |
|---|---|---|
| `https://bi.yuzhicloud.com/analytics/api/health` | 401 | PASS：受保护代理，不是健康失败 |
| `https://bi.yuzhicloud.com/analytics/api/info` | 401 | PASS：受保护代理 |
| `/bi/dashboards` | SPA shell 200 | PARTIAL |
| `/bi/questions` | SPA shell 200 | PARTIAL |
| `/bi/data` | SPA shell 200 | PARTIAL |
| `/bi/screens` | SPA shell 200 | PARTIAL |

SPA shell 200 不证明路由渲染、API 授权、数据或按钮可用。

## P3. 登录与角色

| 项目 | 状态 | 证据 / 缺口 |
|---|---|---|
| 历史登录证据 | PASS（输入） | `sprint-93/it/baseline.md` P2：2026-08-16 以 `xiezm` 登录，session=200；只用于复用步骤和账号 |
| Sprint-94 当前登录路径 | GAP | 尚未在当前实例重新执行登录与 `/api/session/status`；由 F0/T02 关闭 |
| Sprint-94 受保护 API harness | GAP | 尚未在当前 session 重跑 Platform/Analytics 受保护请求；由 F0/T02 关闭 |
| A1 分析维护者 | GAP | 未绑定隔离数据集和资产权限 |
| A2 独立发布者 | GAP | 未验证与维护者职责分离 |
| A3 授权消费者 | GAP | 未验证部门/角色/密级受众 |
| A4 非授权消费者 | GAP | 未验证列表不可见、直链 403 和导出禁止 |

复用边界见 `../assets/dependency-boundary.md` §2。F0/T02 的第一步是当前实例复验；失败则升级为 G0 blocker。`xiezm` 单账号不足以覆盖 A1～A4，角色与目标环境画像由 F0/T03 负责。

## P4. 数据与迁移基线

- `dts_analytics`：Card=0、有效 Dashboard=0、Dashboard Card=0、Database=0、Semantic Model=0、VDS=0、Screen=1。
- `dts_platform`：Query Dataset Asset=0、Query Dataset Version=0、BI Report Link=1 enabled。
- `biadmin`：25 个 DWS/ADS 关系，代表表 1～50 行。

判定：基础关系可供创建隔离样本，但目前无法执行完整正向、越权和兼容迁移验收。详见 `../assets/domain-profile.md`。

## P5. 浏览器兼容

| 检查 | 状态 | 要求 |
|---|---|---|
| Chrome 95 | GAP | F6/T01 前必须以真实构建运行四页面与编辑旅程 |
| loading/empty/error/success 四态 | GAP | 当前无浏览器证据 |
| console/network | GAP | 当前无登录后的 console error、4xx/5xx 和请求计数证据 |
| 键盘与焦点 | GAP | 需覆盖菜单、选择器、编辑器、发布弹窗 |

## P6. 前端聚焦测试

| 命令/范围 | 结果 | 解释 |
|---|---|---|
| Node source-contract：`Sprint45Consumption.source-contract.test.ts` | 4/4 PASS | 当前菜单/页面契约最小回归可执行 |
| Vitest semantic access 测试 | PASS | 现有语义访问测试可执行 |
| 一次将 Node test 与 Vitest 文件混跑 | exit 1 | runner 不匹配：Node 文件没有 Vitest suite；不是产品测试失败 |

后续固定分开运行 `node --test ...source-contract.test.ts` 与 `pnpm exec vitest ...`，不再混用 runner。

## P7. 后端聚焦测试

| 模块/目标 | 结果 | 阻断 |
|---|---|---|
| dts-analytics `QueryExecutionFacadeTest` | 未进入测试执行 | `source/dts-analytics/target/...` 为 root:root，编译写入 `Operation not permitted` |
| dts-platform `QueryDatasetServiceTest` | 未进入测试执行 | `target/classes/META-INF/build-info.properties` root-owned，写入 permission denied |

判定：这是构建环境权限缺陷，不是测试断言失败。F0/T01 必须使用安全的 ownership 修复或隔离 target，证明普通工作用户可重复执行；不得删除未知用户产物或用 root 构建掩盖问题。

## P8. GitNexus 影响基线

- 当前 `npx gitnexus status`：indexed commit=`76d9657`，current commit=`a0a9fc0`，状态 stale。
- 按仓库规则运行 `npx gitnexus analyze`。
- vendored OpenMetadata Python 抽取 worker 超时，顺序回退继续停滞；为避免无界阻塞，安全中止该刷新。
- 当前索引仍非 fresh，因此本轮没有以旧图谱输出代码 blast-radius 结论，也没有修改业务符号。

F0/T01 关闭条件：确定可排除 vendored 路径或修复索引刷新；确认 fresh 后，对每个拟修改 symbol 运行 upstream impact，并在提交前运行 `gitnexus_detect_changes()`。

## P9. G0 关闭清单

- [ ] 普通工作用户可执行两个后端聚焦测试，至少一个 Analytics 和一个 Platform owner。
- [ ] GitNexus 索引 fresh；形成按 Task 的 impact 记录。
- [ ] 创建隔离的 published/stale/denied 数据集与三类 legacy Card 样本。
- [ ] 在 Sprint-94 当前实例复验登录、session 与至少一个 Platform/Analytics 受保护 API。
- [ ] 准备维护者、独立发布者、授权和非授权消费者账号/权限（F0/T03）。
- [ ] Chrome 95 可运行并保留版本、截图、Network 和 console 证据。
- [ ] 将客户/目标环境的资产量、可用调用窗口和 P95 回写 NFR；无历史来源时记录 UNKNOWN 与观测起点。
- [ ] `../assets/route-inventory.md` 32 行及后端旧写 surface 均有 source/window/value/status；不得把 UNKNOWN 记 0。
- [ ] 后端旧写面（`/api/card` write、MBQL execute、public/embed）盘点完成，与前端路由表分开记录。
- [ ] 所有样本有清理/回滚方案，且不改现有 PJM 业务数据。

## P10. 退役 S0 的零变更证明

本 Sprint 属 Metabase 渐进退役的 S0（盘点与冻结）。交付前须能证明：

- [ ] diff 中无任何新增路由重定向（`LegacyDataModelingRedirect` 的既有两处除外，本 Sprint 不扩展）。
- [ ] diff 中无 feature flag 由开改关。
- [ ] 无迁移 apply 被执行，无表/列/route handler 被删除。
- [ ] `bi/virtual-datasets*` 三条路由行为零回归（有回归测试证据）。
