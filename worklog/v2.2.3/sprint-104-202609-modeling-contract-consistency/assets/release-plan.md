# Sprint-104 建模契约交付记录

## 交付范围

以下历史记录为 F1 交付，涉及两项正式服务范围：

1. `dts-platform`：随 JAR 的 dbt 工作区引导内容交付组合唯一性宏。
2. `dts-platform-webapp`：随正式前端交付提供建模功能入口。

`dts-dbt:1.10.0` 仅作为既有正式镜像运行 IT-04 专项测试，不属于本项构建或拟部署服务。不在本计划中创建额外常驻服务，也不以本地 workspace 或临时容器文件替代正式交付内容。

## 发布前记录

`2161b2cda7b2782a0beed29158b9ca681c1a9d36` 是 F1 的历史部署基线，`bd0670acc` 是更早的验收基线；两者不能作为 F2/T10–T13 的构建或验收证据。当前正式证据入口为 [formal-validation-and-delivery-evidence-20260907.md](../it/evidence/current-environment/formal-validation-and-delivery-evidence-20260907.md)：`e83b51076e21` 的三服务镜像、隔离迁移和离线预检已有记录，`90d111280` 已完成三镜像构建、包校验与受控部署；运营任务路由问题仍待修复。

## 执行顺序

1. 在部署目录确认分支、工作区和提交 SHA，执行正式构建与交付包检查。
2. 核对 `dts-platform` 交付物包含工作区引导所需的宏，再按部署配置更新该服务。
3. 按部署配置更新 `dts-platform-webapp`，并记录实际镜像标识。
4. 使用既有 `dts-dbt:1.10.0` 运行时，按 [runbook.md](runbook.md) 运行一次性 IT-04 验证并保存 JSON 证据。
5. 分别记录正式构建/交付包、容器部署和真实模型或页面验收结果；后两项必须由对应负责人实际执行后填写。

下述构建和部署已执行；另一台离线环境安装、Chrome 95 与全部模型端到端验收仍未完成。

## 实际构建与部署记录（2026-09-06）

- 正式源码提交：`bd0670acc89e7dd1be82e0d12961ba7744f63ca2`。开发区 commit/push 后，部署区 ff-only 拉取并核对。
- 入口：`builds/dts-build.sh --image dts-platform dts-platform-webapp --opmanager-output data/sprint104-acceptance/release-bd0670acc`。TypeScript 和 legacy production bundle 均成功。
- `dts-platform:1.0.0`：`sha256:8351d96cab4ad7563cd1e404e2c7c6529740588d0126d5a179a59e4b3fe86895`。
- `dts-platform-webapp:1.0.0`：`sha256:06754db0262dd46a0d8fab8a8a7fa45bfb5fe1126c835af3252cd71c5203af7c`。
- 交付包：`/opt/prod/s10/deploy/data/sprint104-acceptance/release-bd0670acc/dts-opmanager-upgrade-20260906-161102.tar.gz`，SHA-256：`353c7f78bb409eff02f73fbc5239b9358afbd9b913c8ce1283b811005da5d65b`。包内 images 仅包含上述两个镜像 tar。
- 在 `/opt/prod/s10/deploy` 执行 `docker compose -f docker-compose-app.yml up -d --no-deps dts-platform dts-platform-webapp`，退出0。后端 healthy；前端 running。
- 核对部署前后的全部其他容器 ID/StartedAt：无变化。保留 deploy 项目、正式挂载及持久化数据。
- 未将交付包发送到另一台离线机器；未以本记录宣称离线安装和 Chrome 95/模型页面全部验收通过。证据日志位于 `/tmp/sprint104-formal-build.log`，部署目录 `data/sprint104-acceptance/pre-deployment.json` 和 `release-evidence.json`。

## 修复版正式交付（3fa21140e）

- 编译器回归19/19，前端presentation回归6/6。正式双镜像构建退出0。
- package：`release-3fa21140e/dts-opmanager-upgrade-20260906-163631.tar.gz`；SHA-256 `67df70a9f8e9c7980227c2cff4b74f8556bd8bbe130f2e1bf8363d4050782e9e`。
- platform镜像：`sha256:30cace1f8e75ce777cac1a4985e337ddbaf5f8a55add1b7fcf4aa2dccb55ba3e`。
- webapp镜像：`sha256:bea293e514212386baab42e825e3deef6c025fdffe0450c580a7af4314511456`。
- 同一正式Compose范围更新成功，后端healthy、前端running，其他全部容器ID/StartedAt不变。证据见 `it/evidence/current-environment/release-3fa21140e.json`。

