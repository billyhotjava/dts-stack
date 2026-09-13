# T02: 数据源类型与 connectionConfig 映射

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

把 DTS 数据源类型和 OpenMetadata serviceType / connectionConfig 映射收敛到一个可维护入口。

## 范围

- 梳理当前支持的数据源类型和 OpenMetadata connector 名称。
- 建立 serviceType、driver、scheme、默认端口映射。
- 不支持的数据源返回明确 unsupported，而不是拼接半成品配置。
- 为国产库或兼容库保留扩展点。

## 完成标准

- [ ] 支持当前数据库接入主链路中的目标库类型。
- [ ] unsupported 类型不会触发无效 OpenMetadata 请求。
- [ ] 映射逻辑有单测。
- [ ] 文档列出支持矩阵和限制。
