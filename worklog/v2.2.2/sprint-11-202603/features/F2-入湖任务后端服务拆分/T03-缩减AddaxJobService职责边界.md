# T03: 缩减 AddaxJobService 职责边界

**优先级**: P1
**状态**: READY
**依赖**: T02

## 目标
为超大 Addax 服务建立更明确的拆分方向，逐步下沉模板生成、参数归一和作业校验逻辑。

## 技术设计

- 识别纯转换逻辑与外部交互逻辑
- 优先把易测的纯函数/归一化逻辑下沉
- 保持 Addax 作业输出契约不变

## 影响范围

- [AddaxJobService.java](/opt/prod/s10/s10-stack/source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AddaxJobService.java)
- 相关 DTO / helper / 测试

## 验证

- [ ] Addax 相关测试通过
- [ ] 生成作业关键路径回归通过

## 完成标准

- [ ] AddaxJobService 体量下降
- [ ] 关键转换逻辑独立可测