## 最新兼容性修复交付（66fcd49dd）

- 源码提交：`66fcd49dd8c1db3484e2358eecf1a3ae5f08df34`；草稿安全回归27/27，正式双镜像构建退出0。
- 包：`release-66fcd49dd/dts-opmanager-upgrade-20260906-165424.tar.gz`，SHA-256 `855c1efdb059730ebda1b80818a57b437fbfd4b2ea5116dd9560fc42317e0e13`。
- platform镜像：`sha256:7b2a3f6f739c4e6459f9f1d6414b6d44e8f2546d8407db901121ca2239849846`；webapp同3fa版本内容，正式构建复用同一镜像ID。
- Compose更新退出0，platform healthy、webapp running，其他容器未变化。完整记录见 `it/evidence/current-environment/release-66fcd49dd.json`。
- 部署后浏览器控制两次超时，未完成旧模型校验/提交及两次物化复测，不将测试与部署结果作为页面通过证据。

## 冻结源实际生命周期修复交付（2161b2cda）

- SecurityTest27/27；正式双镜像构建、Compose部署均退出0，platform healthy，webapp镜像未变，其他容器无变化。
- 包及镜像完整信息：`it/evidence/current-environment/release-2161b2cda.json`。
- 旧草稿校验/提交及显示名保留已通过页面复验。新样例首次物化/质量/发布登记通过；再次运行与目录同步未通过，Sprint不可标记DONE。

## F2/T10–T13 正式交付计划（Gate G3，进行中）

**变更类型**：组合（`dts-platform`、`dts-analytics`、`dts-platform-webapp` 的 API/UI 与两套 Liquibase expand migration）。

**风险等级**：高。`dts-platform` 和 `dts-analytics` 分别在启动时执行 Liquibase；T13 还依赖平台到 analytics 的受信任服务调用。F1 历史双镜像不能作为本范围的构建、离线包或验收证据。

本节保留可重复执行的发布步骤。实际结果以 [formal-validation-and-delivery-evidence-20260907.md](../it/evidence/current-environment/formal-validation-and-delivery-evidence-20260907.md) 和 [release-evidence-plan.md](release-evidence-plan.md) 为准：`e83b51076e21` 的三服务已经部署，隔离迁移 update/rollback/reupdate 与离线 hash/load/plan 已通过；`90d111280` 的正式三镜像构建及受控部署已完成；运营任务路由问题仍待修复。浏览器登录、完整离线安装目标和实际容器回滚演练仍是 G3 GAP，不能写为整体 DONE。

### 1. 迁移和兼容性

| 服务/变更 | changeSet | 本次阶段 | 旧代码兼容性 | 物理回滚 | 当前状态 |
|---|---|---|---|---|---|
| platform 资产乐观锁 | `20260906-01-catalog-dataset-version` | Expand：`catalog_dataset.version bigint NOT NULL DEFAULT 0` | 旧代码忽略新增列；新代码可读取初始 `0` | 不建议线上 drop column；代码镜像可回退且列保留 | 隔离库 update/rollback/reupdate 已通过；线上 changeSet 已 EXECUTED |
| analytics 平台源绑定 | `0054-01` | Expand：`tenant_id`、`platform_data_source_id` 可空，加二元唯一约束 | 旧代码可忽略可空列；无 tenant 的存量行保持 legacy-unresolved | 不建议线上 drop constraint/column；代码镜像可回退且列保留 | 隔离库 update/rollback/reupdate 已通过；线上 changeSet 已 EXECUTED |

两个 changeSet 均含显式 rollback，并已在隔离临时库完成 update/rollback/reupdate。该结果不授权线上 schema drop：生产代码回退仍保留 expand schema，禁止自动删除新增列或唯一约束。若新版本已写入依赖新字段的数据，选择前向修复，不盲目回退代码。

升级库预检必须在部署环境进行并保存只读结果：

