#!/bin/sh
set -eu

sh /opt/dts/bin/prepare-dbt-runtime-profile-root.sh
exec sh -c 'exec java $JAVA_OPTS -jar /app/app.jar'
