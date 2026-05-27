begin;

select public.generate_daily_occurrences(date '2026-05-28') as inserted_count;

select
  assignment_id,
  child_member_id,
  local_date,
  status
from public.task_occurrences
where local_date = date '2026-05-28';

rollback;
