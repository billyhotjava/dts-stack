URI 规范建议（统一规则，方便前后端解析与检索）

数据表：db://<cluster>/<catalog>/<schema>/<table>

例：db://pg17/dts/ods/orders

本地/共享文件：file:///var/data/reports/2025-09/audit.csv

HDFS：hdfs://nn1/user/dts/uploads/a.pdf

S3/对象存储：s3://audit-bkt/reports/2025/09/28.json

Kafka Topic：kafka://prod-cluster/dts-change-events

HTTP API：https://api.example.com/v1/users/123

无法精确归类时：other://<free-text> 并将 targetKind=OTHER

这样你只需一个字段就能覆盖“在哪个库/哪张表/哪个文件”的需求，同时还能扩展到更多资源类型。