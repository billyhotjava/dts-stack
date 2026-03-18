# OPS-007: 备份、运行数据保护与回滚清单

## 目标

升级前自动备份关键文件，保护运行数据目录，并生成可审计的回滚清单。

## 交付物

- `backups/upgrade-*/`
- `extra/rollback-manifest.json`
- 运行数据保护清单

## 验收标准

- `services/dts-pg/data` 不被覆盖
- 关键文件存在备份副本
- 回滚清单能说明哪些文件被替换、哪些目录被保留
