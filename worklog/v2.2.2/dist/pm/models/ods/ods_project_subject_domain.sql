{{ config(
    materialized='ephemeral',
    tags=['project-management', 'ods']
) }}

-- ODS 表: 项目主体域原始数据
-- 入湖目标表: ods_project_subject_domain
-- 字段数: 31 (不含 id/source_system/import_time)
-- 用途: 执行域进度节点事实

SELECT * FROM ods_project_subject_domain
