# H83-01 dbt 运行时紧急修复前置契约

**状态**：DONE_CANDIDATE（F0/T05 已消费并登记认证 derivative；生产激活待 F6 验收）
**类型**：独立热修复前置项，不受 Sprint-83 功能实现节奏约束
**确认日期**：2026-08-02
**关联决策**：D09、ADR-83-15、RT-01

> 2026-08-03 收口：H83-01 已归档精确锁、候选镜像与 RT-01 证据；F0/T05 在 `it/rt-01/certified-profile.json` 登记了独立认证 derivative。原候选证据仍保持 `NOT_CERTIFIED`，不通过改环境变量就地“升级”。本状态只证明认证制品就绪，不代替部署 digest 核对和真实物化 E2E。

## 1. 触发事实与事件边界

修复前，`builds/dts-dbt/Dockerfile` 只直接固定 `dbt-postgres==1.10.0`，而当时本地 `dts-dbt:1.10.0` 镜像实际观测为 `dbt-core 2.0.0-alpha.5`、`dbt-adapters 1.24.5`、`dbt-common 1.38.0`。镜像标签与实际 Core 不一致，且传递依赖没有形成可复现的精确锁定集合，因此触发了本紧急修复。修复后的候选与认证 derivative 不再引用该 alpha 版本集合。

该事实是需要紧急修复的工程准入缺口，不等于已发生生产事故。本契约不声明已经出现客户执行失败、数据错误、数据丢失或安全事件；若后续获得事故证据，应进入独立事故响应流程，不能用 H83-01 的完成状态代替事故结论。

## 2. 目标、边界与调度

H83-01 的唯一目标是修复运行镜像、建立一个可复现/可验证/可回滚的 PostgreSQL dbt runtime **候选**，并产生 [`dbt-compatibility-and-source-only-contract.md`](dbt-compatibility-and-source-only-contract.md) 定义的原始 RT-01 工程证据包。H83-01 不登记产品认证状态；只有 F0/T05 审查该证据、登记 `certificationProfileId` 后，才能执行 `NOT_CERTIFIED → CERTIFIED`。

- H83-01 是发布/物化前置热修复，可与 Sprint-83 的界面、表示读模型、ZIP inspect/import 等设计和实现并行，不等待这些 Feature 排期。
- H83-01 或 F0/T05 任一未通过时，静态 `inspection` 与 `importProjection` 可按各自能力继续；任何发布/物化必须 fail-closed，返回 `DBT_RUNTIME_NOT_CERTIFIED`。
- H83-01 不负责证明客户 dbt 包、客户 Core/manifest schema、宏或 package 的兼容性，也不实现 source-only parser/import。
- H83-01 不扩展 MySQL、达梦或其他 adapter；这些组合继续为 `UNSUPPORTED`。

## 3. 精确运行时画像

候选 profile 在构建前必须形成不可歧义的版本清单，至少记录：

| 类别 | 必填证据 |
|---|---|
| 直接依赖 | 精确 `dbt-core`、`dbt-postgres` 版本，不允许范围表达式 |
| 传递依赖 | 完整冻结解析结果，至少显式核对 `dbt-adapters`、`dbt-common`；锁文件中的每个分发包均为精确版本并带制品 hash |
| 构建环境 | Python 版本、基础镜像 digest、操作系统/CPU 架构、构建源码 commit |
| 运行镜像 | 唯一不可变 image digest；可读 tag 只能作为别名，不能作为认证身份 |
| 数据库 | 被验证的 PostgreSQL 大版本/小版本、驱动与连接模式；凭据不得进入证据文件 |
| 候选身份 | 唯一 `candidateProfileId`，绑定上述版本集合、数据库范围和 image digest；正式 `certificationProfileId` 由 F0/T05 登记 |

安装必须消费已审查的 hash-locked requirements/constraints；仅在 Dockerfile 中固定 adapter 顶层版本不满足准入要求。候选版本由实际验证决定，本契约不预先宣称任何 Core/adapter 组合已兼容。

## 4. RT-01 候选工程证据步骤

以下步骤必须由同一候选 image digest 完成，中途重新构建或依赖变化即使 tag 不变也必须从头执行：

