\set ON_ERROR_STOP on

\prompt 'Type DROP_IT_FIN_DEMO_SRC to remove only schema it_fin_demo_src: ' confirm_fin_demo_cleanup

SELECT :'confirm_fin_demo_cleanup' = 'DROP_IT_FIN_DEMO_SRC' AS confirmed \gset

\if :confirmed
DROP SCHEMA IF EXISTS it_fin_demo_src CASCADE;
\echo 'Removed schema it_fin_demo_src and its three synthetic source tables.'
\else
\echo 'Cleanup aborted. No schema was removed.'
\endif
