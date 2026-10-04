# T02: 统一上传与网关大小限制

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

把大 CSV 上传链路的前端提示、Nginx、platform、ingestion 配置统一到正式性能目标。

## 技术设计

建议引入统一配置：

```text
DTS_CSV_IMPORT_MAX_FILE_SIZE=629145600
```

覆盖点：

- `dts-platform-webapp` Nginx `client_max_body_size`。
- `dts-ingestion` multipart `max-file-size` / `max-request-size`。
- `dts-platform` `DATA_STANDARD_MAX_FILE_SIZE` 或地铁导入专用配置。
- 前端上传提示从固定 `≤200MB` 改为读取后端能力或统一文案。

## 影响范围

- `source/dts-ingestion/src/main/resources/application.yml`
- `source/dts-platform/src/main/resources/config/application.yml`
- `source/dts-platform-webapp/nginx/default.conf`
- `source/dts-platform-webapp/src/pages/**`
- `docker-compose*.yml` / `.env` 中的环境变量传递

## 验证

- [ ] 100MB CSV 不被网关或 multipart 拦截。
- [ ] 500MB CSV 不被网关或 multipart 拦截。
- [ ] UI 展示的限制与后端配置一致。

## 完成标准

- [ ] 上传链路所有限制不低于 600MB。
- [ ] 超限错误返回可读的业务提示。
