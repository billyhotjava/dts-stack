# F11 首批可执行测试

日期：2026-09-20。本批先保护已有身份解析、人员绑定和真实页面入口；不实现 F11 新业务接口，也不修改 PKI。85 条用例规格仍是完整目标，下面的子项不能替代整条用例验收。

**本轮实测补充**：用户提供管理端及三员账号后，已通过真实 Chrome 可见 UI 执行 [管理页面回归](runs/20260920-Chrome三员页面回归.md)。这是 CUA 驱动的页面操作记录，不是下列两条 U-EMP/平台脚本运行结果。用户明确本轮不用测试 PKI，因此不运行 pki 登录分支，也不要求证书；保留原脚本可选分支不代表修改 PKI 实现。

## 实现与证明范围

| 可执行文件 | 对应规格 / 本批断言 | 仍未证明的内容 |
|---|---|---|
| [ModelingIdentityServiceTest](../../../../../../source/dts-platform/src/test/java/com/yuzhi/dts/platform/security/modeling/ModelingIdentityServiceTest.java) | UT-010/013/016/024/026/029/036 的现有组件子项：缺稳定 ID、不匹配主体、必需字段缺失、目录错误映射、精确部门、下一边界刷新、嵌套异常恢复、密级不凭空补值；16 个新增参数化执行项，保留 4 个原回归项 | v2 协议版本/枚举、issuer/realm、统一 PDP、2 秒真实网络预算、菜单组合解析、全部后台域、缺密级的下游拒绝 |
| [F11PersonnelBindingTest](../../../../../../source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/personnel/F11PersonnelBindingTest.java) | UT-007：真实分配器配合有状态仓库替身，两种大小写导入顺序及保存后重放、已有绑定抵御新登录名建议；4 个执行项 | 真实开户、并发、来源永久身份、同名无绑定账号认领规则、PKI |
| [F11PersonnelIdentityRepositoryIT](../../../../../../source/dts-admin/src/test/java/com/yuzhi/dts/admin/repository/F11PersonnelIdentityRepositoryIT.java) | IT-004/005 的数据库基础子项，以及 IT-019 的稳定 ID 前置回归：大小写精确查找、kc_id 唯一、改展示名保留绑定、MDM 标志与账号 enabled 分列；4 个执行项 | 多连接竞争、MDM 实际同步不解除停用、真实 Keycloak、迁移回填/歧义隔离/scoped grants 不扩大；改展示名不能算迁移通过 |
| [permission-browser.f11.ts](../../../../../../tests/web-e2e/specs/f11/permission-browser.f11.ts) | ST-002 的 U-EMP 子项：可见页面登录→允许菜单→建模深链接拒绝→返回允许菜单；ST-020 的选定页面桌面/窄屏子项；2 条脚本 | 自定义角色、无菜单、后台 API 抗绕过、全部管理页面/弹窗、真实 Chrome 95、PKI 完整兼容链 |

Java 集成使用一次性 PostgreSQL 17.4 容器、仓库 Liquibase master 和真实 Repository；不连接部署数据库，不启用容器复用。每例事务回滚，由 Testcontainers 清理本次容器。单元测试的目录/仓库替身不算跨服务证据。

实际命令、版本、结果和证据见 `runs/` 下的执行记录；脚本编译、发现测试、执行通过是三个不同状态。

## 后端正式执行入口

开发目录只修改和静态检查。代码提交并推送后，在 `/data/dts-stack` 检查分支和工作区，`git pull --ff-only` 并核对 SHA，再执行：

