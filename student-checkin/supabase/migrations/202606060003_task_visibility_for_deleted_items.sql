alter table public.task_occurrences
  add column if not exists is_visible boolean not null default true;

update public.task_occurrences
set is_visible = true
where is_visible is null;

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
  join public.task_templates tt
    on tt.id = o.task_template_id
  left join lateral (
    select s.actual_duration_seconds
    from public.task_sessions s
    where s.occurrence_id = o.id
    order by s.created_at desc
    limit 1
  ) latest_session on true
  where coalesce(o.is_visible, true)
    and ((a.is_active = true and tt.is_active = true) or o.status = 'completed')
  order by coalesce(o.scheduled_time_local, '99:99'), o.created_at
$$;

create or replace function public.parent_today_snapshot()
returns table (
  id uuid,
  task_template_id uuid,
  child_member_id uuid,
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
    o.child_member_id,
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
  join public.task_templates tt
    on tt.id = o.task_template_id
  join public.family_members child_member
    on child_member.id = o.child_member_id
  left join lateral (
    select s.actual_duration_seconds
    from public.task_sessions s
    where s.occurrence_id = o.id
    order by s.created_at desc
    limit 1
  ) latest_session on true
  where coalesce(o.is_visible, true)
    and ((a.is_active = true and tt.is_active = true) or o.status = 'completed')
  order by child_member.child_display_name, coalesce(o.scheduled_time_local, '99:99'), o.created_at
$$;

create or replace function public.parent_delete_task_template(
  p_task_template_id uuid
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_context record;
  v_today date := (now() at time zone 'Asia/Shanghai')::date;
  v_deleted integer := 0;
begin
  select *
  into v_context
  from public.current_member_context()
  where role = 'parent';

  if v_context.family_id is null then
    raise exception 'PARENT_CONTEXT_REQUIRED';
  end if;

  update public.task_assignments a
  set
    is_active = false,
    updated_at = now()
  where a.family_id = v_context.family_id
    and a.task_template_id = p_task_template_id
    and a.is_active = true;

  update public.task_occurrences o
  set
    is_visible = false,
    updated_at = now()
  where o.family_id = v_context.family_id
    and o.task_template_id = p_task_template_id
    and o.local_date >= v_today
    and o.status = 'completed'
    and coalesce(o.is_visible, true);

  delete from public.task_occurrences o
  where o.family_id = v_context.family_id
    and o.task_template_id = p_task_template_id
    and o.local_date >= v_today
    and o.status <> 'completed';

  get diagnostics v_deleted = row_count;

  update public.task_templates t
  set
    is_active = false,
    updated_at = now()
  where t.family_id = v_context.family_id
    and t.id = p_task_template_id;

  return v_deleted;
end;
$$;

create or replace function public.parent_delete_task_occurrence(
  p_occurrence_id uuid
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_context record;
  v_occurrence record;
  v_today date := (now() at time zone 'Asia/Shanghai')::date;
  v_deleted integer := 0;
begin
  select *
  into v_context
  from public.current_member_context()
  where role = 'parent';

  if v_context.family_id is null then
    raise exception 'PARENT_CONTEXT_REQUIRED';
  end if;

  select
    o.assignment_id,
    o.task_template_id
  into v_occurrence
  from public.task_occurrences o
  where o.id = p_occurrence_id
    and o.family_id = v_context.family_id
  limit 1;

  if v_occurrence.assignment_id is null then
    raise exception 'TASK_OCCURRENCE_NOT_FOUND';
  end if;

  update public.task_assignments a
  set
    is_active = false,
    updated_at = now()
  where a.family_id = v_context.family_id
    and a.id = v_occurrence.assignment_id
    and a.is_active = true;

  update public.task_occurrences o
  set
    is_visible = false,
    updated_at = now()
  where o.family_id = v_context.family_id
    and o.assignment_id = v_occurrence.assignment_id
    and o.local_date >= v_today
    and o.status = 'completed'
    and coalesce(o.is_visible, true);

  delete from public.task_occurrences o
  where o.family_id = v_context.family_id
    and o.assignment_id = v_occurrence.assignment_id
    and o.local_date >= v_today
    and o.status <> 'completed';

  get diagnostics v_deleted = row_count;

  if not exists (
    select 1
    from public.task_assignments a
    where a.family_id = v_context.family_id
      and a.task_template_id = v_occurrence.task_template_id
      and a.is_active = true
  ) then
    update public.task_templates t
    set
      is_active = false,
      updated_at = now()
    where t.family_id = v_context.family_id
      and t.id = v_occurrence.task_template_id;
  end if;

  return v_deleted;
end;
$$;
