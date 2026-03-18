# Sprint-2：离线环境一键升级编排

## 目标

把当前“解压新包 -> 手工拷 PG 数据 -> 手工对比 compose/config/.env -> 手工导入镜像 -> 手工启动”的升级流程，收敛成一个适用于**内网离线环境**的单入口升级器。

本 Sprint 只设计并实现升级编排层，不改业务功能。

## 关键约束

- 最终部署环境是**内网离线环境**
- 升级前由人工**先停止旧容器**
- 升级方式接受**原目录就地升级**
- 升级包解压在新目录，例如 `/data/update-dts`
- 运行中的旧目录例如 `/data/old-dts`
- 新包中包含完整的 `dts-stack/`
- 新包中包含 `images/`，存放升级所需镜像 tar
- 旧配置优先保留，尤其是：
  - `.env`
  - `docker-compose*.yml`
  - `config/` 下现场定制配置
- 新包里的新增变量、服务、镜像、脚本需要自动补入
- 升级全过程必须输出日志到旧目录 `logs/`

## 交付范围

### 脚本
- 新增统一入口：`bin/dts-upgrade`
- 新增升级共享库：`bin/lib/dts-upgrade-common.sh`

### 升级元数据
- 新增升级包元数据约定：
  - `extra/release-manifest.json`
  - `extra/merge-rules.yml`
  - `extra/checksums.txt`
  - `extra/rollback-manifest.json`

### 验证
- 本地离线模拟升级验证
- `.env` 合并验证
- `docker-compose*.yml` 合并验证
- `config/` 合并验证
- `images/` 离线导入验证
- 升级日志与回滚清单验证

## 设计结论

- 升级器在 **`/data/update-dts/dts-stack`** 内执行，但操作目标是旧目录 **`/data/old-dts`**
- 升级器默认要求旧容器已停；若未停，直接失败
- 升级器不得依赖任何在线下载能力，不允许 `docker pull / curl / wget / apt / pnpm install`
- 合并策略以**旧配置优先**，新包主要用于补新增项
- 升级器需要先完成完整性校验、镜像就绪校验、备份与升级锁，再进入覆盖和启动阶段

## 文档

- [design.md](./design.md)
- [plan.md](./plan.md)
- [tasks](./tasks)
