# v2.2.3 离线平滑升级说明（鲲鹏 / 麒麟 OS）

## 1. 适用场景

本文档面向现场运维人员，适用于以下场景：

- 操作系统为麒麟 OS
- 服务器为鲲鹏架构
- 目标环境为内网离线环境
- 旧版本运行目录为 `stack_old`
- 新版本源码目录为 `s10_stack`
- 新版本代码已经在 `s10_stack` 中完成拉取
- 新版本应用镜像已经在当前服务器本地重新编译完成

本文档只说明 **原目录就地升级**，不说明新机部署。

现场执行时可优先使用更短的操作手册：

- [upgrade-lite-operations-kylin-kunpeng.md](upgrade-lite-operations-kylin-kunpeng.md)

当前 legacy 现场已知版本如下：

- `docker`：`18.09.0`
- `docker-compose`：`1.29.2`
- 架构：`linux/arm64`

这意味着：

- 现场人工运维命令统一使用 `docker-compose`
- `docker compose` 子命令在该环境下默认不可用
- `dts-upgrade-lite` 会优先使用 `docker-compose`，若环境提供新版插件再使用 `docker compose`
- 推荐使用 `bin/dts-upgrade-lite`，该脚本为纯 shell 实现，不依赖现场 Python

---

## 2. 升级原则

本次升级的原则如下：

1. 以旧目录 `stack_old` 为升级目标目录，不新建运行目录。
2. 以新目录 `s10_stack` 作为升级包来源和升级脚本来源。
3. 旧目录 `.env` 中的现场自定义配置优先保留。
4. `.env` 中 `IMAGE_*` 会按新包刷新，避免镜像已 load 但仍启动旧 tag。
5. 新版本新增的环境变量会从新目录补入旧目录。
6. 现场 `docker-compose.legacy.yml` 默认不覆盖，只生成差异报告。
7. `services/dts-pg/data` 是数据库状态目录，不允许被新包覆盖。
8. 如果 PostgreSQL 主版本不兼容，升级会直接阻断。
9. `legacy` 模式和普通 `single` 模式使用不同的 compose 主文件。

---

## 3. 目录约定

以下命令以这组目录为例：

- 旧版本目录：`/data/stack_old`
- 新版本目录：`/data/s10_stack`
- 升级包临时解压目录：`/tmp/dts-upgrade`

如果你的实际目录不同，请替换成现场真实路径。

---

## 4. 升级前检查

### 4.1 确认当前容器状态

先进入旧目录：

```bash
cd /data/stack_old
```

如果是普通模式，检查：

```bash
docker-compose -f docker-compose-app.yml ps
```

如果是 legacy 模式，检查：

```bash
docker-compose -f docker-compose.legacy.yml ps
```

要求：

- `plan` 阶段允许容器仍在运行
- `apply` 阶段会由 `dts-upgrade-lite` 自动执行 `docker-compose down` 和 `up -d --force-recreate`

### 4.2 确认旧目录模式

执行：

```bash
grep -E '^(LEGACY_STACK|DEPLOY_MODE)=' /data/stack_old/.env
```

判断规则：

- 如果 `LEGACY_STACK=true`，按 **legacy 模式** 升级
- 否则按 **single 模式** 升级

### 4.3 确认镜像标签来源

旧目录 `.env` 中通常包含镜像变量：

```bash
grep -E '^(IMAGE_DTS_|IMAGE_POSTGRES|IMAGE_DBT|IMAGE_AIRFLOW|IMAGE_ADDAX)=' /data/stack_old/.env
```

再检查当前服务器本地已有镜像：

```bash
docker images | grep -E 'dts-(admin|platform|ingestion|admin-webapp|platform-webapp|analytics|analytics-webapp-modern|dbt|airflow|addax)'
```

必须确认新包里的 `.env` 或 `imgversion.conf` 包含本次目标 `IMAGE_*`。

注意：

- `dts-upgrade-lite apply` 会刷新旧目录 `.env` 中的 `IMAGE_*`
- 非 `IMAGE_*` 的现场业务配置仍默认保留现场值

