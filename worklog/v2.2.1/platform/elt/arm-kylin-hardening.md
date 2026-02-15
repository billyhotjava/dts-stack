# ARM/麒麟兼容加固与复测（v2.2.1）

## 1. 目标
确保平台侧 ETL 与验证脚本在 `x86_64 + Linux`、`aarch64 + Kylin` 两类环境下具备一致行为与可复跑证据。

## 2. 兼容性检查表
| 分类 | 检查项 | 状态 | 说明 |
|---|---|---|---|
| 镜像 | Addax/DBT/Platform 镜像支持多架构 | `PARTIAL` | 已有 ARM 适配记录，需补版本矩阵 |
| Shell 脚本 | `bash` 语法与核心命令在麒麟可用 | `READY` | `platform/scripts/*.sh` 仅用基础 POSIX + awk |
| 时间与时区 | 统一 `Asia/Shanghai` 输出 | `READY` | 证据脚本默认时区标签可配置 |
| 路径权限 | airflow/dags/logs/plugins 目录权限一致 | `PARTIAL` | 现场出现过 `Permission denied`，需固定初始化策略 |
| 网络 | 容器网络名不写死，自动发现 | `READY` | 现有脚本已避免硬编码 `dts-core` |
| 日志采集 | `docker logs --since` 在麒麟可用 | `READY` | 已用于 evidence 脚本 |
| 数据库工具 | `psql` 在 PG 容器可调用 | `READY` | 通过 `docker exec` 方式执行 |

## 3. 复测步骤（建议顺序）
1. 先执行预检（强烈建议）：
   - `bash worklog/v2.2.1/platform/scripts/arm-kylin-preflight.sh`
2. 一键执行多模式矩阵（推荐）：
   - `bash worklog/v2.2.1/platform/scripts/run-matrix.sh --hours 168 --arch aarch64 --modes normal,legacy,dev --require-data --note-prefix matrix-arm --tag-prefix matrix-arm`
3. 单模式手工执行（可选）：
   - `bash worklog/v2.2.1/platform/scripts/run-all.sh --hours 168 --mode legacy --arch aarch64 --require-data --note "kylin-legacy" --tag kylin-legacy`
4. 执行隔离回归：
   - `bash worklog/v2.2.1/platform/scripts/isolation-lineage-check.sh snapshot --label kylin-before`
   - 执行目标项目操作后再快照，随后 compare。

## 4. 常见问题与建议
- 问题：`Permission denied`（plugins/dags/logs）
  - 建议：统一由 `init.sh` 或单独 preflight 脚本修正宿主目录 owner/group 和权限。
- 问题：任务创建后 DAG 未及时可见
  - 建议：保留二段重试与指数等待，并在前端明确“正在同步 DAG”。
- 问题：驱动/连接器在 ARM 上行为不一致
  - 建议：建立驱动白名单与 checksum 清单，镜像构建时固定版本。
- 问题：误把 x86 样本标记成 ARM
  - 建议：使用默认架构门禁（`--arch` 必须等于宿主架构），仅在调试场景使用 `--allow-arch-override`。

## 5. 当前结论
- 当前状态：`可执行首轮 ARM/麒麟复测`。
- 待完成：现场跑完完整矩阵并回填 `stability-24h.md` 结果。

## 6. 最新执行备注（2026-02-14）
- 新增统一入口：`platform/scripts/run-all.sh`，支持 `--mode/--arch/--tz/--require-data`。
- 新增预检脚本：`platform/scripts/arm-kylin-preflight.sh`（默认校验 `aarch64 + kylin + docker + 关键容器`）。
- 本地复跑结果：脚本链路通过；在 `168h` 窗口可采到非空样本（总 3、成功 2、失败 1），仍需在目标 ARM/麒麟环境完成等价复测。
