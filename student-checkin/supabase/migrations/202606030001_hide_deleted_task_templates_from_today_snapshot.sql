create or replace function public.child_today_snapshot()
returns table (
  id uuid,
  task_name_snapshot text,
  mode_snapshot text,
  delivery_requirement_snapshot text,
  point_value_snapshot integer,
  target_duration_seconds_snapshot integer,
  scheduled_time_local text,
  status text,
  started_at timestamptz,
  completed_at timestamptz,
  actual_duration_seconds integer
)
language sql
stable
security definer
set search_path = public
as $$
  with member_context as (
    select *
    from public.current_member_context()
    where role = 'child'
  ),
  local_today as (
    select (now() at time zone 'Asia/Shanghai')::date as value
  )
  select
    o.id,
    o.task_name_snapshot,
    o.mode_snapshot,
    o.delivery_requirement_snapshot,
    o.point_value_snapshot,
    o.target_duration_seconds_snapshot,
    o.scheduled_time_local,
    o.status,
    o.started_at,
    o.completed_at,
    latest_session.actual_duration_seconds
  from member_context mc
  join local_today t on true
  join public.task_occurrences o
    on o.child_member_id = mc.member_id
   and o.local_date = t.value
  join public.task_assignments a
    on a.id = o.assignment_id
   and a.is_active = true
  join public.task_templates tt
    on tt.id = o.task_template_id
   and tt.is_active = true
  left join lateral (
    select s.actual_duration_seconds
    from public.task_sessions s
    where s.occurrence_id = o.id
    order by s.created_at desc
    limit 1
  ) latest_session on true
  order by coalesce(o.scheduled_time_local, '99:99'), o.created_at
$$;

drop function if exists public.parent_today_snapshot();

create function public.parent_today_snapshot()
returns table (
  id uuid,
  task_template_id uuid,
  child_name text,
  task_name_snapshot text,
  mode_snapshot text,
  delivery_requirement_snapshot text,
  point_value_snapshot integer,
  target_duration_seconds_snapshot integer,
  scheduled_time_local text,
  status text,
  started_at timestamptz,
  completed_at timestamptz,
  actual_duration_seconds integer
)
language sql
stable
security definer
set search_path = public
as $$
  with member_context as (
    select *
    from public.current_member_context()
    where role = 'parent'
  ),
  local_today as (
    select (now() at time zone 'Asia/Shanghai')::date as value
  )
  select
    o.id,
    o.task_template_id,
    child_member.child_display_name as child_name,
    o.task_name_snapshot,
    o.mode_snapshot,
    o.delivery_requirement_snapshot,
    o.point_value_snapshot,
    o.target_duration_seconds_snapshot,
    o.scheduled_time_local,
    o.status,
    o.started_at,
    o.completed_at,
    latest_session.actual_duration_seconds
  from member_context mc
  join local_today t on true
  join public.task_occurrences o
    on o.family_id = mc.family_id
   and o.local_date = t.value
  join public.task_assignments a
    on a.id = o.assignment_id
   and a.is_active = true
  join public.task_templates tt
    on tt.id = o.task_template_id
   and tt.is_active = true
  join public.family_members child_member
    on child_member.id = o.child_member_id
  left join lateral (
    select s.actual_duration_seconds
    from public.task_sessions s
    where s.occurrence_id = o.id
    order by s.created_at desc
    limit 1
  ) latest_session on true
  order by child_member.child_display_name, coalesce(o.scheduled_time_local, '99:99'), o.created_at
$$;

grant execute on function public.parent_today_snapshot() to anon, authenticated, service_role;