```bash
mvn -B -f source/pom.xml -pl dts-admin,dts-platform -am \
  -Dtest=F11AuthorizationDecisionTest,ModelingIdentityServiceTest,ModelingAuthorizationServiceTest,F11PersonnelBindingTest,PersonnelSourceFieldGuardTest,IdentityResolutionServiceTest,LegacyRoleAliasesTest,AdminApiResourcePermissionCatalogF11Test \
  -Dit.test=F11PersonnelIdentityRepositoryIT \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

> 说明：只传一个 `-Dtest`，包含 common/admin/platform 全部指定类，避免重复参数覆盖。结果另查各指定类 XML 的实际执行数和零跳过，不能只看 Maven 退出码。

两个 `failIfNoSpecifiedTests=false` 仅容许 reactor 中不拥有这些测试的依赖模块；结果必须另查三个指定类的 XML，核对实际执行数和零跳过，不能只看 Maven 退出码。Surefire 报告在对应模块 `target/surefire-reports/`，集成报告在 admin 的 `target/failsafe-reports/`。这是本批定向验证，非整个服务的全量测试或正式交付包验收。

## Chrome 执行前置

使用独立 [playwright.f11.config.ts](../../../../../../tests/web-e2e/playwright.f11.config.ts)，不继承现有 API 登录/storage-state global setup，不启动 mock 服务。运行机器必须有可见桌面和真实 Google Chrome，页面对接已核对版本的隔离测试服务。账号用例要求 U-EMP 无建模权，并预先具备至少一个允许的可见叶子菜单。

| 环境变量 | 填写规则 |
|---|---|
| `F11_PLATFORM_URL` | 测试门户源站，如 `https://测试域名:端口`，不附路由或凭据 |
| `F11_LOGIN_MODE` | `password` 或 `pki`；密码路径只证明密码登录后授权，不能证明 PKI |
| `F11_USERNAME`、`F11_PASSWORD` | 仅密码路径必需；从受控位置注入，不写入仓库或运行报告 |
| `F11_ALLOWED_MENU_LABEL` | U-EMP 允许且当前可见的叶子菜单中文名称 |
| `F11_ALLOWED_MENU_HREF` | 该菜单实际相对 href，用于核验点击对象；需人工确认属于本门户 |
| `F11_ALLOWED_PAGE_TEXT` | 进入允许页面后独有的业务正文，不用通用导航文字 |
| `F11_MODEL_DENIAL_TEXT` | 建模拒绝页的明确权限不足文案；不能填通用错误、登录失败或任意页面文字 |
| `F11_CHROME_EXECUTABLE` | 可选，真实 Chrome 可执行路径；未提供时使用安装的 `chrome` channel |
| `F11_EXPECTED_CHROME_MAJOR` | 正式兼容验收应指定实际目标版本，如 `95`；只改 UA 不算对应版本通过 |

PKI 模式只点击既有“证书登录”入口。证书、介质和 PIN 由实际操作人通过原流程完成，脚本等待原登录结果；不实现/替换原插件、回调、证书映射。浏览器自动化无法驱动原插件或缺证书时记录 `BLOCKED`，转由实际 Chrome 人工按 [ST-001](system-chrome.md#f11-st-001-pki-登录保持现状) 全部步骤执行并留证，不能换密码路径冲抵。

在构建测试目录安装锁定依赖并检查脚本：

```bash
cd /data/dts-stack/tests/web-e2e
pnpm install --frozen-lockfile
pnpm exec tsc --noEmit --target ES2022 --module NodeNext \
  --moduleResolution NodeNext --esModuleInterop --strict --skipLibCheck \
  playwright.f11.config.ts specs/f11/permission-browser.f11.ts
pnpm exec playwright test --config=playwright.f11.config.ts --list
```

前置齐备后，由已装载受控环境变量的可见桌面终端执行：

```bash
pnpm exec playwright test --config=playwright.f11.config.ts
```

`--list` 仅发现两条脚本，不会打开 Chrome。实际运行全程通过可见元素点击、输入和导航，禁止 token 注入、API 登录、权限响应 mock、隐藏元素强制点击或页面内 fetch 替代动作。PKI 等待窗口每条最多 120 秒；人为介质交互是记录中的人工步骤，不冒充全自动完成。

报告输出 `tests/web-e2e/reports/f11/html/` 和 `reports/f11/artifacts/`；关键步骤截图由脚本生成，保留实际浏览器版本。为防登录凭据/证书交互进入原始工件，本入口关闭 trace/video；需要网络取证时另提供脱敏 requestId/响应状态。失败自动截图及页面截图仍须按测试数据权限管理。

## 下一批接入条件

1. T02/T03 提供 v2 当前身份、组合菜单接口和冻结协议后，实施 UT-011/012/016/020/021/036 剩余子项、IT-007–010；2 秒预算必须以真实边界计时证明。
2. T07 冻结 Q21 来源身份、状态/版本语义并实现同步状态机后，接入 UT-001–009、IT-001–006 的剩余 MDM/Keycloak/竞争恢复场景。
3. T04–T06/T08 提供 PDP、审批、范围及迁移入口后，再实施动作/范围、批量零副作用、后台、迁移与恢复用例；不为凑覆盖率在测试中自建一套业务实现。
4. 系统 21 条规格继续按真实页面旅程接入。目标测试环境、账号矩阵、政策/业务夹具和 PKI 介质不齐的相应项保持未验收；测试脚本已存在不表示 F11 可以上线。
