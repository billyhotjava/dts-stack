# openEuler 自动接收本地 x86 发布

链路：`/opt/prod/s10/deploy` → 阿里云发布目录 → openEuler。
Windows 仅中转安装时的 SSH，镜像和运行文件不经过 Windows。

入口为 `bin/dts-release-sync.py`，配置和密钥位于仓库外。
本地 `publish` 每 5 分钟检查已提交的受管运行文件及 `builds/dist/*.tar`；
只选择每个 DTS 镜像最新的 linux/amd64 归档，不重新编译或构建镜像。
必须先在开发目录提交并推送，再在部署目录 `git pull --ff-only`。
导出未稳定 120 秒、受管文件变更中或归档架构错误时，不发布。

打包复用 `builds/dts-build.sh --pack --no-images`，根据
`bin/lib/dts-runtime-files.sh` 的产品文件规则裁剪，并加入已存在的 x86 镜像。
不发布本地 `.env`、证书、现场配置、生成的业务 DAG 和业务模型。
正式打包规则声明的静态发布 DAG、dbt 静态依赖和宏会收录，即使它们由正式构建生成、未被 Git 跟踪。
运行文件的 Git SHA 与各镜像原始 `sourceRevision`、image ID、校验和分别记录；
未声明来源提交的旧镜像会保留 null，不宣称所有镜像由当前提交重建。

发布使用专用 SFTP 密钥，先上传包，最后原子切换带 RSA 签名的 `latest.json`。
私钥留在本机；目标仅保存公钥，HTTP 传输内容必须验签及通过 SHA-256 校验。
阿里云必须为新包预留空间；上传失败不会切换版本，也不自动删除旧包或镜像。

目标 `consume` 每 5 分钟检查发布清单。它断点下载、验证包、生成升级计划，
检查 Airflow 没有 running DAG run，再调用新包的 `dts-upgrade-lite apply`。
该升级器会备份数据库和产品文件，短暂停止并重新启动现有 Compose 管理的
整套 DTS 服务；Portainer 等其他 Compose 项目不在此范围。
现场 `.env` 只更新本次所带镜像的键，现场 Compose 保留原样。
产品 Airflow 脚本、静态发布 DAG、dbt 宏等按已有受管文件规则更新。
原来运行的服务必须恢复 running/healthy，镜像 ID 必须匹配，才记录成功。
Docker classic 与 containerd 可能分别显示 config ID 和 manifest ID；校验同时验证
归档的 OCI manifest 到 config 的引用、内容摘要和 RootFS 层，不能只比较显示字符串。

同一发布不重复重启；Airflow 有运行任务时推迟；失败或升级中断时保留
`FAILED.json` 并停止后续自动应用，避免不断重启。修复或按升级报告回滚后，
检查现场状态，再由运维删除失败标记并重新启动服务。不会自动恢复数据库。
自动化也不代替业务页面验收或跨版本数据库兼容性审查。

## 运维入口

本地用户服务：`systemctl --user status dts-release-publish.timer`。
日志：`journalctl --user -u dts-release-publish.service`。

openEuler：`systemctl status dts-release-update.timer`。
日志：`journalctl -u dts-release-update.service`。
暂停：`systemctl stop dts-release-update.timer`；恢复使用 `start`。
目标配置：`/etc/dts-release-sync/config.json`。
状态、下载包与发布快照：`/home/dts-stack/auto-release`。
升级报告及备份：`/opt/prod/dts-stack/logs/upgrade-lite-*`。

只下载并生成计划（不会应用）：

```bash
python3 -B /usr/local/sbin/dts-release-sync.py check --config /etc/dts-release-sync/config.json
```

首次接管已完成的人工升级，使用 `adopt` 替代 `check`：必须逐项验证已安装运行文件、
镜像内容及健康状态完全匹配，才登记基线；此操作不会重启服务。
传输工具本身部署到 `/usr/local/sbin`，不加入 DTS 应用运行文件包。

所有密码均不写入脚本、文档、Git 或定时任务。