```sql
-- 0054 应用后检查唯一性是否仍可证明；任何结果均阻断自动修复。
SELECT tenant_id, platform_data_source_id, COUNT(*)
FROM analytics_database
WHERE tenant_id IS NOT NULL AND platform_data_source_id IS NOT NULL
GROUP BY tenant_id, platform_data_source_id
HAVING COUNT(*) > 1;

-- 存量无归属行不会自动匹配或合并，须记录并按 T13 合同人工处置。
SELECT id, name
FROM analytics_database
WHERE tenant_id IS NULL OR platform_data_source_id IS NULL;
```

通过条件是第一条返回 0 行；第二条仅作为 legacy-unresolved 清单，不可由脚本按名称补 tenant 或 source。迁移前后的 schema、Liquibase 日志、上述 SQL 输出均应进入 evidence。当前 `analytics_database` 为 0 行不单独证明升级路径；已完成的隔离库 update/rollback/reupdate 及线上 EXECUTED 记录见当前正式证据页。

### 2. 开发提交到部署目录

以下命令的目录和顺序已由仓库脚本与 Compose 文件确认；其中 `<release-sha>` 等值必须由本次部署实际生成，不能复用历史 SHA。开发目录绝不执行 build、image 或 Compose：

```bash
# /opt/prod/s10/v2.2.3：仅在 review 通过后执行
git status --short
# 仅提交已审查并明确暂存的 Sprint-104 文件；不得用 git add -A 吞入共享工作树改动。
git diff --cached --check
git diff --cached --name-only
git commit -m 'feat(modeling): sprint104 delivery and analysis consistency'
git push origin HEAD

# /opt/prod/s10/deploy：必须是同一分支的干净独立检出
git status --short --branch
git pull --ff-only
RELEASE_SHA="$(git rev-parse HEAD)"
git show -s --format='%H %D %s' "$RELEASE_SHA"
test -z "$(git status --porcelain)"
```

`builds/dts-build.sh` 的 backend jar 构建使用 `-DskipTests`；所以专项测试须先在 `/opt/prod/s10/deploy` 按本次测试清单完成并把结果记录为单独证据。构建成功不等同于测试、部署或浏览器验收通过。

### 3. 不可变镜像与离线升级包

构建脚本从 `IMGVERSION_FILE` 读取镜像名。不要覆盖 deploy 的常规 `imgversion.conf` 或运行中的 `.env` 的 `1.0.0` 标签；在 release 目录生成仅供本次构建和 Compose 使用的不可变标签文件：

```bash
cd /opt/prod/s10/deploy
RELEASE_ID="s104-${RELEASE_SHA:0:12}"
RELEASE_DIR="data/sprint104-release/${RELEASE_ID}"
mkdir -p "$RELEASE_DIR"
RELEASE_CONF="$RELEASE_DIR/imgversion.conf"
cp imgversion.conf "$RELEASE_CONF"
sed -i -E \
  -e "s|^IMAGE_DTS_PLATFORM=.*|IMAGE_DTS_PLATFORM=dts-platform:${RELEASE_ID}|" \
  -e "s|^IMAGE_DTS_ANALYTICS=.*|IMAGE_DTS_ANALYTICS=dts-analytics:${RELEASE_ID}|" \
  -e "s|^IMAGE_DTS_PLATFORM_WEBAPP=.*|IMAGE_DTS_PLATFORM_WEBAPP=dts-platform-webapp:${RELEASE_ID}|" \
  "$RELEASE_CONF"

DTS_BUILD_EXPECTED_SHA="$RELEASE_SHA" IMGVERSION_FILE="$RELEASE_CONF" \
  builds/dts-build.sh --image dts-platform dts-analytics dts-platform-webapp \
  --opmanager-output "$RELEASE_DIR"

# dts-build.sh 会再次尝试 git pull；HEAD 漂移即废弃本次产物，不得沿用旧 RELEASE_ID。
test "$(git rev-parse HEAD)" = "$RELEASE_SHA"
```

该入口会构建三项镜像，并将**本次调用生成**的 image tar 放入 OpManager 升级包。构建结束后立即记录 SHA、镜像 reference/ID/digest、包路径、archive SHA-256 和每个 image tar SHA-256，命令见 evidence 方案。

