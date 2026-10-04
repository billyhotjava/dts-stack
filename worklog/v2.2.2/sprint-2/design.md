# Sprint-2 设计：离线环境一键升级编排

## 背景

当前现场升级流程高度依赖人工：

1. 解压 `dts-deploy.tar.gz` 到新目录
2. 从旧目录手工保留 `services/dts-pg/data`
3. 人工比对 `.env`
4. 人工比对 `docker-compose*.yml`
5. 人工比对 `config/`
6. 人工导入镜像
7. 手工启动新版本

这个流程存在几个现实问题：

- 内网离线环境下不能依赖远程下载补救
- 现场 `.env` 和 compose 往往有大量自定义变量、IP、域名、挂载和端口
- 新包又会引入新的变量、服务和镜像
- 只要人工漏一步，就可能导致升级失败或配置漂移

## 目标

设计并落地一个**离线、自包含、旧配置优先**的一键升级器，使升级流程收敛为：

1. 人工停止旧容器
2. 在新包目录执行 `bin/dts-upgrade`
3. 自动完成校验、备份、镜像导入、配置合并、目录同步、启动与日志输出

## 非目标

- 不做在线升级
- 不做跨主机远程发布
- 不做数据库逻辑迁移工具
- 不自动停止旧容器
- 不把现场所有未知文本格式都做语义级合并

## 升级包结构

升级包解压后约定：

```text
/data/update-dts/
├── dts-stack/
│   ├── bin/
│   ├── builds/
│   ├── config/
│   ├── services/
│   └── ...
├── images/
│   ├── dts-admin-*.tar
│   ├── dts-platform-*.tar
│   └── ...
└── extra/
    ├── release-manifest.json
    ├── merge-rules.yml
    ├── checksums.txt
    └── rollback-manifest.json
```

## 升级入口

执行方式：

```bash
cd /data/update-dts/dts-stack
bin/dts-upgrade \
  --target /data/old-dts \
  --images-dir /data/update-dts/images \
  --extra-dir /data/update-dts/extra
```

## 核心原则

### 1. 离线自足

升级器只允许使用：

- 本地升级包
- 本地旧目录
- `docker load`
- `docker compose`
- 本地文件复制、备份、合并与日志记录

严格禁止：

- `docker pull`
- `curl`
- `wget`
- `apt`
- 任何运行时依赖下载

### 2. 旧配置优先

升级规则不是“新包覆盖旧目录”，而是：

- **旧目录是运行事实**
- **新包是结构和新增项来源**

也就是：

- 旧值优先保留
- 新值只补缺，不盲目覆盖
- 冲突时按规则自动选旧值并记日志

### 3. 可回滚

升级器在真正覆盖前，必须先生成：

- 升级锁
- 关键文件备份
- 回滚清单

## 合并规则

### `.env`

- 旧值优先
- 新包新增 key 自动补入
- 新旧同 key 不同值时保留旧值
- 新包缺失而旧环境存在的 key 不删除
- 所有差异写入升级日志和 summary

### `docker-compose*.yml`

- 以新包文件作为结构基线
- 自动保留旧环境中的高风险现场覆盖项：
  - `environment`
  - `ports`
  - `volumes`
  - `extra_hosts`
  - `hostname`
  - `container_name`
  - `labels`
  - `networks` 下的自定义地址和别名
- 新包新增 service / volume / network 自动补入
- 新包删除的旧 service 默认不自动删，仅记录为“待人工确认遗留项”

### `config/`

- 已知结构化文件：
  - `.yml`
  - `.yaml`
  - `.json`
  - `.properties`
  做键级合并，旧值优先
- 非结构化或不识别格式：
  - 保留旧版为主
  - 将新版落到冲突备份目录
  - 记录到 summary

### 运行数据目录

这些目录不覆盖、不迁移，只做存在性与权限校验：

- `services/dts-pg/data`
- `logs/`
- 其他持久化目录

## 升级状态机

```text
PRECHECK
-> VERIFY_OFFLINE_PACKAGE
-> VERIFY_OLD_CONTAINERS_STOPPED
-> ACQUIRE_LOCK
-> BACKUP
-> LOAD_IMAGES
-> MERGE_ENV
-> MERGE_COMPOSE
-> MERGE_CONFIG
-> SYNC_NEW_FILES
-> WRITE_SUMMARY
-> START_CONTAINERS
-> POSTCHECK
-> DONE
```

失败处理：

- `PRECHECK` 到 `LOAD_IMAGES` 失败：不落盘覆盖，直接退出
- `MERGE_*` 失败：保留备份，退出，要求人工处理
- `START_CONTAINERS` 失败：输出失败摘要和回滚建议，不自动回滚运行数据

## 日志与备份

旧目录内新增：

```text
/data/old-dts/logs/upgrade-YYYYmmdd-HHMMSS.log
/data/old-dts/logs/upgrade-YYYYmmdd-HHMMSS.summary.md
/data/old-dts/backups/upgrade-YYYYmmdd-HHMMSS/
/data/old-dts/.upgrade-lock
```

## 风险

1. YAML 结构合并规则不够稳，可能误保留旧值导致新服务缺少关键字段
2. 现场目录可能存在额外自定义文件，升级器不能假设旧目录绝对标准化
3. 镜像 tar 缺失时，离线环境没有任何补救路径，只能失败退出
4. 若旧 compose 中存在大量历史遗留 service，默认保留可能继续带来配置噪声

## 推荐分期

### Phase 1
- 升级器入口
- 离线校验
- 镜像导入
- `.env` 合并
- compose 合并
- 日志/备份/回滚清单

### Phase 2
- `config/` 深度结构化合并
- 更细粒度 postcheck
- 历史遗留 service 清理建议器
