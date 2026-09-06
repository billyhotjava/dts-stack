# Sprint-104 F2 发布证据方案与当前记录

本文件保留发布证据的采集方式，并同步截至 2026-09-07 的已执行结果。当前统一入口为 [formal-validation-and-delivery-evidence-20260907.md](../it/evidence/current-environment/formal-validation-and-delivery-evidence-20260907.md)。它不是 Sprint DONE 证明：浏览器登录、完整离线安装目标和实际容器回滚演练尚未完成。

## Release identity

| 字段 | 已记录事实 |
|---|---|
| 已完成构建/部署源码 | `e83b51076e216a2464d5b8703186a7cb93ac723c` |
| 隔离迁移脚本源码 | `3aa077d0c49d48aa0600705a11c3676fb5bf95ce` |
| 当前后续源码 | `90d111280` 已完成三服务正式构建及部署，日志 `/tmp/s104-release-90d111280.log` |
| 已完成 release ID | `s104-e83b51076e21` |
| 构建日志 | `/tmp/s104-release-build.log` |
| 运营 source pin 修复验证 | `90d111280`，44/44 PASS，`/tmp/s104-operational-source-final.log` |

`90d111280` 的 image ID、archive SHA 与部署结果已登记于正式证据页。该版本恢复了运营源快照，随后暴露旧绑定错用候选构建 DAG；运营端到端验收尚未通过。

## 构建和包完整性

`e83b51076e21` 的构建器输出、archive hash、解包 manifest 和 image tar hash 已在正式证据页归档。`package-verification.log` 对已解包内容校验为 `OK`，并含 `release-manifest.json` 与 `rollback-manifest.json`。

| 镜像 | reference | image ID | image tar SHA-256 | 已核验 |
|---|---|---|---|---|
| dts-platform | `dts-platform:s104-e83b51076e21` | `sha256:13c721e4eafd90b8caed743dc93846eaf5c06e1512224a31ff98a307d6fa4447` | `328a1a89467aed33838b6dcc45847b7c5498f8edf9bfc51cc7af2acee6f97ee6` | PASS |
| dts-analytics | `dts-analytics:s104-e83b51076e21` | `sha256:29ed6c9f2c5be11b48b77daeb6216cb5741480bc85d9ed5d613e85123394cd21` | `91ae99be7dd7be5e673b4dbb99bd4ceb07e9ab3a2ef665113e456bfe5775d12e` | PASS |
| dts-platform-webapp | `dts-platform-webapp:s104-e83b51076e21` | `sha256:9dbe565ce3e0eb31768ce594206d00fba896e94dbe567544badc7a65422a526c` | `edd1e17749b852b091f66f900107dbd67756b474a3716d229e46c3675b8a43ca` | PASS |
| OpManager archive | `dts-opmanager-upgrade-20260907-003926.tar.gz` | N/A | `586342cd0fbfa3e7495bf7dab523b8b0a4ce841d522b545e862e18050e7e8473` | PASS |

用于未来 release 的采集命令仍为：

```bash
cd /opt/prod/s10/deploy
RELEASE_DIR="data/sprint104-release/${RELEASE_ID}"
ARCHIVE="$(find "$RELEASE_DIR" -maxdepth 1 -name 'dts-opmanager-upgrade-*.tar.gz' -print -quit)"
test -n "$ARCHIVE"
git rev-parse HEAD | tee "$RELEASE_DIR/source-sha.txt"
sha256sum "$ARCHIVE" | tee "$RELEASE_DIR/archive.sha256"
docker image inspect "dts-platform:${RELEASE_ID}" "dts-analytics:${RELEASE_ID}" \
  "dts-platform-webapp:${RELEASE_ID}" --format '{{index .RepoTags 0}} {{.Id}} {{index .RepoDigests 0}}' \
  | tee "$RELEASE_DIR/image-identities.txt"
```

## 迁移与部署证据

| 检查 | 证据 | 结果 |
|---|---|---|
| platform `20260906-01` 隔离 update/rollback/reupdate | `/tmp/s104-delivery-migrations-3aa077d0.log` | PASS |
| analytics `0054-01` 隔离 update/rollback/reupdate | `/tmp/s104-delivery-migrations-3aa077d0.log` | PASS |
| 线上两个 changeSet | 当前正式证据页：均为 EXECUTED | PASS（执行记录） |
| `analytics_database` 唯一性/legacy-unresolved | 隔离脚本验证唯一约束和保留 legacy 行 | PASS（隔离范围） |
| `e83` 三服务容器替换 | `pre-deployment.json`、`deployed-containers.json` | PASS；仅三个目标容器变化 |
| 当前容器运行状态 | platform、analytics healthy；webapp running、无 healthcheck | PASS（容器运行） |
| 运营 source pin 修复 | `/tmp/s104-operational-source-final.log` | PASS，44/44 |
| IT-08–IT-18、Chrome 95、真实登录模型路径 | 未执行 | GAP |

## 离线和回滚演练

| 演练 | 证据/通过条件 | 状态 |
|---|---|---|
| archive / 三项 image tar SHA 校验 | archive、manifest、package verification 一致 | PASS |
| 离线 `docker load` | 三份 image tar 本地加载后标签和 image ID 与 manifest 相同 | PASS |
| upgrade-lite plan | 隔离 target，未执行 apply/rollback | PASS |
| 离线完整安装目标 | 真实离线站点 `apply` 与运行验证 | GAP |
| 三服务升级 | `e83` analytics → platform → webapp 受控更新，目标容器运行 | PASS（已部署范围） |
| 三服务回退 | 上一 immutable env 回退、schema 保留、旧读路径可用 | 未执行 / GAP |
| 迁移物理 rollback | 隔离库 rollback 已通过；线上不执行 schema drop | PASS（隔离） / 线上不适用 |

`e83` 包的离线预检不等同于离线安装验收；三容器的运行状态不等同于浏览器或真实模型路径验收。Gate G3 保持进行中，直到上述 GAP 逐项获得实际证据。
