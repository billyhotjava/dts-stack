# Sprint-104 F2 发布证据方案（待执行）

本文件是 `release-plan.md` 的证据记录模板。它只列出已经由 deploy 脚本和 Compose 核实的命令及待填写证据；所有 `TODO` 都是 GAP，不可改成 PASS。

## Release identity

| 字段 | 实际值 |
|---|---|
| 源码分支 / commit SHA | TODO |
| deploy checkout `git status --short --branch` | TODO |
| 构建主机 / 架构 / Docker 版本 | TODO |
| `RELEASE_ID` | TODO (`s104-<sha12>`) |
| 构建起止时间 | TODO |
| 构建日志路径 | TODO |

## 构建和包完整性

在 `/opt/prod/s10/deploy` 且三项镜像均已按 `release-plan.md` 构建后执行。以下是证据采集命令，不会修改运行容器：

```bash
cd /opt/prod/s10/deploy
RELEASE_DIR="data/sprint104-release/${RELEASE_ID}"
ARCHIVE="$(find "$RELEASE_DIR" -maxdepth 1 -name 'dts-opmanager-upgrade-*.tar.gz' -print -quit)"
test -n "$ARCHIVE"

git rev-parse HEAD | tee "$RELEASE_DIR/source-sha.txt"
sha256sum "$ARCHIVE" | tee "$RELEASE_DIR/archive.sha256"
docker image inspect \
  "dts-platform:${RELEASE_ID}" \
  "dts-analytics:${RELEASE_ID}" \
  "dts-platform-webapp:${RELEASE_ID}" \
  --format '{{index .RepoTags 0}} {{.Id}} {{index .RepoDigests 0}}' \
  | tee "$RELEASE_DIR/image-identities.txt"

STAGE="$RELEASE_DIR/package-inspect"
rm -rf "$STAGE"
mkdir -p "$STAGE"
tar -xzf "$ARCHIVE" -C "$STAGE"
sha256sum "$STAGE"/images/*.tar | tee "$RELEASE_DIR/image-tars.sha256"
tar -tzf "$ARCHIVE" | tee "$RELEASE_DIR/archive-files.txt"
```

`RepoDigests` 对本地 docker build 可能为空；此时记录 image ID 和 image tar SHA-256，不伪造 registry digest。确认 archive 内只有本次三项 image tar 后，填写下表。

| 镜像 | reference | image ID / digest | image tar SHA-256 | 已核验 |
|---|---|---|---|---|
| dts-platform | TODO | TODO | TODO | TODO |
| dts-analytics | TODO | TODO | TODO | TODO |
| dts-platform-webapp | TODO | TODO | TODO | TODO |
| OpManager archive | TODO | N/A | TODO | TODO |

## 迁移与部署证据

| 检查 | 证据文件/命令输出 | 结果 |
|---|---|---|
| 清洁库：platform `20260906-01` | TODO | GAP |
| 清洁库：analytics `0054-01` | TODO | GAP |
| 升级库：两个 changeSet 完成 | TODO | GAP |
| analytics 唯一性和 legacy-unresolved SQL | TODO | GAP |
| analytics health 与日志 | TODO | GAP |
| platform health、Liquibase 日志与服务调用失败码 | TODO | GAP |
| webapp image/container identity | TODO | GAP |
| IT-08–IT-18 实际页面和模型路径 | TODO | GAP |

部署前后分别采集：

```bash
docker compose --env-file "$RELEASE_ENV" -f docker-compose-app.yml ps \
  dts-analytics dts-platform dts-platform-webapp \
  | tee "$RELEASE_DIR/compose-ps.txt"
docker inspect dts-analytics dts-platform dts-platform-webapp \
  --format '{{.Name}} {{.Image}} {{.State.Status}} {{.State.Health.Status}} {{.State.StartedAt}}' \
  | tee "$RELEASE_DIR/container-identities.txt"
```

容器名称和 Compose project name 如现场不同，先用 `docker compose ... ps -q` 解析 ID，再做 inspect；不得猜测或批量操作其他服务。

## 离线和回滚演练

| 演练 | 通过条件 | 状态 |
|---|---|---|
| 离线 archive / 三项 image tar SHA 校验 | 与本文件记录逐字匹配 | 未执行 |
| 离线 `docker load` | 三项不可变 reference 可 inspect | 未执行 |
| upgrade-lite plan | 无未批准的 env/compose/volume 差异 | 未执行 |
| 三服务升级 | analytics → platform → webapp 均 healthy | 未执行 |
| 三服务回退 | 用上次 immutable env 回退，schema 保留，旧读路径可用 | 未执行 |
| 迁移物理 rollback | 不执行；两个 changeSet 无 rollback 段 | N/A / GAP |

当前构建器将 `extra/release-manifest.json` 写成占位值，并产生空 `extra/checksums.txt`。在该问题修复或替代 manifest 与 archive 一起交付并完成离线校验前，离线交付完整性为 **GAP**。
