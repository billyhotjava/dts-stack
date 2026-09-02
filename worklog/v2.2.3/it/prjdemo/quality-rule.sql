SELECT *
FROM public.prjdemo_dwd_project_task_snapshot
WHERE progress_pct IS NULL
   OR progress_pct < 0
   OR progress_pct > 100;
