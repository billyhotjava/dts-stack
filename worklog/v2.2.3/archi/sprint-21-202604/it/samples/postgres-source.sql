-- Sprint-21 Connector Center smoke sample for PostgreSQL-compatible sources.
-- Usage:
--   psql "$SOURCE_DATABASE_URL" -f worklog/v2.2.3/sprint-21-202604/it/samples/postgres-source.sql

create schema if not exists dts_smoke;

drop table if exists dts_smoke.erp_project;

create table dts_smoke.erp_project (
    project_id varchar(32) primary key,
    project_name varchar(128) not null,
    owner_name varchar(64),
    budget_amount numeric(18, 2),
    progress_pct numeric(6, 2),
    update_time timestamp not null,
    is_active boolean default true
);

insert into dts_smoke.erp_project (
    project_id,
    project_name,
    owner_name,
    budget_amount,
    progress_pct,
    update_time,
    is_active
) values
    ('P-1001', 'Connector Center baseline', 'ops', 120000.00, 35.50, timestamp '2026-04-29 10:00:00', true),
    ('P-1002', 'ODS lineage smoke', 'data', 86000.00, 61.00, timestamp '2026-04-29 11:00:00', true),
    ('P-1003', 'Precheck threshold case', 'qa', 42000.00, 15.00, timestamp '2026-04-29 12:00:00', true);
