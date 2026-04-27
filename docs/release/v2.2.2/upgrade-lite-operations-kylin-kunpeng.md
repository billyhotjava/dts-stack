# DTS 现场升级操作说明（纯 Shell / legacy）

本文面向麒麟 OS + 鲲鹏 ARM64 现场，用于替代“手动停容器、Portainer 删除容器、手工 load 镜像、手工 `docker-compose up -d`”的流程。

## 适用前提

- 现场运行目录示例：`/data/stack_old`
- 升级包解压目录示例：`/tmp/dts-upgrade`
- 现场使用 legacy 模式：`.env` 中 `LEGACY_STACK=true`
- 现场有 `bash`、`tar`、`diff`、`sha256sum`、`docker`、`docker-compose`
- 不要求现场安装 Python，也不要求安装 Java 升级程序

## 保护原则

- `docker-compose.legacy.yml` 是现场资产，默认不覆盖，只生成差异报告。
- `.env` 中非 `IMAGE_*` 的现场业务配置默认保留。
- `.env` 中 `IMAGE_*` 按新包刷新，避免镜像已导入但仍启动旧 tag。
- `config/` 已有文件不覆盖，差异文件保存到报告目录。
- 运行文件和脚本已有文件不覆盖，缺失文件才补入。
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
  --target /data/stack_old \
  --source /tmp/dts-upgrade/dts-stack \
  --images-dir /tmp/dts-upgrade/images \
  --extra-dir /tmp/dts-upgrade/extra
```

查看报告目录：

```bash
ls -1d /data/stack_old/logs/upgrade-lite-*
cat /data/stack_old/logs/upgrade-lite-*/summary.md
```

现场可打开：

```text
/data/stack_old/logs/upgrade-lite-*/report.html
```

重点确认：

- `mode` 是 `legacy`
- `compose file` 是 `docker-compose.legacy.yml`
- PostgreSQL 主版本兼容
- `env-plan.tsv` 中 `IMAGE_*` 更新符合本次版本
- `compose.diff` 仅作为差异展示，不会覆盖现场 compose
- `risk-list.txt` 中没有无法接受的风险

## 3. 执行升级

确认报告无问题后执行：

```bash
./bin/dts-upgrade-lite apply \
  --target /data/stack_old \
  --source /tmp/dts-upgrade/dts-stack \
  --images-dir /tmp/dts-upgrade/images \
  --extra-dir /tmp/dts-upgrade/extra \
  --yes
```

脚本会自动：

- 校验 checksums（如果提供）
- `docker load` 镜像 tar（如果提供）
- `docker-compose -f docker-compose.legacy.yml down --remove-orphans`
- 冷备 `services/dts-pg/data`
- 更新 `.env` 中 `IMAGE_*`
- 追加新包新增环境变量
- 启动 `docker-compose -f docker-compose.legacy.yml up -d --force-recreate`

## 4. 升级后检查

```bash
cd /data/stack_old
docker-compose -f docker-compose.legacy.yml ps
cat logs/upgrade-lite-*/summary.md
```

确认：

- summary 最后一行包含 `final status: success`
- 关键容器已启动
- `.env` 中 `IMAGE_*` 已更新
- 现场域名、MDM、OIDC、代理、数据库密码等非镜像配置仍保留
- `logs/upgrade-lite-*/backup` 已生成

## 5. 回滚

只回滚配置和新增文件：

```bash
cd /tmp/dts-upgrade/dts-stack
./bin/dts-upgrade-lite rollback \
  --target /data/stack_old \
  --backup-dir /data/stack_old/logs/upgrade-lite-时间戳/backup
```

如果数据库目录也必须恢复，才使用：

```bash
./bin/dts-upgrade-lite rollback \
  --target /data/stack_old \
  --backup-dir /data/stack_old/logs/upgrade-lite-时间戳/backup \
  --restore-db
```

## 6. 常见处理

- `plan` 显示 PostgreSQL 主版本不一致：停止升级，先做数据库迁移方案。
- `apply` 后仍是旧镜像：检查 `image-plan.txt`、`docker load` 记录和 `.env IMAGE_*`。
- MDM 或现场路由异常：优先检查 `docker-compose.legacy.yml` 是否被人工改动；`dts-upgrade-lite` 默认不会覆盖它。
- 需要人工接收新配置：从 `config-conflicts/` 或 `runtime-conflicts/` 中取 `.new` 文件，人工合并后再重启。
