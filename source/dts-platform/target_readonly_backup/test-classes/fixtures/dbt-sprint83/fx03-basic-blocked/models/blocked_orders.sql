{{ config(tags=['dwd', 'domain:sprint83_demo']) }}
select * from {{ ref('missing_orders') }}
union all
select * from {{ ref(var('dynamic_model')) }}
