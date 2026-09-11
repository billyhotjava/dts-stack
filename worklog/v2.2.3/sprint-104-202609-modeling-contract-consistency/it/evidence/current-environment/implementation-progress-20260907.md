# Sprint 104 本轮实现与验证记录

状态：实施中。源码落地不等于部署或验收通过。

## 本轮范围

- F2-T02：四步同路由向导，按 `step` 定位；定义保存独立于实现；提交实现串行保存、校验、提交；导航不触发生命周期写入。未保存修改提供保存/放弃/留在当前页。版本与发布记录作为低频操作。
- F2-T02：统一 delivery-status，按模型、修订、校验和、环境、候选及物化实现版本核验证据；列表与向导消费同一聚合，目录登记与分析准备分开。
- F2-T03：物化关系核验完成时登记质量目标，质量上下文 GET 无写操作；规则配置固定当前产出 dataset，保存原规则版本 CAS，禁止重绑定；常用规则在 W3 就地配置，完整配置页保留回到当前候选上下文。
- F2-T04：目录与 W4 复用治理摘要编辑器；负责人/说明局部 PATCH 使用原始版本 ETag；409 保留输入；既有 full PUT 同样防止旧页面覆盖新数据。
- F2-T05：按 tenantId + platformDataSourceId 精确注册分析连接，核验指定表和字段；无名称/默认库回退；前向迁移保留 legacy 数据，无法证明归属时拒绝关联。

## 当前证据

GitNexus 重建完成：165454 symbols / 353200 relationships。工作台及代码视图 impact 为 LOW；新聚合、质量/资产路径按各自 impact 记录实现。未修改已执行 changeSet。

截至本记录首次写入，开发目录只进行了编辑与静态检查；未运行构建、编译或测试。待 commit/push 后在部署目录拉取同一 SHA，执行专项测试、正式构建和浏览器验收。

## 尚待完成

- 正式测试/构建，修复所发现的回归。
- 新镜像、迁移、在线与离线交付证据。
- Chrome 实际四步全流程及跨模型证据隔离、冲突/失败恢复、帮助与兼容性验收。
- 既有 UNKNOWN 运行阻断的真实恢复验证。

所有任务继续保持 IN_PROGRESS，未将源码完成计作 Sprint DONE。

## 正式验证进展（2026-09-07）

源码 `6817843b9` 与修正 `ae21a9c10` 均完成开发目录 commit/push、部署目录 ff-only 同步。测试和构建全部在部署目录执行。

- 前端首轮 31 项通过、6 项失败：测试 runner 混用、matchMedia 模拟缺失、中文按钮空格及重构后的旧源位置断言，已分别纠正。Node 原生契约独立运行 21/21。
- `ae21a9c10` 的失败范围复测 15/16，通过规则编辑、治理摘要异步/CAS、交付状态组件；余下源断言错误限制了正常指标表单还原字段，已修正待复跑。
- `ae21a9c10` 前端 `pnpm build` 退出 0，TypeScript 与 legacy production bundle 通过；日志 `/tmp/s104-formal-frontend-rebuild.log`。
- Java 首轮 testCompile 的 fixture 无 setter 已修正；第二轮 platform 128 tests，3 failures/25 errors：一项命令权限回归须恢复早拒绝，其余 fixture 缺 published audit、Mockito 嵌套 mock/无用 stub 和文本 helper 默认 null。分析服务独立编译发现缺失 Optional 导入。均正按失败范围修复，尚不能登记后端通过。
- 新镜像尚未部署，以上不是 Chrome 或运行验收证据。

## 正式验证与交付当前结果（2026-09-07）

部署目录已完成修正后的后端恢复组 29/29、analytics 15/15、Node 原生契约 21/21、最终 source-contract 9/9、前端正式构建、隔离 Liquibase update/rollback/re-update，以及三镜像与离线包校验。后端首轮目标组的 73 项通过来自一个另有 23 项夹具错误的失败命令，未与恢复组相加或写成整体成功。构建源码为 `e83b51076e216a2464d5b8703186a7cb93ac723c`；迁移脚本验证使用 `3aa077d0c49d48aa0600705a11c3676fb5bf95ce`。完整边界、日志、镜像 ID 和包校验见[正式验证与离线交付证据](formal-validation-and-delivery-evidence-20260907.md)。

三个 e83 镜像已部署：platform 与 analytics 为 `healthy`，webapp 为 `running`（该容器无 healthcheck），且部署清单记录仅这三个服务容器替换。已对解包离线包在独立临时目标执行 `dts-upgrade-lite plan` 并本地加载三份 image archive，均成功；未执行离线 `apply`/`rollback` 或 dbt 运行。线上两条新增 migration 已执行。catalog 多输出组件 5/5、helper 定向复测 3/3、typecheck 已有专项证据；新 W4 前端不在 e83 镜像中。当前物化 dispatch 已定位为 `MATERIALIZATION_SOURCE_MISSING`，通用根因仍在修复，未标为恢复。Chrome 95、真实租户页面及剩余 IT 场景仍未执行，Sprint 保持 IN_PROGRESS。完整容器、离线 plan、包内 dbt 资源和限制见[正式验证与离线交付证据](formal-validation-and-delivery-evidence-20260907.md)。