### 4.4 确认 PostgreSQL 主版本兼容

检查旧数据库目录版本：

```bash
cat /data/stack_old/services/dts-pg/data/pgdata/PG_VERSION
```

检查新版本目标 PostgreSQL 镜像：

```bash
grep '^IMAGE_POSTGRES=' /data/s10_stack/.env /data/s10_stack/imgversion.conf 2>/dev/null
```

要求：

- PostgreSQL 主版本必须一致

例如：

- `17 -> 17`：允许升级
- `16 -> 17`：不允许直接走本手册，必须先走数据库迁移方案

---

## 5. 升级前准备

### 5.1 准备新版本 `.env`

如果新目录中还没有 `.env`，先复制一份旧目录：

```bash
cp /data/stack_old/.env /data/s10_stack/.env
```

然后根据新版本要求，确认新目录 `.env` 中包含：

- 新版本新增的环境变量
- 正确的 `IMAGE_*` 镜像 tag

说明：

- 升级时会用 `s10_stack/.env` 补充新增键
- 升级时会保留 `stack_old/.env` 中已有现场值

### 5.2 选择升级包方式

如果你已经在本机完成了镜像构建，并且这些镜像已经存在于本机 `docker images` 中，推荐使用：

```bash
cd /data/s10_stack
./builds/dts-build.sh --pack --no-images --output /tmp/dts-upgrade.tar.gz
```

这种方式的特点是：

- 不再打包镜像 tar
- 升级时直接使用本机已有镜像
- 即使 `/tmp/dts-upgrade/images` 或 `/tmp/dts-upgrade/extra` 不存在，升级脚本也会自动创建目录并继续执行

如果你希望生成完整离线包，包含镜像 tar，则使用：

```bash
cd /data/s10_stack
./builds/dts-build.sh --pack --output /tmp/dts-upgrade.tar.gz
```

补充说明：

- 如果镜像已经手工 `docker load` 到本机，或本机已经通过 `docker build` 生成所需镜像，那么升级时允许：
  - `images/` 目录不存在
  - `images/` 目录为空
  - `extra/` 目录不存在
  - `extra/` 目录为空
- 在这种情况下，升级器会自动进入“镜像已预装”模式：
  - 跳过 `release-manifest.json` / `checksums.txt` 校验
  - 跳过 `docker load`
  - 仍会生成 `logs/upgrade-lite-*/summary.md`、`report.html` 和 `backup/`

### 5.3 确认脚本兼容 `docker-compose`

`dts-upgrade-lite` 已经支持：

1. 优先使用现场常见的 `docker-compose`
2. 如果没有 `docker-compose`，再尝试 `docker compose`

所以在这台 legacy 现场，不再需要手工准备兼容包装器。

只需要确认下面命令可用：

```bash
docker-compose version
```

要求：

- 能输出 `docker-compose version 1.29.2` 相关信息

说明：

- 若命令输出 Python 3.7 / cryptography deprecation warning，只要退出码为 `0` 可以忽略

---

## 6. 解压升级包

```bash
rm -rf /tmp/dts-upgrade
mkdir -p /tmp/dts-upgrade
tar -xzf /tmp/dts-upgrade.tar.gz -C /tmp/dts-upgrade
```

解压后必须看到：

- `/tmp/dts-upgrade/dts-stack`
- `/tmp/dts-upgrade/images`（可为空）
- `/tmp/dts-upgrade/extra`（可为空）

检查：

```bash
find /tmp/dts-upgrade -maxdepth 2 -type f | sort
```

至少应包含：

- `dts-stack/bin/dts-upgrade-lite`
- `dts-stack/bin/dts-upgrade`
- `dts-stack/bin/dts-upgrade-rollback`

如果你这次采用的是“镜像已预装”模式，则下面两项允许缺失：

- `extra/release-manifest.json`
- `extra/checksums.txt`

---

## 7. 执行升级

进入升级包目录：

