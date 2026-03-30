# T06: 发布端点加 classification 参数

**优先级**: P1
**状态**: READY
**依赖**: T03

## 目标
大屏发布时可设置密级（classification），存入 analytics_screen 表

## 技术设计

修改 `ScreenResource.publish()` 端点：

```java
// POST /api/screens/{id}/publish
// Body: { "description": "版本说明", "classification": "INTERNAL" }
// classification 可选值: PUBLIC, INTERNAL, SECRET, CONFIDENTIAL
// 不传或为 null 时保持现有值
```

实现：
1. 从 request body 读取 classification
2. 校验值是否在 PUBLIC/INTERNAL/SECRET/CONFIDENTIAL 范围内
3. 写入 `screen.setClassification(classification)`
4. 保存 screen

当前不做密级校验（查看时不检查用户人员密级），只存储值。

## 影响范围
- 修改: `ScreenResource.publish()` — 读取并保存 classification
- 修改: `AnalyticsScreen.java` — 已在 T03 中添加字段

## 验证
- [ ] 发布时传入 classification 正确保存
- [ ] 不传 classification 时保持 null
- [ ] 无效值返回 400

## 完成标准
- [ ] 发布接口支持 classification 参数
- [ ] 数据库正确存储
