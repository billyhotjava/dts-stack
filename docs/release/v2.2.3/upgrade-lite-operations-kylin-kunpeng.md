# DTS 现场升级操作说明（纯 Shell / legacy）

本文面向麒麟 OS + 鲲鹏 ARM64 现场，用于替代“手动停容器、Portainer 删除容器、手工 load 镜像、手工 `docker-compose up -d`”的流程。

## 目录契约

- 升级目标目录默认为 `/data/dts-stack`（脚本默认值，可省略 `--target`）
- 升级与回滚不会改变目标目录的路径与 inode
- `services/*/data` 持久化数据目录在升级期间不会被脚本写入
- 升级产物：`${target}/logs/upgrade-lite-${timestamp}/`

## 适用前提

- 现场运行目录默认：`/data/dts-stack`（如需覆盖可显式 `--target`）
- 升级包解压目录示例：`/tmp/dts-upgrade`
- 现场使用 legacy 模式：`.env` 中 `LEGACY_STACK=true`
- 现场有 `bash`、`tar`、`diff`、`sha256sum`、`docker`、`docker-compose`
- 不要求现场安装 Python，也不要求安装 Java 升级程序

## 保护原则

- `docker-compose.legacy.yml` 是现场资产，默认不覆盖，只生成差异报告。
- `.env` 中非 `IMAGE_*` 的现场业务配置默认保留。
- `.env` 中 `IMAGE_*` 按新包刷新，避免镜像已导入但仍启动旧 tag。
- `config/` 已有文件不覆盖，差异文件保存到报告目录。
- 产品运行文件（`bin/`、启动脚本、Airflow Python 运行库及静态构建 DAG、dbt 脚本和宏）有变更时先备份再替换，回滚恢复旧版本。现场配置冲突保留原值并生成 `.new` 文件。
- 新版包声明 `runtimeFilesRequired=true`，必须携带并通过 `extra/files-checksums.txt` 校验。镜像校验与运行文件校验在加载镜像、停止容器前执行。
- 数据库目录 `services/dts-pg/data` 只做整目录冷备；回滚数据库必须显式加 `--restore-db`。

## 1. 解压升级包

```bash
rm -rf /tmp/dts-upgrade
mkdir -p /tmp/dts-upgrade
tar -xzf /tmp/dts-upgrade.tar.gz -C /tmp/dts-upgrade
cd /tmp/dts-upgrade/dts-stack
```

确认升级器存在：

```bash
test -x ./bin/dts-upgrade-lite
```

## 2. 生成升级报告

`plan` 不会修改现场 `.env`、compose 或配置文件。

```bash
./bin/dts-upgrade-lite plan \
  --target /data/dts-stack \
  --source /tmp/dts-upgrade/dts-stack \
  --images-dir /tmp/dts-upgrade/images \
  --extra-dir /tmp/dts-upgrade/extra
```

查看报告目录：

```bash
ls -1d /data/dts-stack/logs/upgrade-lite-*
cat /data/dts-stack/logs/upgrade-lite-*/summary.md
```

现场可打开：

```text
/data/dts-stack/logs/upgrade-lite-*/report.html
```

重点确认：

- `mode` 是 `legacy`
- `compose file` 是 `docker-compose.legacy.yml`
- PostgreSQL 主版本兼容
- `env-plan.tsv` 中 `IMAGE_*` 更新符合本次版本
- `compose.diff` 仅作为差异展示，不会覆盖现场 compose
- `risk-list.txt` 中没有无法接受的风险
- `summary.md` 的 Runtime files 列出 `BACKUP_AND_UPDATE`、`ADD`、`KEEP_SITE`，确认包含本次变更的 Airflow 运行脚本。

## 3. 执行升级

确认报告无问题后执行：

```bash
./bin/dts-upgrade-lite apply \
  --target /data/dts-stack \
  --source /tmp/dts-upgrade/dts-stack \
  --images-dir /tmp/dts-upgrade/images \
  --extra-dir /tmp/dts-upgrade/extra \
  --yes
```

脚本会自动：

- 校验镜像 checksums 和完整运行文件 files-checksums；新版包缺少校验清单会停止升级
- `docker load` 镜像 tar（如果提供）
- `docker-compose -f docker-compose.legacy.yml down --remove-orphans`
- 冷备 `services/dts-pg/data`
- 更新 `.env` 中 `IMAGE_*`
- 追加新包新增环境变量
- 备份并替换产品运行脚本，保留现场配置；新增文件记录到回滚清单
- 启动 `docker-compose -f docker-compose.legacy.yml up -d --force-recreate`

## 4. 升级后检查

```bash
cd /data/dts-stack
docker-compose -f docker-compose.legacy.yml ps
cat logs/upgrade-lite-*/summary.md
```

确认：

- summary 最后一行包含 `final status: success`
- 关键容器已启动
- `.env` 中 `IMAGE_*` 已更新
- 现场域名、MDM、OIDC、代理、数据库密码等非镜像配置仍保留
- `logs/upgrade-lite-*/backup` 已生成
- Airflow 脚本与交付包一致；仅更新镜像不能证明绑定挂载脚本已更新

开发、Git 同步、制包和现场升级的完整边界见 [normal-release-and-runtime-update.md](normal-release-and-runtime-update.md)。升级入口必须从新交付包执行，不能继续使用现场旧版本升级脚本。

## 5. 回滚

只回滚配置和新增文件：

```bash
cd /tmp/dts-upgrade/dts-stack
./bin/dts-upgrade-lite rollback \
  --target /data/dts-stack \
  --backup-dir /data/dts-stack/logs/upgrade-lite-时间戳/backup
```

如果数据库目录也必须恢复，才使用：

```bash
./bin/dts-upgrade-lite rollback \
  --target /data/dts-stack \
  --backup-dir /data/dts-stack/logs/upgrade-lite-时间戳/backup \
  --restore-db
```

## 6. 常见处理

- `plan` 显示 PostgreSQL 主版本不一致：停止升级，先做数据库迁移方案。
- `apply` 后仍是旧镜像：检查 `image-plan.txt`、`docker load` 记录和 `.env IMAGE_*`。
- MDM 或现场路由异常：优先检查 `docker-compose.legacy.yml` 是否被人工改动；`dts-upgrade-lite` 默认不会覆盖它。
- 需要人工接收新配置：从 `config-conflicts/` 或 `runtime-conflicts/` 中取 `.new` 文件，人工合并后再重启。