```bash
cd /tmp/dts-upgrade/dts-stack
```

先生成升级报告。该步骤只写入 `/data/stack_old/logs/upgrade-lite-*` 报告目录，不修改 `.env`、compose 或配置文件：

```bash
./bin/dts-upgrade-lite plan \
  --target /data/stack_old \
  --source /tmp/dts-upgrade/dts-stack \
  --images-dir /tmp/dts-upgrade/images \
  --extra-dir /tmp/dts-upgrade/extra
```

重点查看：

```bash
ls -1d /data/stack_old/logs/upgrade-lite-*
```

可在现场浏览器打开 `report.html`，重点确认：

- `docker-compose.legacy.yml` 差异
- `.env` 中 `IMAGE_*` 更新计划
- 现场自定义 key 的保留情况
- `config/` 和运行文件冲突清单
- PostgreSQL 主版本兼容性

确认后执行升级：

```bash
./bin/dts-upgrade-lite apply \
  --target /data/stack_old \
  --source /tmp/dts-upgrade/dts-stack \
  --images-dir /tmp/dts-upgrade/images \
  --extra-dir /tmp/dts-upgrade/extra \
  --yes
```

lite 升级器会自动执行以下动作：

1. 识别 `legacy` / `single` 模式
2. 检查 PostgreSQL 主版本兼容
3. 创建升级锁
4. 校验 checksums（如果提供）
5. 导入镜像（如果 `images/` 中有镜像 tar）
6. 自动停止旧容器
7. 备份 `.env` 并冷备 `services/dts-pg/data`
8. 刷新 `.env` 中 `IMAGE_*`，追加新包新增 key，保留现场业务 key
9. 保留现场 `docker-compose.legacy.yml`，只生成 diff 报告，不覆盖
10. `config/` 已有文件不覆盖，差异另存到报告目录；缺失文件才补入
11. 运行文件和脚本只补缺失文件，已有文件不覆盖
12. 使用 `docker-compose -f docker-compose.legacy.yml up -d --force-recreate` 启动
13. 执行 postcheck 并生成静态 HTML 报告

---

## 8. 升级后验证

### 8.1 查看升级日志

```bash
ls -1d /data/stack_old/logs/upgrade-lite-*
```

重点查看：

```bash
cat /data/stack_old/logs/upgrade-lite-*/summary.md
```

必须重点确认：

- 本次识别到的运行模式
- 本次实际使用的 compose 文件
- PostgreSQL 兼容检查结果
- 冷备目录位置
- 如果本次未提供 `images/` / `extra/manifest`，summary 中会显示镜像包已跳过
- 最终状态为成功

### 8.2 检查容器状态

如果是普通模式：

```bash
cd /data/stack_old
docker-compose -f docker-compose-app.yml ps
```

如果是 legacy 模式：

```bash
cd /data/stack_old
docker-compose -f docker-compose.legacy.yml ps
```

要求：

- 关键 DTS 容器已启动
- 关键容器状态正常

### 8.3 检查 `.env`

```bash
grep -E '^(BASE_DOMAIN|HOST_|IMAGE_DTS_|IMAGE_POSTGRES|IMAGE_DBT|IMAGE_AIRFLOW)=' /data/stack_old/.env
```

要求：

- 现场域名、IP、账号等自定义值仍然存在
- 新版本新增环境变量已经补入
- 镜像 tag 为本次目标版本

### 8.4 检查数据库目录

```bash
ls -ld /data/stack_old/services/dts-pg/data
find /data/stack_old/backups -path '*/services/dts-pg/data' -type d | sort
```

要求：

- 旧数据库目录仍然存在
- 升级前冷备目录已经生成

### 8.5 检查业务可用性

至少检查：

- 平台首页可访问
- 管理端可访问
- PostgreSQL 可连接
- 关键业务库和核心表可见

---

## 9. 回滚说明

### 9.1 何时只回滚配置

以下场景可以只回滚配置：

