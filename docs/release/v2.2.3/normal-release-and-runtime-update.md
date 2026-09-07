# DTS 离线现场升级与 Airflow 运行文件更新

## 1. 示例环境与交付要求

本文使用一套独立的客户离线环境举例：服务器已安装旧版 DTS，本次升级到新的正式发布版本。以下主机名、目录和发布编号均为示例，执行前按现场实际值替换。全新服务器的首次安装应按安装手册执行，本文用于已有环境升级。

| 项目 | 示例值 |
| --- | --- |
| 离线服务器 | `dts-offline-01`，Linux x86_64，不能访问互联网 |
| 现有运行目录 | `/srv/dts/app` |
| 运行配置 | `/srv/dts/app/docker-compose-app.yml`，使用现有项目名和 `.env` |
| 上传目录 | `/srv/dts/incoming` |
| 本次交付包 | `dts-release-002.tar.gz` 及 `dts-release-002.tar.gz.sha256` |
| 新包解压目录 | `/srv/dts/releases/release-002`，与运行目录分离 |

现场已具备 Docker、Compose、Bash、tar 和 sha256sum。鲲鹏现场应使用匹配的 arm64 交付包；采用 legacy 部署时，后文人工执行的 Compose 命令改用现场原有的 `docker-compose` 和 `docker-compose.legacy.yml`，保持原项目名及启动参数。

交付方须先完成代码提交、正式构建和制包，提供同一 Git 提交对应的运行文件与镜像。发布清单记录源码 SHA、镜像 ID、架构和校验和；不能用“镜像标签看起来最新”代替版本一致性核对。现场只接收正式包，不执行 Git 拉取、源码编译、镜像构建或联网安装依赖。

本示例使用完整离线包，目录应包含：

```text
release-002/
├── dts-stack/                  # 产品脚本、静态 DAG、dbt 文件、配置模板、升级器
├── images/                     # 本次发布的镜像 tar
└── extra/
    ├── release-manifest.json   # 发布版本、源码 SHA、镜像 ID/架构
    ├── checksums.txt           # 镜像 tar 的 SHA256
    └── files-checksums.txt     # 运行文件和发布元数据的 SHA256
```

如交付方提供无镜像包，必须另外交付并加载匹配的镜像，仍须保留发布清单和运行文件校验清单。不要将其他包的脚本、依赖或镜像混入本次解压目录。

## 2. 接收、校验和解包

把包及校验文件传入上传目录。校验文件内应使用包的文件名，不应引用交付方构建机上的绝对路径。以下操作在离线服务器上执行：

```bash
cd /srv/dts/incoming
sha256sum -c dts-release-002.tar.gz.sha256
```

只有校验结果为 OK 才继续。使用全新的解压目录，避免与上次发布文件混合：

```bash
mkdir -p /srv/dts/releases
mkdir /srv/dts/releases/release-002
tar -xzf /srv/dts/incoming/dts-release-002.tar.gz -C /srv/dts/releases/release-002

cd /srv/dts/releases/release-002
sha256sum -c extra/files-checksums.txt
cd images
sha256sum -c ../extra/checksums.txt
```

检查发布清单中的架构与现场一致，源码 SHA 与批准交付的版本一致。任一步校验失败都应停止，重新获取完整包；不要改写校验文件后继续升级。

## 3. 使用包内升级器更新（推荐）

必须从新包执行升级器，不能使用旧运行目录里尚未更新的升级脚本。先生成计划：

```bash
cd /srv/dts/releases/release-002/dts-stack
./bin/dts-upgrade-lite plan \
  --target /srv/dts/app \
  --source /srv/dts/releases/release-002/dts-stack \
  --images-dir /srv/dts/releases/release-002/images \
  --extra-dir /srv/dts/releases/release-002/extra
```

按终端输出打开报告，重点核对镜像版本、数据库主版本、现场配置差异及 Runtime files：`BACKUP_AND_UPDATE` 表示备份后替换产品文件，`KEEP_SITE` 表示保留已有现场文件。报告和备份存放在运行目录的 `logs/upgrade-lite-*` 下。

暂停调度并等待运行中的任务结束，在维护窗口执行：

```bash
cd /srv/dts/releases/release-002/dts-stack
./bin/dts-upgrade-lite apply \
  --target /srv/dts/app \
  --source /srv/dts/releases/release-002/dts-stack \
  --images-dir /srv/dts/releases/release-002/images \
  --extra-dir /srv/dts/releases/release-002/extra \
  --yes
```

该流程会重新校验交付包、加载镜像、停止现有服务、备份并更新文件、启动服务。它会影响当前 Compose 管理的整套服务，需为备份和停机预留时间与磁盘空间。停服务失败时中止更新；产品脚本不再因目标文件已存在而被跳过。

现场 `.env` 的业务配置、连接信息、数据目录、业务 DAG 和导入模型按保护规则保留。完成后记录本次 `apply` 输出的报告及备份路径，原交付包保留用于追溯。

## 4. 本次物化修复涉及哪些 Airflow 文件

`services/dts-airflow/extra` 在宿主机上，通过 Compose 只读挂载到容器的 `/opt/airflow/extra`。因此升级镜像之外，还需要更新宿主机文件并重新启动 Airflow 进程。

