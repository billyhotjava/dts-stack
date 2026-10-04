# JDBC Drivers (external)

This directory is mounted into `dts-analytics` containers at `/opt/dts/jdbc` and is used to load **external JDBC drivers** that we do not vendor in the application (e.g. Dameng/DM8).

- Put JDBC driver jars here (example: `DmJdbcDriver18.jar`).
- Jars are ignored by git (`services/jdbc/*.jar`).

Runtime config:
- `ANALYTICS_JDBC_DRIVERS_DIR=/opt/dts/jdbc` (set by compose)