- `.env` 合并错误
- compose 合并错误
- `config/` 合并错误
- 主容器未正常启动，但数据库目录未被破坏

执行：

```bash
cd /tmp/dts-upgrade/dts-stack
./bin/dts-upgrade-lite rollback \
  --target /data/stack_old \
  --backup-dir /data/stack_old/logs/upgrade-lite-时间戳/backup
```

### 9.2 何时必须连数据库一起回滚

以下场景必须使用数据库整目录回滚：

- PostgreSQL 启动后 postcheck 失败
- 数据目录被误初始化
- 数据目录被误覆盖或误迁移
- 你不能再信任当前 `services/dts-pg/data`

执行：

```bash
cd /tmp/dts-upgrade/dts-stack
./bin/dts-upgrade-lite rollback \
  --target /data/stack_old \
  --backup-dir /data/stack_old/logs/upgrade-lite-时间戳/backup \
  --restore-db
```

### 9.3 数据库整目录回滚原则

数据库目录：

- `/data/stack_old/services/dts-pg/data`

必须按整目录恢复，原因是 PostgreSQL 数据目录内部文件是强一致整体，不能按普通配置文件逐个拼接恢复。

允许的方式只有两种：

1. 保持当前数据库目录不动
2. 用升级前冷备整目录恢复

禁止的方式：

- 只恢复部分数据库文件
- 手工拼接 `pgdata` 内部目录
- 把新包里的 `services/dts-pg/data` 骨架复制到旧目录覆盖现场数据

---

## 10. 常见问题

### 10.1 升级后为什么还是旧镜像

常见原因：

- 新包 `.env` / `imgversion.conf` 中的 `IMAGE_*` 不是本次目标 tag
- 容器没有被重建
- 本机没有成功 `docker load` 对应镜像 tar

处理方式：

- 检查 `upgrade-lite-*/image-plan.txt`
- 检查 `upgrade-lite-*/summary.md` 中的 `docker load` 记录
- 确认启动命令包含 `up -d --force-recreate`

### 10.2 为什么 legacy 环境不能用普通 compose 启动

因为 legacy 模式的运行态主文件是：

- `docker-compose.legacy.yml`

不能在现场重新按：

- `docker-compose-app.yml`

去拼装替代。

另外，在这台 legacy 现场上，人工命令应统一使用：

- `docker-compose`

而不是：

- `docker compose`

因为当前 Docker 版本为 `18.09.0`，不自带 compose plugin。

### 10.3 为什么数据库不能直接覆盖

因为：

- `services/dts-pg/data` 是 PostgreSQL 运行状态目录
- 不是普通配置目录
- 必须按整目录冷备和整目录恢复处理

---

## 11. 推荐执行顺序

现场建议严格按下面顺序执行：

1. 在 `s10_stack` 生成升级包
2. 解压升级包
3. 确认 `stack_old/.env` 中 `LEGACY_STACK` / `DEPLOY_MODE`
4. 执行 `bin/dts-upgrade-lite plan`，打开 `report.html` 查看差异
5. 确认 PostgreSQL 主版本兼容、compose 差异和 `.env` 镜像更新计划
6. 执行 `bin/dts-upgrade-lite apply --yes`
7. 检查 `upgrade-lite-*/summary.md` 和 `report.html`
8. 检查容器状态
9. 检查业务可用性
10. 如失败，按情况执行配置回滚或数据库整目录回滚

---

## 12. 相关文件

- 新版本源码目录：`/data/s10_stack`
- 旧版本运行目录：`/data/stack_old`
- lite 升级脚本：`/tmp/dts-upgrade/dts-stack/bin/dts-upgrade-lite`
- 兼容旧升级脚本：`/tmp/dts-upgrade/dts-stack/bin/dts-upgrade`
- lite 报告：`/data/stack_old/logs/upgrade-lite-*/report.html`
- lite 摘要：`/data/stack_old/logs/upgrade-lite-*/summary.md`
- lite 回滚备份目录：`/data/stack_old/logs/upgrade-lite-*/backup`
