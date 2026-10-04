# Offline Upgrade Runbook

## 1. 适用范围

本手册适用于 **内网离线环境** 下的 DTS 原目录就地升级：

- 旧目录示例：`/data/old-dts`
- 升级包解压目录示例：`/data/update-dts`
- 升级命令从新包内的 `dts-stack/` 执行

当前脚本入口：

```bash
cd /data/update-dts/dts-stack
bin/dts-upgrade \
  --target /data/old-dts \
  --images-dir /data/update-dts/images \
  --extra-dir /data/update-dts/extra
```

## 2. 升级前检查

执行升级前，先确认：

1. 旧目录存在，并且是正在运行的 DTS 安装目录。
2. 升级包目录完整，至少包含：
   - `dts-stack/`
   - `images/`
   - `extra/release-manifest.json`
   - `extra/checksums.txt`
3. 旧目录中关键运行数据已确认存在：
   - `services/dts-pg/data`
4. 目标主机磁盘空间足够容纳：
   - 升级日志
   - 备份目录
   - 本地镜像导入

## 3. 停机要求

升级器 **不会自动停止旧容器**。升级前必须人工停机。

推荐命令：

```bash
cd /data/old-dts
./stop.sh
```

如果现场不是通过 `stop.sh` 停机，也必须保证旧部署的 compose 服务已经全部停止。  
`dts-upgrade` 会在预检查阶段再次校验，若还有运行中容器会直接失败退出。

## 4. 升级执行

在升级包目录执行：

```bash
cd /data/update-dts/dts-stack
bin/dts-upgrade \
  --target /data/old-dts \
  --images-dir /data/update-dts/images \
  --extra-dir /data/update-dts/extra
```

当前升级器会依次执行这些状态：

```text
PRECHECK
VERIFY_OFFLINE_PACKAGE
VERIFY_OLD_CONTAINERS_STOPPED
BACKUP
LOAD_IMAGES
MERGE_ENV
MERGE_COMPOSE
MERGE_CONFIG
SYNC_NEW_FILES
WRITE_SUMMARY
START_CONTAINERS
POSTCHECK
```

## 5. 升级成功校验

升级成功后，至少检查以下内容：

1. 目标目录 `logs/` 下生成了本次升级摘要：
   - `logs/upgrade-YYYYmmdd-HHMMSS.summary.md`
2. 摘要中包含：
   - `- final status: success`
   - `- state START_CONTAINERS`
   - `- state POSTCHECK`
3. 目标目录 `backups/upgrade-*/` 已生成。
4. `extra/rollback-manifest.json` 已被本次升级运行结果覆盖。
5. 关键运行数据仍在原位，例如：
   - `services/dts-pg/data`
6. 新容器已经被拉起，且 postcheck 能看到 running services。

## 6. 日志与产物位置

升级运行后，需要重点关注这些位置：

### 旧目录内

- 升级主日志：
  - `logs/upgrade-YYYYmmdd-HHMMSS.log`
- 升级摘要：
  - `logs/upgrade-YYYYmmdd-HHMMSS.summary.md`
- 升级备份目录：
  - `backups/upgrade-YYYYmmdd-HHMMSS/`
- 升级锁：
  - `.upgrade-lock`

### 升级包内

- 离线镜像清单：
  - `extra/release-manifest.json`
- 完整性校验：
  - `extra/checksums.txt`
- 本次升级回滚清单：
  - `extra/rollback-manifest.json`

## 7. 回滚与失败处理

如果升级失败，不自动回滚运行数据。建议按以下顺序处理：

1. 打开失败摘要和主日志，确认失败状态。
2. 查看：
   - `logs/upgrade-*.summary.md`
   - `logs/upgrade-*.log`
3. 根据 `backups/upgrade-*/rollback-manifest.json` 或 `extra/rollback-manifest.json`，确认：
   - 哪些文件被备份
   - 哪些数据目录被保护未覆盖
4. 如需人工回滚，优先恢复：
   - `.env`
   - `docker-compose*.yml`
   - `config/` 下被替换的文件
5. `services/dts-pg/data` 默认应保持原位，不做覆盖式回滚。

## 8. 当前实现边界

当前版本已支持：

- 离线镜像校验与导入
- `.env` 旧值优先合并
- `docker-compose*.yml` 规则合并
- `config/` 中 `.properties/.json/.yml/.yaml` 的基础合并
- 未识别配置文件的冲突备份
- 备份目录与回滚清单
- 启动与最小 postcheck

当前仍建议人工重点复核：

- 复杂 YAML 配置的语义是否完全符合现场预期
- compose 中历史遗留 service 是否需要后续清理
- 升级后业务侧功能验证

## 9. 现场验收清单

- [ ] 旧容器已人工停止
- [ ] 升级命令执行完成且退出码为 0
- [ ] `logs/upgrade-*.summary.md` 显示 `final status: success`
- [ ] `backups/upgrade-*/` 已生成
- [ ] `extra/rollback-manifest.json` 已生成最新内容
- [ ] `services/dts-pg/data` 仍保留原始数据
- [ ] 新容器已启动并通过 postcheck
- [ ] `.env` 中现场自定义变量仍然保留
- [ ] `docker-compose*.yml` 中现场端口、卷、主机名等关键覆盖项仍然保留
- [ ] `config/` 中现场定制配置已确认无误
