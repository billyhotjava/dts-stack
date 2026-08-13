# 发布安全计划（Gate G3）

**变更类型**：API 增量 + 数据建模前端能力替换 + 审计动作目录扩展
**风险等级**：中（涉及实现所有权原子转换、严格审计与并发版本钉定；不含数据库 schema 变更或数据回填）
**状态**：IN_PROGRESS（源码验证完成，待运行实例重建、真实事务/浏览器验收与回滚演练）

## 1. 发布不变量

1. `ModelSpec` 与 implementation revision 仍是唯一事实源；代码接管不得建立平行模型、制品台账或发布状态机。
2. 可视化/代码视图切换只改变页面表现，不写库；只有显式 ownership transition 才能把 `DESIGNER_GENERATED` 转为 `DBT_MANAGED`。
3. transition 必须同时钉定 model/implementation revision 与 checksum，并在一个事务中完成 revision、制品、幂等回执和严格审计；任一步失败整体回滚。
4. 接管后的物化、候选构建、评审和发布继续复用既有 release candidate 控制面；前端不自动推进审批或上线。
5. `PUBLISHED` 与物理关系 `ONLINE + verified + exists` 分开判断；本次容器健康不等于真实发布链验收完成。

## 2. 迁移与兼容策略

| 阶段 | 内容 | 本次是否包含 | 回滚边界 |
|---|---|---:|---|
| Expand | 新增 dbt 只读预览、ownership transition 校验/提交 API 和审计动作；扩展表示能力枚举 | 是 | 回退应用镜像；旧客户端忽略新增端点和枚举 |
| Migrate | 无 schema migration、无数据回填；仅在用户显式确认接管时生成新 revision/制品/回执 | 否 | 已成功接管是可审计业务修订，不通过删库回滚 |
| Contract | 不删除旧 API、菜单、路由或数据库结构；旧 `convert-to-designer-generated` 保持原样 | 否 | - |

兼容部署采用后端先行：先加载 `dts-admin` 审计动作字典，再部署 additive `dts-platform` API，最后部署 `dts-platform-webapp`。旧工作台在新后端上仍可运行；新前端不得先于后端切换。

## 3. 当前验证与已知缺口

- 建模后端核心竖线：10 个聚焦测试类，共 147 条通过。
- 审计目录：`dts-common` 聚焦测试 5 条通过，三份 runtime mirror 内容一致且 JSON 有效。
- 前端：Vitest 5 文件 15 条、TypeScript、生产 build 与 Chrome 95 静态兼容扫描通过。
- 既有 release candidate 聚焦回归 80 条中 79 条通过；`builtCandidateCannotBeCancelled` 暴露 `BUILT → CANCELLED` 基线语义冲突。相关发布实现未被 Sprint-91 修改，本次不顺带改写状态机。
- 真实登录、真实 transition 事务、三角色发布、物理 `ONLINE` 和 Chrome 95 浏览器旅程尚未完成，因此部署后仍不得把 Sprint 或 Gate G3/G4 标记为 PASS。

## 4. 部署范围与顺序

只重建以下应用镜像，不重建数据库、Kafka、Keycloak、OpenMetadata、Airflow、dbt runtime 或其他未受影响服务：

1. `dts-admin:1.0.0`：加载新增严格审计动作目录，等待 healthy。
2. `dts-platform:1.0.0`：加载 ownership transition 与表示能力 API，等待 healthy。
3. `dts-platform-webapp:1.0.0`：加载双模式工作台，验证容器入口和外部页面 HTTP。

部署前回滚锚点：

- `dts-admin:rollback-20260813-sprint91` → `sha256:a06331aef1dcdd4ca201c93bdade328b550ea5e3c582b7b7dff5effc99069862`
- `dts-platform:rollback-20260813-sprint91` → `sha256:0c33fd4aa91c89216a8350d242533a99031f19d03b29ca9393e4e140c4e47be9`
- `dts-platform-webapp:rollback-20260813-sprint91` → `sha256:778129f8f3866e697cb254be385becb5d814355b121e7ee17b65e5510ad5a262`

## 5. 回滚步骤

1. 停止继续扩大变更范围，保留失败容器日志、镜像 digest 和请求 correlation/idempotency 标识。
2. 将三个 rollback tag 重新标记为 compose 使用的 `1.0.0` tag。
3. 按 `dts-admin → dts-platform → dts-platform-webapp` 执行 `docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate <service>`，每一步等待后端 healthy。
4. 核验管理端/平台健康、前端入口和审计动作目录；若仍失败，不启动其他容器，不删除新业务 revision，以前向修订处理已确认的接管事实。

回滚标签解析核对可以作为发布前置；实际容器回滚/前滚演练和真实业务验收完成前，Gate G3 保持 IN_PROGRESS。

## 6. 发布后检查

- [ ] 三个新容器使用本轮构建镜像，`dts-admin`、`dts-platform` 达到 healthy。
- [ ] 平台健康端点成功，前端容器首页与外部 `/data-modeling` 返回成功状态。
- [ ] 启动日志无新增 migration、Spring context、审计目录或前端代理错误。
- [ ] 维护账号完成一次预览/取消零写入/确认接管；核对双 revision、三类制品、receipt 和审计。
- [ ] DESIGNER、原生 DBT、接管 DBT 各完成一条三角色发布链，并独立核对物理 `ONLINE + verified + exists`。
- [ ] Chrome 95 完成集中旅程，确认 Monaco 仅在代码视图懒加载。
- [ ] 执行一次实际回滚/前滚演练并保存镜像 digest、健康和页面证据。
