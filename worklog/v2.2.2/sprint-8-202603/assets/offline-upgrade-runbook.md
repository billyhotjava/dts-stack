# Offline Upgrade Runbook

## 适用范围

- 适用于内网离线交付环境
- 适用于 `single` 与 `legacy` 两种首批支持模式
- 旧目录为现场运行目录，例如 `/data/old-dts`
- 新包目录为升级包解压目录，例如 `/data/update-dts`

## 升级前准备

1. 确认升级包结构完整：
   - `dts-stack/`
   - `images/`
   - `extra/`
2. 确认旧目录中存在 `.env`
3. 确认旧目录 PostgreSQL 数据目录为：
   - `services/dts-pg/data`
4. 确认现场容器已人工停止

## 执行升级

在升级包目录运行：

```bash
cd /data/update-dts/dts-stack
bin/dts-upgrade \
  --target /data/old-dts \
  --images-dir /data/update-dts/images \
  --extra-dir /data/update-dts/extra
```

## 升级成功后检查

1. 查看日志：
   - `/data/old-dts/logs/upgrade-*.log`
   - `/data/old-dts/logs/upgrade-*.summary.md`
2. 检查 `.env`：
   - 现场自定义值仍在
   - 新包新增变量已补入
3. 检查 compose 主文件：
   - `legacy`: 只使用 `docker-compose.legacy.yml`
   - `single`: 使用 `docker-compose.yml` 与 `docker-compose-app.yml`
4. 检查 PostgreSQL：
   - 旧数据目录仍在 `services/dts-pg/data`
   - 冷备目录已生成在 `backups/upgrade-*/services/dts-pg/data`
5. 检查关键容器和业务库可用

## 何时只回滚配置

只回滚配置而不恢复数据库，适用于：

- 配置合并错误
- 镜像导入失败
- 主容器启动失败，但 PostgreSQL 数据目录没有被恢复或重置
- 需要快速回到升级前配置状态

执行方式：

```bash
cd /data/update-dts/dts-stack
bin/dts-upgrade-rollback \
  --target /data/old-dts \
  --backup-dir /data/old-dts/backups/upgrade-时间戳
```

## 何时连数据库一起回滚

以下情况必须连 PostgreSQL 冷备一起回滚：

- PostgreSQL 已启动但 postcheck 失败
- 数据目录被误初始化、误覆盖、误迁移
- 你不再信任当前 `services/dts-pg/data` 的一致性
- 现场需要恢复到升级前完整数据库状态

执行方式：

```bash
cd /data/update-dts/dts-stack
bin/dts-upgrade-rollback \
  --target /data/old-dts \
  --backup-dir /data/old-dts/backups/upgrade-时间戳 \
  --restore-db
```

## 回滚动作说明

`bin/dts-upgrade-rollback` 的动作顺序是：

1. 读取 `backup-dir/rollback-manifest.json`
2. 按当前目录识别 `legacy` / `single`
3. 停掉当前栈
4. 恢复 `backedUpFiles`
5. 若指定 `--restore-db`，恢复 `backedUpDataDirs`
6. 清理 `.upgrade-lock`
7. 按恢复后的模式重新启动
8. 做 postcheck 并输出：
   - `logs/rollback-*.log`
   - `logs/rollback-*.summary.md`

## legacy / normal 重启方式

- `legacy`

```bash
docker compose -f docker-compose.legacy.yml up -d
```

- `single`

```bash
docker compose -f docker-compose.yml -f docker-compose-app.yml up -d
```

## 数据库整目录回滚原则

- `services/dts-pg/data` 不是普通配置目录
- 不做局部文件恢复
- 不做键级合并
- 只允许：
  - 保持当前目录不动
  - 或用冷备整目录恢复

## 关键文件

- 升级日志：`logs/upgrade-*.log`
- 升级摘要：`logs/upgrade-*.summary.md`
- 回滚日志：`logs/rollback-*.log`
- 回滚摘要：`logs/rollback-*.summary.md`
- 升级前冷备：`backups/upgrade-*/services/dts-pg/data`
- 回滚清单：`backups/upgrade-*/rollback-manifest.json`
