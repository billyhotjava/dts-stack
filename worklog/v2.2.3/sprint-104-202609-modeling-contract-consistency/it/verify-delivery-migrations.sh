#!/usr/bin/env bash
# Isolated deploy-checkout proof for Sprint-104's two additive Liquibase changeSets.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
readonly EXPECTED_DEPLOY_ROOT="/opt/prod/s10/deploy"
readonly PG_CONTAINER="${S104_MIGRATION_PG_CONTAINER:-deploy-dts-pg-1}"
readonly PG_SUPER_USER="${S104_MIGRATION_PG_USER:-postgres}"
readonly RUN_ID="$(date +%Y%m%d%H%M%S)-$$"
readonly PLATFORM_DB="s104_platform_${RUN_ID//-/}"
readonly ANALYTICS_DB="s104_analytics_${RUN_ID//-/}"
readonly TMP_DIR="$(mktemp -d)"
PG_DRIVER=""

fail() { echo "[s104-migration] ERROR: $*" >&2; exit 1; }

if [[ "$REPO_ROOT" != "$EXPECTED_DEPLOY_ROOT" ]]; then
    fail "run only from the clean deploy checkout ($EXPECTED_DEPLOY_ROOT), never from the development checkout"
fi
[[ -f "$REPO_ROOT/.env" ]] || fail "missing deploy .env"
command -v docker >/dev/null || fail "docker is required"
command -v mvn >/dev/null || fail "mvn is required"
docker inspect "$PG_CONTAINER" >/dev/null 2>&1 || fail "Postgres container is unavailable: $PG_CONTAINER"
docker port "$PG_CONTAINER" 5432 2>/dev/null | grep -q ':5432$' || fail "Postgres port 5432 is not published locally"

# The generated database names are constant-format identifiers, never caller supplied.
drop_database() {
    local database="$1"
    docker exec "$PG_CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$PG_SUPER_USER" -d postgres \
        -c "DROP DATABASE IF EXISTS \"${database}\" WITH (FORCE)" >/dev/null
}

cleanup() {
    local status=$?
    drop_database "$PLATFORM_DB" || true
    drop_database "$ANALYTICS_DB" || true
    rm -rf "$TMP_DIR"
    exit "$status"
}
trap cleanup EXIT

psql_database() {
    local database="$1"
    local sql="$2"
    docker exec "$PG_CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$PG_SUPER_USER" -d "$database" -c "$sql"
}

scalar() {
    local database="$1"
    local sql="$2"
    docker exec "$PG_CONTAINER" psql -X -v ON_ERROR_STOP=1 -At -U "$PG_SUPER_USER" -d "$database" -c "$sql"
}

assert_scalar() {
    local database="$1"
    local sql="$2"
    local expected="$3"
    local actual
    actual="$(scalar "$database" "$sql")"
    [[ "$actual" == "$expected" ]] || fail "expected '$expected', got '$actual'"
}

expect_sql_failure() {
    local database="$1"
    local sql="$2"
    if psql_database "$database" "$sql" >/dev/null 2>&1; then
        fail "expected SQL to fail: $sql"
    fi
}

write_wrapper() {
    local wrapper="$1"
    local change_log="$2"
    cat > "$wrapper" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.24.xsd">
    <include file="$change_log" relativeToChangelogFile="false"/>
</databaseChangeLog>
EOF
}

write_minimal_maven_pom() {
    cat > "$TMP_DIR/pom.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>local.s104</groupId>
    <artifactId>isolated-liquibase-verification</artifactId>
    <version>1.0.0</version>
    <build>
        <plugins>
            <plugin>
                <groupId>org.liquibase</groupId>
                <artifactId>liquibase-maven-plugin</artifactId>
                <version>4.29.2</version>
                <configuration>
                    <changeLogFile>${liquibase.changeLogFile}</changeLogFile>
                    <url>${liquibase.url}</url>
                    <username>${liquibase.username}</username>
                    <password>${liquibase.password}</password>
                    <driver>org.postgresql.Driver</driver>
                    <classpath>${liquibase.classpath}</classpath>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.postgresql</groupId>
                        <artifactId>postgresql</artifactId>
                        <version>42.7.11</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
EOF
}

run_liquibase() {
    local database="$1"
    local wrapper="$2"
    local goal="$3"
    shift 3
    mvn -o -B -q -f "$TMP_DIR/pom.xml" \
        -Dliquibase.changeLogFile="$wrapper" \
        -Dliquibase.url="jdbc:postgresql://127.0.0.1:5432/${database}" \
        -Dliquibase.username="$PG_SUPER_USER" \
        -Dliquibase.password="${PG_SUPER_PASSWORD:?PG_SUPER_PASSWORD must be set in deploy .env}" \
        -Dliquibase.classpath="$PG_DRIVER" \
        "org.liquibase:liquibase-maven-plugin:4.29.2:${goal}" "$@"
}