构建器从实际 image tar 生成源码 SHA、镜像 ID、标签、架构、archive SHA-256 清单；`checksums.txt` 与 lite upgrader 的 images 目录基准一致，另有部署文件校验。镜像携带源码 revision 标签，`DTS_BUILD_EXPECTED_SHA` 阻断拉取后源码漂移。`e83b51076e21` 已完成包校验和离线 hash/load/plan；`90d111280` 的正式三镜像构建日志为 `/tmp/s104-release-90d111280.log`，已完成包校验与受控部署，结果见正式证据页。

### 4. 受控 Compose 部署与验证

从 `RELEASE_CONF` 生成只覆盖三项镜像的环境文件，保留站点 `.env` 其余配置。先以 `config` 证明 Compose 将使用本次不可变标签；不得运行 `init.sh`、不得修改现网 `.env`、不得重建无关服务：

```bash
RELEASE_ENV="$RELEASE_DIR/compose-release.env"
cp .env "$RELEASE_ENV"
grep -E '^IMAGE_DTS_(PLATFORM|ANALYTICS|PLATFORM_WEBAPP)=' "$RELEASE_CONF" >> "$RELEASE_ENV"

docker compose --env-file "$RELEASE_ENV" -f docker-compose-app.yml config > "$RELEASE_DIR/compose.rendered.yml"
rg 'image: dts-(platform|analytics|platform-webapp):' "$RELEASE_DIR/compose.rendered.yml"

# analytics 先执行其 expand migration；确认 healthy 后才更新 platform，再更新 UI。
docker compose --env-file "$RELEASE_ENV" -f docker-compose-app.yml up -d --no-deps --pull never --force-recreate dts-analytics
docker compose --env-file "$RELEASE_ENV" -f docker-compose-app.yml ps dts-analytics

docker compose --env-file "$RELEASE_ENV" -f docker-compose-app.yml up -d --no-deps --pull never --force-recreate dts-platform
docker compose --env-file "$RELEASE_ENV" -f docker-compose-app.yml ps dts-platform

docker compose --env-file "$RELEASE_ENV" -f docker-compose-app.yml up -d --no-deps --pull never --force-recreate dts-platform-webapp
docker compose --env-file "$RELEASE_ENV" -f docker-compose-app.yml ps dts-platform-webapp
```

在每一步之间等待对应 healthcheck 为 `healthy` 并保存容器 ID、image ID、启动时间和日志。`e83b51076e21` 已按此受控三服务范围部署，三个容器证据见当前正式证据页。若 `dts-platform` 的本次提交改变了 `dts-dbt-runtime-init` 所依赖的镜像内脚本，先在维护窗口单独重跑该 one-shot 服务并确认 `exited (0)`；否则不得用 `--no-deps` 隐式跳过它。Compose health、HTTP 和日志仅证明容器运行，T10–T13 真实模型路径、质量闭环和 Chrome 95 页面验收仍须按 IT-08–IT-18 单独完成。

### 5. 离线交付、回滚和停止条件

离线目标只接收本次 archive、archive SHA-256、三项 image tar SHA-256、`compose-release.env`、源码 SHA 和证据 manifest；先校验 SHA 再 `docker load`。`e83b51076e21` 已完成 archive/hash 校验、三镜像本地 `docker load` 和隔离 target 的 `dts-upgrade-lite plan`，未执行 `apply`。不得下载依赖、使用开发目录 bind mount、`docker cp`、`docker commit` 或容器内热修复。对已有站点先运行包内 `bin/dts-upgrade-lite plan` 审阅差异，再由授权操作员执行 `apply`；`plan` 或 manifest 校验失败即停止。

容器回滚条件是 migration 未破坏兼容性且保留上一版本三项不可变镜像、其 SHA 和上次 Compose env。回滚命令使用上一版本 env 的同一受控三服务范围并保存新旧容器/镜像证据；**不执行 schema drop 或 Liquibase rollback**。运行前必须在隔离/预生产环境演练：升级三服务、核对两项迁移、执行一条 T13 注册失败重试和一条 T12 CAS 冲突、再按上一 release env 回退三服务并确认旧读路径可用。该演练尚未执行，Gate G3 保持 GAP。

停止条件：任一 migration 失败、唯一性预检有重复、legacy 无归属行被自动写入、三服务任一非 healthy、镜像/包 SHA 不一致、或 release manifest 仍为占位且没有随包可验证替代物时，停止部署并保留现状；修复后重新走部署目录正式构建。