| 相对运行目录的路径 | 本次操作 |
| --- | --- |
| `services/dts-airflow/extra/dts_runtime/dbt_task_factory.py` | 更新；本次修改补充平台接口失败时的错误码和关联 ID |
| `services/dts-airflow/dags/dts_release_build_postgres_primary.py` | 确认与交付包一致；旧包缺失时补入，这是静态构建 DAG |

按表更新产品文件，保留现场的 `config`、其他业务 DAG、连接配置和数据；测试文件不需要部署。本表针对已具备同版运行依赖的本次修复；更早版本跨版本升级应使用完整包内升级器，同步声明的 dbt 项目、宏和静态依赖。

**物化收尾顺序的修复位于 `dts-platform` 后端。** 仅替换 Python 文件不能独立解决原来的 HTTP 422。必须同时更新该发布版本的 `dts-platform` 镜像；更新 `dts-platform-webapp` 镜像后，页面才能提供配套的执行步骤日志入口。

### 选择手动更新时

本节是第 3 节的替代方式，适用于已确认仅需本次修复、现场运行配置与交付包兼容的环境。先完成第 2 节全部校验，并核对第 3 节 `plan` 报告；如还存在其他运行依赖或配置变更，使用升级器完成更新。

1. 暂停调度、等待任务结束，记录原镜像 ID 和标签；备份运行目录的 `.env`、`imgversion.conf` 及上表两个文件，记录原先不存在的文件。备份存入独立目录，例如 `/srv/dts/backups/release-002-before`。
2. 使用现场原有 Compose 项目和配置，停止 `dts-platform`、`dts-platform-webapp`、`dts-airflow-scheduler`、`dts-airflow-webserver`、`dts-airflow-triggerer`，确认停止成功。
3. 从已校验的新包 `images` 目录按清单执行 `docker load -i`，核对加载后的镜像 ID。把现场 `.env` 和 `imgversion.conf` 中对应的 `IMAGE_DTS_PLATFORM`、`IMAGE_DTS_PLATFORM_WEBAPP` 更新为包内声明的标签，保留其他现场参数。
4. 从 `/srv/dts/releases/release-002/dts-stack/` 取出上表文件，按相对路径复制到 `/srv/dts/app/`。缺失目录先创建，保证 Airflow 进程可读；更新后用 `cmp` 或 SHA256 与包内文件逐一比对。文件来自正式包，不从开发目录或运行容器中取文件。
5. 使用原 Compose 项目重建相关容器。以下为本示例命令；若现场原命令包含 `-p`、`--env-file` 或额外 `-f`，须沿用：

```bash
cd /srv/dts/app
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate \
  dts-platform dts-platform-webapp \
  dts-airflow-scheduler dts-airflow-webserver dts-airflow-triggerer
```

此命令使用已导入的镜像重建容器，不执行镜像构建。`--no-deps` 要求其他依赖服务已经正常运行；所有镜像必须预先导入，避免现场尝试联网拉取。Airflow 镜像本身没有变更时仍需重启进程，以加载更新后的挂载脚本。

## 5. 升级验收和日志定位

先确认升级报告无失败项、容器正常且镜像 ID 与发布清单一致，再从建模页面重新发起一次构建。记录本次运行的 DAG ID、run ID 和平台关联 ID；历史成功记录不能替代本次运行结果。

应核对以下四个 Airflow 步骤：

| task ID | 主要检查内容 |
| --- | --- |
| `prepare_runtime` | 执行环境和配置准备 |
| `dbt_build` | 模型构建 |
| `sync_manifest_and_probe` | 构建产物同步及物理表核验 |
| `finalize_run` | 构建收尾和质量资产登记 |

四个步骤成功，且 Candidate 为 BUILT、dispatch 为 COMPLETED、pipeline 为 BUILT，才算本次物化验收通过。通过后恢复调度。

可在平台日志查看页面选择对应运行和执行步骤，也可在本示例宿主机查阅：

```text
/srv/dts/app/logs/airflow/dag_id=<DAG_ID>/run_id=<RUN_ID>/task_id=<TASK_ID>/attempt=<次数>.log
/srv/dts/app/logs/dts-platform/app.log
```

具体路径以现场 Compose 的日志挂载配置为准。若 `dbt_build` 成功但物化仍失败，继续检查后两个步骤；使用日志中的错误码、关联 ID 或 dispatch 对应的 run group 查询平台异常。

## 6. 回滚

使用升级器完成更新的环境，应从第 3 节本次 `apply` 报告取得真实备份路径。替换下例路径中的 `实际升级时间` 后执行：

```bash
cd /srv/dts/releases/release-002/dts-stack
./bin/dts-upgrade-lite rollback \
  --target /srv/dts/app \
  --backup-dir /srv/dts/app/logs/upgrade-lite-实际升级时间/backup
```

回滚恢复旧配置和脚本、删除本次新增文件，并按旧版本配置启动容器。须保留旧镜像或旧镜像归档；默认不恢复业务数据库，数据库兼容性及恢复方案应在升级前确认。

手动更新不会自动生成升级器回滚清单。需要手动停止相关服务，恢复第 4 节备份的文件与镜像配置、删除记录的新增文件，再使用旧镜像重建容器。回滚后重新核对服务和业务状态，保留发布清单、校验和及失败日志。