1. **可复现构建**：连续构建解析到同一精确依赖集合；归档锁文件 hash、源码 commit、基础镜像 digest 和候选 image digest。
2. **依赖一致性**：在候选镜像内执行 `python -m pip check`，退出码为 0；同时归档完整已安装分发包清单。
3. **版本断言**：在候选镜像内执行 `dbt --version`，Core、PostgreSQL adapter 及 Python 版本与 profile 清单完全一致；不得用 tag 推断版本。
4. **真实 PostgreSQL 执行**：对隔离的真实 PostgreSQL 实例依次执行 `dbt parse`、`dbt compile`、`dbt build`、`dbt run`，每条命令退出码为 0。命令必须使用同一固定项目、profiles 配置结构和候选 image digest，日志中不得出现密码、Token 或完整连接串。
5. **制品证据**：归档并校验 `manifest.json`、`catalog.json`、`run_results.json` 的生成时间、schema/version、invocation 关联和 SHA-256；不得复用其他镜像或旧运行的 `target/`。
6. **relation 证据**：从 PostgreSQL 侧查询实际生成的 table/view/incremental relation，核对 database/schema/name、列名与类型、物化类型、样例断言或确定性行数/摘要，并证明重复运行符合预期幂等语义。
7. **门禁验证**：候选 profile 可被精确解析，但在 F0/T05 登记前产品状态仍为 `NOT_CERTIFIED`；未认证 digest、版本漂移、adapter 不匹配和未声明 PostgreSQL 版本稳定返回 `DBT_RUNTIME_NOT_CERTIFIED`，不能回退到默认镜像继续执行。

每步证据必须包含 UTC 时间、执行主体、源码 commit、profile ID、image digest、PostgreSQL 版本、脱敏命令与退出结果。任一必填证据缺失或不一致，RT-01 即失败，状态保持 `BLOCKED`。

## 5. 发布、回滚与审计

### 5.1 发布前

- 记录当前部署使用的 image digest、运行配置和物化门禁状态，生成可执行的回滚清单。
- 新镜像使用唯一 tag 和 digest 部署；禁止覆盖 tag 后把旧证据沿用到新 digest。
- 在候选交给 F0/T05 评审前完成 RT-01 原始证据，由运行时负责人确认可复现性；数据平台负责人在 F0/T05 中独立审查并决定是否登记认证，H83-01 不切换执行流量。

### 5.2 回滚触发

出现以下任一情况必须停止切换并回滚：digest 与认证记录不一致、`pip check`/版本断言失败、任一真实 PostgreSQL 命令失败、artifact 无法关联、relation 不一致、凭据泄漏风险、健康检查或受控烟测失败。

### 5.3 回滚动作

1. 将发布/物化门禁恢复为 `NOT_CERTIFIED`，先阻断新运行；
2. 终止或隔离候选 digest 的新任务，确认没有继续写入目标 relation；
3. 恢复变更前记录的镜像 digest 与运行配置；该旧 digest 当前仍未认证，因此不得因回滚而重新开放物化；
4. 验证未认证调用稳定返回 `DBT_RUNTIME_NOT_CERTIFIED`，并核对 Airflow、platform 与公共审计记录；
5. 保留失败日志、artifact checksum、relation 观察和 correlation ID，修正后以新 profile/digest 重新执行完整 RT-01。

回滚不是认证成功，也不删除失败证据。若需要修复真实业务 relation，必须使用单独、经过审批的数据恢复方案。

## 6. 完成判定

H83-01 候选修复与原始证据已完成；下列条件只证明候选就绪，不单独表示产品 profile 已 `CERTIFIED`：

- [x] Core、PostgreSQL adapter 和全部传递依赖已精确、带 hash 锁定；
- [x] 基础镜像、候选镜像 digest、源码 commit 与 profile ID 已归档；
- [x] 同一 digest 的 `pip check` 与 `dbt --version` 证据通过；
- [x] 同一 digest 在声明的真实 PostgreSQL 版本完成 parse/compile/build/run；
- [x] manifest/catalog/run_results 与 PostgreSQL relation evidence 完整、可关联；
- [x] 未认证组合和漂移组合保持 fail-closed；
- [x] 候选回滚与认证 derivative 撤销策略已归档；生产切换/回切仍待 F6 现场演练；
- [x] 候选结果与原始证据回写兼容契约并交给 F0/T05；客户兼容声明和 Sprint-83 真实 E2E 仍按各自门禁独立记录。

完成 H83-01 本身只允许声明该精确 PostgreSQL runtime 候选及原始证据就绪；`materialization=CERTIFIED` 的声明来自 F0/T05 的独立认证记录，不自动形成客户环境兼容承诺，也不能替代 Sprint-83 的部署后 RT-01 回归。
