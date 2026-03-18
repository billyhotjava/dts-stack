# OPS-001: 离线升级包契约与升级器入口

## 目标

定义离线升级包的标准结构，并在包内引入统一升级入口 `bin/dts-upgrade`。

## 交付物

- 升级包结构定义
- `bin/dts-upgrade`
- `bin/lib/dts-upgrade-common.sh`
- `extra/` 元数据目录骨架

## 验收标准

- `builds/dts-build.sh --pack` 产物包含升级器脚本
- 升级包中出现 `extra/` 目录
- 离线包结构可被升级器正确识别
