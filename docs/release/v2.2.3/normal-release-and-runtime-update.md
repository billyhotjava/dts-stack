# 正式发布与离线运行脚本更新

## 目录与版本边界

- `/opt/prod/s10/v2.2.3`：开发、静态检查、提交、推送。
- `/opt/prod/s10/deploy`：核对分支、确认工作区后 `git pull --ff-only`，核对目标 SHA，再使用正式入口测试、构建和制包。工作区中既有产物不能作为新提交已构建的证明。
- 现场运行目录（例如 `/data/dts-stack`）：只接收正式交付包，由包内升级器更新。
- 禁止将开发目录未提交的文件、编译产物或脚本手工放入部署目录及容器。

## Airflow 文件如何交付

`services/dts-airflow/extra` 是交付包中的产品代码目录，由 Compose 只读挂载到 `/opt/airflow/extra`。更新前后端镜像不会更新这份代码；升级时必须同步同一发布批次的运行文件，并重启 Airflow 进程。

包内包括：

- 前后端及依赖镜像 tar，使用目标现场的 CPU 架构。
- `services/dts-airflow/extra/dts_runtime/dbt_task_factory.py`。
- `services/dts-airflow/dags/dts_release_build_postgres_primary.py`；现场生成的业务 DAG 不整体覆盖。
- dbt 项目定义、宏与 `dbt_model/models` 静态依赖；现场导入的 `models` 保留。
- `bin/` 升级与回滚工具、Compose 和现场配置模板。
- `extra/release-manifest.json`（源码 SHA、镜像 ID/架构等）、`checksums.txt`（镜像校验）、`files-checksums.txt`（运行文件校验）。明确 `--no-images` 的包不携带镜像校验文件，需另行交付并预载匹配镜像。

准备交付包时，先完成同一目标 SHA 的正式构建，再在部署目录执行：

```bash
cd /opt/prod/s10/deploy
# 将已确认的完整提交 SHA 写入 EXPECTED_RELEASE_SHA；构建和制包期间保持该 SHA。
export DTS_BUILD_EXPECTED_SHA="${EXPECTED_RELEASE_SHA:?请先设置已确认的完整提交 SHA}"
./builds/dts-build.sh --pack --output /tmp/dts-offline-release.tar.gz
sha256sum /tmp/dts-offline-release.tar.gz > /tmp/dts-offline-release.tar.gz.sha256
```

制包入口会读取现有镜像导出目录，必须先核对其中镜像的版本和架构，不能混入以前批次或另一架构的 tar。交付包 SHA256 随发布记录传递，并在现场解包前核对。

## 离线现场更新

将完整包解压到与运行目录分离的新目录，如 `/tmp/dts-update/dts-stack`。在新包目录执行升级入口；无需在现场联网、编译或安装新 Python 依赖。`dts-upgrade-lite` 使用 Bash 和现有系统工具，适用于无 Python 的 legacy 现场。

```bash
cd /tmp/dts-update/dts-stack
./bin/dts-upgrade-lite plan \
  --target /data/dts-stack \
  --source /tmp/dts-update/dts-stack \
  --images-dir /tmp/dts-update/images \
  --extra-dir /tmp/dts-update/extra
```

核对报告中的镜像版本、现场配置差异及 Runtime files。`BACKUP_AND_UPDATE` 是将被备份替换的产品代码，`KEEP_SITE` 是保留的现场文件。等待运行任务结束，在维护窗口将 `plan` 换成 `apply`，其余参数保持一致并追加 `--yes`。

升级器校验完整运行文件清单与镜像，再停服务、备份、更新并启动。`.env` 业务配置、现场 Compose、连接配置及数据目录沿用各自的保护规则；产品代码不会再因为“已有文件”而静默跳过。新包缺少文件清单、文件被改动或混入未列出的文件时，在改动服务之前失败。

Python 可用的现场也可从新包运行 `bin/dts-upgrade --target ... --images-dir ... --extra-dir ...`；该入口要求原容器已经停止。它同时兼容旧字符串镜像清单与新的对象清单，使用相同运行文件校验和产品文件替换规则。

## 回滚与验收

lite 入口：使用升级报告打印的 `backup` 路径运行 `bin/dts-upgrade-lite rollback --target /data/dts-stack --backup-dir <backup>`。

完整入口：使用现场 `backups/upgrade-*/rollback-manifest.json` 所在目录运行 `bin/dts-upgrade-rollback --target /data/dts-stack --backup-dir <backup>`。新版包的回滚记录仅写入现场备份目录，原交付包保持不变。

回滚恢复旧脚本，删除本次新增文件，默认不回滚业务数据库。记录源码 SHA、镜像 ID、包 SHA256、升级报告、运行脚本与包的比对结果；再执行一次真实模型构建。Airflow 四个步骤、Candidate、dispatch 和 pipeline 均成功才算本次物化验收通过。
