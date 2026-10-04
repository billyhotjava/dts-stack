{{ config(materialized='table', tags=['it-fin-demo', 'dwd', 'dimension']) }}

WITH account_clean AS (
    SELECT
        cast(account_code AS varchar(32)) AS account_code,
        cast(account_name AS varchar(100)) AS account_name,
        cast(account_category_raw AS varchar(32)) AS account_category_raw,
        cast(control_type_raw AS varchar(32)) AS control_type_raw,
        upper(
            btrim(
                replace(
                    replace(
                        replace(coalesce(cast(account_category_raw AS text), ''), chr(160), ''),
                        chr(65279),
                        ''
                    ),
                    chr(8203),
                    ''
                )
            )
        ) AS account_category_normalized,
        upper(
            btrim(
                replace(
                    replace(
                        replace(coalesce(cast(control_type_raw AS text), ''), chr(160), ''),
                        chr(65279),
                        ''
                    ),
                    chr(8203),
                    ''
                )
            )
        ) AS control_type_normalized,
        cast(record_status_code AS varchar(32)) AS record_status_code
    FROM {{ source('it_fin_demo_ods', 'budget_account') }}
    WHERE account_code IS NOT NULL
)

SELECT
    account_code,
    account_name,
    account_category_raw,
    control_type_raw,
    CASE
        WHEN account_category_normalized IN ('人员', '人员费', 'PERSONNEL') THEN 'BAC-PERSONNEL'
        WHEN account_category_normalized IN ('设备', '设备费', 'EQUIPMENT') THEN 'BAC-EQUIPMENT'
        WHEN account_category_normalized IN ('材料', '材料费', 'MATERIAL') THEN 'BAC-MATERIAL'
        WHEN account_category_normalized IN ('服务', '服务费', 'SERVICE') THEN 'BAC-SERVICE'
        ELSE 'BAC-UNKNOWN'
    END AS account_category_code,
    CASE
        WHEN control_type_normalized IN ('硬管控', '硬控制', 'HARD') THEN 'BCT-HARD'
        WHEN control_type_normalized IN ('软管控', '软控制', 'SOFT') THEN 'BCT-SOFT'
        ELSE 'BCT-UNKNOWN'
    END AS control_type_code,
    record_status_code
FROM account_clean