run_platform() {
    local wrapper="$TMP_DIR/platform.xml"
    write_wrapper "$wrapper" "$REPO_ROOT/source/dts-platform/src/main/resources/config/liquibase/changelog/20260906_01_catalog_dataset_version.xml"
    run_liquibase "$PLATFORM_DB" "$wrapper" update
    assert_scalar "$PLATFORM_DB" "select count(*) from catalog_dataset where version = 0" "1"
    assert_scalar "$PLATFORM_DB" "select count(*) from information_schema.columns where table_name = 'catalog_dataset' and column_name = 'version' and is_nullable = 'NO' and column_default like '0%'" "1"
    psql_database "$PLATFORM_DB" "insert into catalog_dataset (id, name) values ('00000000-0000-0000-0000-000000000002', 'post-update')" >/dev/null
    assert_scalar "$PLATFORM_DB" "select count(*) from catalog_dataset where version = 0" "2"
    run_liquibase "$PLATFORM_DB" "$wrapper" rollback -Dliquibase.rollbackCount=1
    assert_scalar "$PLATFORM_DB" "select count(*) from information_schema.columns where table_name = 'catalog_dataset' and column_name = 'version'" "0"
    assert_scalar "$PLATFORM_DB" "select count(*) from catalog_dataset" "2"
    run_liquibase "$PLATFORM_DB" "$wrapper" update
    assert_scalar "$PLATFORM_DB" "select count(*) from catalog_dataset where version = 0" "2"
}

run_analytics() {
    local wrapper="$TMP_DIR/analytics.xml"
    write_wrapper "$wrapper" "$REPO_ROOT/source/dts-analytics/src/main/resources/config/liquibase/changelog/0054_platform_database_registration.xml"
    run_liquibase "$ANALYTICS_DB" "$wrapper" update
    assert_scalar "$ANALYTICS_DB" "select count(*) from analytics_database where tenant_id is null and platform_data_source_id is null" "1"
    assert_scalar "$ANALYTICS_DB" "select count(*) from pg_constraint where conname = 'uk_analytics_database_tenant_platform_source'" "1"
    psql_database "$ANALYTICS_DB" "insert into analytics_database (id, name, tenant_id, platform_data_source_id) values (2, 'bound', 'tenant-a', '10000000-0000-0000-0000-000000000001')" >/dev/null
    expect_sql_failure "$ANALYTICS_DB" "insert into analytics_database (id, name, tenant_id, platform_data_source_id) values (3, 'duplicate', 'tenant-a', '10000000-0000-0000-0000-000000000001')"
    run_liquibase "$ANALYTICS_DB" "$wrapper" rollback -Dliquibase.rollbackCount=1
    assert_scalar "$ANALYTICS_DB" "select count(*) from information_schema.columns where table_name = 'analytics_database' and column_name in ('tenant_id', 'platform_data_source_id')" "0"
    assert_scalar "$ANALYTICS_DB" "select count(*) from analytics_database" "2"
    run_liquibase "$ANALYTICS_DB" "$wrapper" update
    assert_scalar "$ANALYTICS_DB" "select count(*) from analytics_database where tenant_id is null and platform_data_source_id is null" "2"
    psql_database "$ANALYTICS_DB" "update analytics_database set tenant_id = 'tenant-a', platform_data_source_id = '10000000-0000-0000-0000-000000000001' where id = 2" >/dev/null
    expect_sql_failure "$ANALYTICS_DB" "insert into analytics_database (id, name, tenant_id, platform_data_source_id) values (3, 'duplicate-again', 'tenant-a', '10000000-0000-0000-0000-000000000001')"
}

# Read only the deploy password in a subprocess so `.env` cannot overwrite this
# script's readonly connection settings. Nothing writes the value to stdout.
PG_SUPER_PASSWORD="$(bash -c 'set -a; source "$1"; printf %s "$PG_SUPER_PASSWORD"' -- "$REPO_ROOT/.env")"
[[ -n "$PG_SUPER_PASSWORD" ]] || fail "PG_SUPER_PASSWORD must be set in deploy .env"
PG_DRIVER="$(find "${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"/org/postgresql/postgresql -type f -name 'postgresql-*.jar' -print 2>/dev/null | sort -V | tail -n 1)"
[[ -n "$PG_DRIVER" ]] || fail "PostgreSQL JDBC driver is absent from the local Maven cache"
write_minimal_maven_pom

drop_database "$PLATFORM_DB"
drop_database "$ANALYTICS_DB"
psql_database postgres "create database \"${PLATFORM_DB}\"" >/dev/null
psql_database postgres "create database \"${ANALYTICS_DB}\"" >/dev/null
psql_database "$PLATFORM_DB" "create table catalog_dataset (id uuid primary key, name varchar(128) not null); insert into catalog_dataset (id, name) values ('00000000-0000-0000-0000-000000000001', 'legacy');" >/dev/null
psql_database "$ANALYTICS_DB" "create table analytics_database (id bigint primary key, name varchar(255) not null); insert into analytics_database (id, name) values (1, 'legacy');" >/dev/null

run_platform
run_analytics
echo "[s104-migration] PASS: isolated update, rollback, and re-update completed for both Sprint-104 changeSets"
