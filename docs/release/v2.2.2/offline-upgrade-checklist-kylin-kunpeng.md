# v2.2.2 离线升级现场执行清单（鲲鹏 / 麒麟 OS）

适用目录示例：

- 旧目录：`/data/stack_old`
- 新目录：`/data/s10_stack`
- 升级包临时目录：`/tmp/dts-upgrade`

legacy 现场已知版本：

- `docker 18.09.0`
- `docker-compose 1.29.2`

要求：

- 现场人工命令统一使用 `docker-compose`
- 升级脚本会自动从 `docker compose` 回退到 `docker-compose`

---

## 1. 停机确认

先确认旧容器已经停掉。

普通模式检查：

```bash
cd /data/stack_old
docker-compose -f docker-compose-app.yml ps
```

legacy 模式检查：

```bash
cd /data/stack_old
docker-compose -f docker-compose.legacy.yml ps
```

要求：

- 不能再有运行中的 DTS 容器

---

## 2. 模式确认

```bash
grep -E '^(LEGACY_STACK|DEPLOY_MODE)=' /data/stack_old/.env
```

判断：

- `LEGACY_STACK=true`：按 legacy 模式处理
- 否则：按 single 模式处理

---

## 3. 镜像 tag 确认

检查旧目录 `.env` 中的镜像变量：

```bash
grep -E '^(IMAGE_DTS_|IMAGE_POSTGRES|IMAGE_DBT|IMAGE_AIRFLOW|IMAGE_ADDAX)=' /data/stack_old/.env
```

检查本机已有镜像：

```bash
docker images | grep -E 'dts-(admin|platform|ingestion|admin-webapp|platform-webapp|analytics|analytics-webapp-modern|dbt|airflow|addax)'
```

要求：

- `stack_old/.env` 中的 `IMAGE_*` 必须和本机这次新镜像 tag 一致

注意：

- 升级器对 `.env` 是旧值优先
- 如果旧目录里还是旧 tag，升级后会继续起旧镜像

---

## 4. PostgreSQL 兼容确认

检查旧数据库版本：

```bash
cat /data/stack_old/services/dts-pg/data/pgdata/PG_VERSION
```

检查新版本目标 PostgreSQL：

```bash
grep '^IMAGE_POSTGRES=' /data/s10_stack/.env /data/s10_stack/imgversion.conf 2>/dev/null
```

要求：

- PostgreSQL 主版本必须一致

例如：

- `17 -> 17` 可以升级
- `16 -> 17` 不允许直接按本清单升级

---

## 5. 准备新版本 `.env`

如果新目录还没有 `.env`：

```bash
cp /data/stack_old/.env /data/s10_stack/.env
```

然后确认：

- 新目录 `.env` 中新增变量已补全
- 新目录 `.env` 中 `IMAGE_*` 为本次目标版本

---

## 6. 生成升级包

如果镜像已经在本机 build 完成，推荐：

```bash
cd /data/s10_stack
./builds/dts-build.sh --pack --no-images --output /tmp/dts-upgrade.tar.gz
```

说明：

- 如果镜像已经手工 `docker load` 或已在本机 build 完成，升级时允许 `images/`、`extra/` 不存在或为空
- 升级器会自动跳过镜像包校验和 `docker load`
- 升级器仍会自动写入 `rollback-manifest.json`

如果需要连镜像 tar 一起打包：

```bash
cd /data/s10_stack
./builds/dts-build.sh --pack --output /tmp/dts-upgrade.tar.gz
```

---

## 7. 确认 `docker-compose` 可用

```bash
docker-compose version
```

要求：

- `docker-compose version` 能输出 `docker-compose 1.29.2` 相关信息
- 如果出现 Python 3.7 deprecation warning，只要退出码是 `0` 可以忽略

---

## 8. 解压升级包

```bash
rm -rf /tmp/dts-upgrade
mkdir -p /tmp/dts-upgrade
tar -xzf /tmp/dts-upgrade.tar.gz -C /tmp/dts-upgrade
```

检查：

```bash
find /tmp/dts-upgrade -maxdepth 2 -type f | sort
```

至少应看到：

- `dts-stack/bin/dts-upgrade`
- `dts-stack/bin/dts-upgrade-rollback`

如果本次走“镜像已预装”模式，则下面两项允许缺失：

- `extra/release-manifest.json`
- `extra/checksums.txt`

---

## 9. 执行升级

```bash
cd /tmp/dts-upgrade/dts-stack
./bin/dts-upgrade \
  --target /data/stack_old \
  --images-dir /tmp/dts-upgrade/images \
  --extra-dir /tmp/dts-upgrade/extra
```

---

## 10. 升级后检查

查看升级日志和摘要：

```bash
ls -1 /data/stack_old/logs/upgrade-*
cat /data/stack_old/logs/upgrade-*.summary.md
```

检查容器：

普通模式：

```bash
cd /data/stack_old
docker-compose -f docker-compose-app.yml ps
```

legacy 模式：

```bash
cd /data/stack_old
docker-compose -f docker-compose.legacy.yml ps
```

检查数据库冷备：

```bash
find /data/stack_old/backups -path '*/services/dts-pg/data' -type d | sort
```

检查项：

- summary 显示成功
- compose 文件选择正确
- 若 `images/` / `extra/` 为空，summary 中应显示镜像包已跳过
- 关键容器已启动
- 旧数据库目录还在
- 冷备目录已生成

---

## 11. 失败回滚

### 11.1 只回滚配置

```bash
cd /tmp/dts-upgrade/dts-stack
./bin/dts-upgrade-rollback \
  --target /data/stack_old \
  --backup-dir /data/stack_old/backups/upgrade-时间戳
```

适用：

- `.env` / compose / `config/` 合并问题
- 主容器启动失败，但数据库目录仍可信

### 11.2 连数据库一起回滚

```bash
cd /tmp/dts-upgrade/dts-stack
./bin/dts-upgrade-rollback \
  --target /data/stack_old \
  --backup-dir /data/stack_old/backups/upgrade-时间戳 \
  --restore-db
```

适用：

- PostgreSQL 启动后异常
- 数据目录被误初始化、误覆盖或不再可信

---

## 12. 最后确认

升级完成后至少再确认一次：

1. 站点可访问
2. 关键容器正常
3. 数据库可连接
4. 业务库和核心表可见
5. `logs/upgrade-*.summary.md` 已保存
6. `backups/upgrade-*` 已生成

---

## 13. 参考文档

完整说明见：

- [offline-upgrade-guide-kylin-kunpeng.md](/opt/prod/s10/s10-stack/docs/release/v2.2.2/offline-upgrade-guide-kylin-kunpeng.md)
