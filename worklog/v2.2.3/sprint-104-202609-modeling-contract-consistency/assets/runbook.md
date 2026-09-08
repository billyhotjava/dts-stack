# Sprint-104 复合粒度键 dbt 验证运行手册

## 范围

本手册覆盖 `dts-platform` 的工作区引导宏及 `dts-platform-webapp` 的正式交付范围。`dts-dbt:1.10.0` 仅是一次性专项测试运行时，不是本项构建或拟部署服务。它不创建常驻服务、不覆写业务表，也不替代模型发布或物化验收。

## 前置检查

在 `/opt/prod/s10/deploy` 执行，并只使用已提交、已拉取的源码：

```bash
git pull --ff-only
git rev-parse HEAD
docker image inspect dts-dbt:1.10.0 --format '{{.Id}}'
test -f data/sprint104-acceptance/profiles/profiles.yml
```

当前正式构建候选 SHA 为 `f14830709`；执行前必须以实际 `git rev-parse HEAD` 复核。记录实际提交、镜像完整 ID 和运行时间到本任务的验收记录。不要在命令输出、日志或证据文件打印数据库密码。

## 执行

从受控测试配置向当前 shell 注入 `SPRINT104_DBT_USER` 与 `SPRINT104_DBT_PASSWORD` 后，运行一次性容器。凭据仅作为环境变量传递，不写入文件。

```bash
docker run --rm --network host --entrypoint python3 \
  -e SPRINT104_DBT_USER -e SPRINT104_DBT_PASSWORD \
  -v /opt/prod/s10/deploy:/repo:ro \
  -v /opt/prod/s10/deploy/data/sprint104-acceptance:/evidence \
  dts-dbt:1.10.0 \
  /repo/worklog/v2.2.3/sprint-104-202609-modeling-contract-consistency/it/run-composite-dbt.py \
  --profiles-dir /evidence/profiles --profile dts_sprint104 --target acceptance \
  --source-file /repo/source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtConfigService.java \
  --evidence /evidence/T04-composite-dbt.json
```

脚本退出码 `0` 表示五个预期断言均符合；`2` 表示前置条件缺失；`3` 表示结果与预期不符；`4` 表示证据写入失败。每次执行前删除或另存旧证据，避免混淆记录。

## 判定与停止条件

证据 JSON 必须同时显示：两个合法键顺序为 `0`，两个重复组合和一个空键样例为 `1`。凭据缺失、镜像或提交不符、宏来源摘要变化、或任何结果不符时停止，不将结果标记为发布验收，并转交相应源码或部署负责人处理。

## F2 交付分步诊断要求（T14 待实施验证）

本节为运行手册待交付要求，不是当前系统已具备的操作承诺。

- 先按模型/修订/候选定位物化、质量、发布、目录登记、分析准备各自证据；记录失败步骤、reasonCode、correlationId、目标资产及重试次数。
- “目录已登记、分析失败”只处理分析链路；“缺规则”只处理正确目标的规则配置；不可将重新物化作为通用修复。
- 运行时诊断区分平台源缺失、分析关联缺失、目标/字段未就绪、连接失败、认证拒绝、查询投影失败。T13 固定结构化错误与有界重试后，T14 写入实际操作入口及停止条件。
- 发生版本冲突先重读状态，保留用户输入；不得手工改状态表使其变绿。审计记录不含数据源凭据。
- 以 IT-13/IT-14 真实证据补齐服务恢复和离线部署步骤后才能将本节标为已验证。

## F3 分项运行诊断（2026-09-07）

- 物化前记录模型及实现 revision/checksum、环境、SCHEMA_ONLY/DATA_BUILD、目标数据库/schema/table；物化后同时检查运行成功、关系 VERIFIED 和当前版本完成证据，不能只看零行或 BUILT 标签。
- `MODEL_RELEASE_CANDIDATE_ACTIVE_EXISTS` / `MODEL_ACTIVE_BATCH_CANDIDATE_CONFLICT`：保留返回的 candidateId/status/version；当前规划唯一约束会阻断独立物化，不得删除候选或修改状态表。该契约冲突尚待正式结构迁移边界确认，不把取消原候选当作恢复步骤。
- `DBT_DRAFT_DEPENDENCY_PIN_STALE`：在模型设计页核对上游当前修订，通过“更新引用”保存后重新提交实现；不替换旧版本证据或修改持久化快照。
- `DBT_DRAFT_SOURCE_BUNDLE_UNAVAILABLE`：保留 correlationId，检查静态校验的依赖身份是否与冻结快照一致。badab50a8 修复受管上游占位节点被错误展开为空的缺陷，30 项专项测试通过；真实页面复验另见运行记录。
- `MODEL_SCHEMA_TARGET_ALREADY_EXISTS`：停止结构物化，核对所选目标；不得 DROP/TRUNCATE 或借助接入重建现有表。需要修改结构时按独立版本演进处理。
- 接入绑定失败：核对 modelTarget 当前模型/实现修订、候选、目标身份及声明字段/类型/主键；通过页面修复映射或模型，不删除绑定标记、不放开隐式建表。
- 发布包校验必须等正式构建进程退出 0，再校验完整包、每个镜像归档、镜像 sourceRevision 和受管宏。浏览器部署后需真正重载文档，单独变更 hash 路由不会更新 JavaScript。
- SOURCE 首次写入前已演练原镜像回滚并恢复正常登录；SOURCE 写入后旧程序无法保证兼容，应采用包含 SOURCE 能力的前向修复版本。不得复用首次写入前的回滚结论作为当前降级许可。

## 2026-09-08 结构物化占用恢复

SCHEMA_ONLY_INTENT 为独立结构候选，BUILT 不是需要强制发布的错误状态。MODEL_ACTIVE_CANDIDATE_CLAIM_CONFLICT 表示同模型占用；MODEL_MATERIALIZATION_TARGET_CLAIM_CONFLICT 表示目标与其他活动构建重叠。先核对当前模型/修订/环境/目标和允许的动作，再通过正常页面的明确失效/替换/重试处理。禁止改库清候选、手工删除占用键或伪造发布。

新 origin 数据存在时，旧枚举程序及旧 schema 不可直接回退；迁移 rollback 会给出 ROLLBACK_BLOCKED_SCHEMA_CANDIDATE_HISTORY_EXISTS。此时前向代码修复并重新正式交付，保持历史。发布前保存四服务原镜像及持久挂载证据；不重建其他服务或卷。


### 2026-09-08 结构物化后接管排查补充

- `MODEL_INGESTION_TARGET_NOT_MATERIALIZED`：先比对模型/实现修订、环境与物理核验身份；读取应使用精确模型查询，不使用规划工作台最近两条历史。不得修改候选状态或删表使接入通过。
- 结构候选的逻辑上游可尚未物化；派发只对 `SCHEMA_ONLY_INTENT` 跳过实表代理依赖解析，普通数据构建保留上游证据校验。
- `MODEL_SPEC_GOVERNANCE_CLASSIFICATION_REQUIRED`：建模结果仍可完成，登记/治理受原定密规则限制。测试的汇总模型通过“字段显示设置”显式设置字段密级，再提交该修订的实现；不能用默认降密或恒通过替代定密。
- 修改模型定义后，实现必须匹配同一模型ID、修订与校验和才可直接下一步；旧实现应“提交实现并继续”。预览应返回具体业务阻断，不能变成 rollback-only HTTP500。
