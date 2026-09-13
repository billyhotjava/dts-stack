select
  count(*) filter (where m.match_status = 'MATCHED') as matched_count,
  count(*) filter (where m.match_status = 'UNMATCHED') as unmatched_count,
  count(*) filter (where m.match_status = 'MANUAL_REVIEW') as manual_review_count,
  count(*) as mapping_count
from catalog_asset_mapping m;

select
  m.match_status,
  m.confidence,
  m.fqn,
  m.match_reason
from catalog_asset_mapping m
where coalesce(m.match_status, 'UNMATCHED') <> 'MATCHED'
order by m.last_checked_at desc nulls last, m.fqn asc
limit 100;
