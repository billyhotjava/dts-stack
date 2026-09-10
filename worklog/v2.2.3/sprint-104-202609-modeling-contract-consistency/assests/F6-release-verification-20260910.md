# F6 正式交付与运行验收记录

状态：正式构建、交付包、局部部署及基础探针通过；业务验收待登录与浏览器。不据此关闭 F6、T39 或 IT-36–IT-43。

## 基线与发布边界

- 正式构建提交：`caa4203793fc6c64b37e14eeee3c63a648a2eda2`。开发、部署目录均已核对；部署目录 `git pull --ff-only`，工作区干净。
- 构建目录：`/opt/prod/s10/deploy`。正式入口 `bash builds/dts-build.sh --image dts-admin dts-platform dts-analytics dts-platform-webapp --opmanager-output /opt/prod/s10/deploy/builds/opmanager-dist/f6-caa420379.tar.gz`。
- 现有运行目录 `/opt/dts/release/dts-stack`，inode `36176097`，项目名 `dts-stack`，配置 `docker-compose-app.yml`。保留现有挂载、数据和配置，不迁移环境。
- 四个旧镜像已保留 `<service>:f6-rollback-caa420379` 标签；精确 ID 记录于本机 `/tmp/f6-release-evidence/before-images.json`。
- 正式升级器会停止整个 Compose 栈。须先核对包内升级计划；若运行文件和配置没有额外依赖变化，可按正式运维文档的局部更新流程，仅重建相关服务。

## 迁移与回退

- 本次为 expand：新增可空字段、指标服务投影表和待同步索引；不删除旧列、历史版本或运行记录。
- 升级前只读检查：三项 `20260910-01/02/03` 指标迁移均已执行；指标定义、版本、运行记录各 0 条。未向业务数据库注入测试数据。
- `IndicatorImplementationRefUpgradeTest` 在隔离 PostgreSQL 17.6 执行：3 项通过，0 失败、0 错误、0 跳过；覆盖 fresh、legacy、expanded 和重复迁移。
- 首次宿主机 Maven 因 Docker 生成文件的权限失败，测试未运行。随后在正式 Maven 镜像 `maven:3.9.9-eclipse-temurin-21` 内，取得同一工作区锁后补跑成功；未修改目录权限。
- 回退保留新增结构和历史数据，优先恢复旧应用镜像；不得通过删除指标表或清空业务数据处理失败。应用回退演练尚未执行，不把迁移兼容测试等同于回退演练。

## 交付包与局部部署

- 正式构建退出码 0；包大小约 794 MB，包内实际包含四个 amd64 镜像归档，均标记上述同一提交。
- 包 SHA256：`0f8f339004a37c1529f81cfd46865d3d08ef26460398f850975ac4a72f9443ae`。
- 包内 `misc/files-checksums.txt` 和 `misc/checksums.txt` 校验通过；从包逐一 `docker load`，镜像 ID 与清单逐一相符。
- 新旧镜像 ID、镜像归档校验和及提交见 `F6-release-images-20260910.json`。这是已有环境的四服务增量包，不代表可在空白主机独立安装全栈。
- 包内升级器 plan 通过：`/opt/dts/release/dts-stack/logs/upgrade-lite-20260910-133802/report.html`。数据库主版本 17→17，Compose 无差异，运行文件仅 `.env.template` 为 KEEP_SITE；`services/dts-dbt/run-model-build.sh` 与现场一致。
- 按 `docs/release/v2.2.3/normal-release-and-runtime-update.md` 的局部更新路径，沿用原项目、`.env`、Compose，通过 `up -d --no-deps --force-recreate --pull never` 先更新 admin、analytics，再更新 platform、platform-webapp。未更新其他运行服务、挂载文件或环境变量。
- 更新前逻辑备份：`/opt/dts/backups/f6-caa420379/pre-upgrade.sql`，受限目录中保存且文件非空；尚未进行备份恢复演练。
- 四个运行容器 image ID 均已与包清单核对。admin、analytics、platform 最终均 healthy；platform-webapp running，页面入口 HTTP 200。运行目录 inode 仍为 `36176097`。
- 部署后会话入口 HTTP 200，未登录指标查询 `POST /api/governance/indicators/query` 返回 HTTP 401。只验证未登录拦截，不代表角色授权/撤权矩阵通过。
- 部署后只读复查三项指标迁移仍为 EXECUTED，定义和运行记录仍各 0 条。没有通过数据库修补业务功能。

## 待完成验收

- 真实业务 API、固定版本双卡片、权限撤销和 Chrome95 页面矩阵。
- 浏览器工具当前返回 `apps=[]、browsers=[]`；无测试账号环境变量或登录会话。已请求连接浏览器并提供授权测试登录方式。
- `https://bi.yuzhicloud.com/api/session/status` 返回 HTTP 200、`authenticated=false`，仅证明会话入口可达，不代表业务验收通过。
- 离线目标环境未提供，不能声称离线验收完成。

原始日志：`/tmp/f6-release-build.log`、`/tmp/f6-migration-test.log`、`/tmp/f6-migration-container-test.log`。此前128项专项测试证据见 `F6-verification-20260910.md`，本次新增3项迁移用例单独计数。
