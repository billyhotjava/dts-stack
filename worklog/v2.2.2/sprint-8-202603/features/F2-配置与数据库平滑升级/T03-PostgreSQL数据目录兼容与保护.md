# T03: PostgreSQL 数据目录兼容与保护

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标
把 `services/dts-pg/data` 从“普通目录复制逻辑”中剥离出来，单独定义兼容、备份和回滚规则。

## 技术设计
- 新包中的 `services/dts-pg/data` 只作为空骨架，升级时不复制、不覆盖、不合并
- 旧目录 `services/dts-pg/data` 为唯一运行事实，升级器只能：
  - 校验
  - 备份
  - 记录
  - 在失败时整目录恢复
- 升级前数据库门禁：
  - 校验旧目录 `services/dts-pg/data/pgdata/PG_VERSION` 是否存在
  - 解析新包 `.env` / `imgversion.conf` 中的 `IMAGE_POSTGRES`
  - 提取目标 PostgreSQL major 版本
  - 若旧 major 与目标 major 不一致，直接阻断升级
- 兼容策略：
  - 同 major：允许原位复用旧数据目录
  - 跨 major：不允许走“平滑升级”主流程，必须单独走数据库迁移/导出导入流程
- 备份策略：
  - 升级前要求人工停容器
  - 停机后对 `services/dts-pg/data` 做冷备
  - 冷备路径写入 `backups/upgrade-*/rollback-manifest.json`
  - 可选追加逻辑备份说明，但首版不把 `pg_dump` 作为强依赖
- 运行保护：
  - 升级器不修改旧数据目录内文件权限以外的内容
  - 升级后 postcheck 必须验证 PostgreSQL 容器能使用旧数据目录成功启动
- 失败回滚：
  - 配置可按文件级回滚
  - PostgreSQL 只按整目录快照回滚，不做键级或文件级合并

## 影响范围
- `services/dts-pg/data`
- `imgversion.conf`
- `.env`
- `docker-compose.yml`
- `docker-compose.legacy.yml`
- `init.sh`
- 升级器数据库预检查逻辑
- rollback manifest
- 现场 runbook

## 验证
- [ ] 数据目录不会被覆盖
- [ ] PG major 不兼容时升级直接阻断
- [ ] 升级前有数据库备份指引
- [ ] 回滚清单包含数据库冷备路径
- [ ] postcheck 包含 PostgreSQL 启动成功检查

## 完成标准
- [ ] 数据库文件被纳入平滑升级核心设计
- [ ] 数据目录兼容、冷备、回滚和 postcheck 规则可直接落入升级器实现
