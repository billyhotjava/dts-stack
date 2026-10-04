# OPS-003: 离线镜像导入与升级包完整性校验

## 目标

保证升级器在内网环境不依赖任何下载能力，仅基于 `images/` 和 `extra/` 完成完整性校验与镜像导入。

## 交付物

- `release-manifest.json`
- `checksums.txt`
- 镜像存在性校验
- `docker load` 批量导入

## 验收标准

- 缺少镜像 tar 时报错退出
- 校验和不匹配时报错退出
- 镜像导入顺序与 manifest 一致
