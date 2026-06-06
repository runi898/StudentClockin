drop function if exists public.parent_today_snapshot();

create function public.parent_today_snapshot()
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

drop function if exists public.parent_create_quick_task(text, text, text, integer, integer, text);

create function public.parent_create_quick_task(
  p_child_member_id uuid,
  p_name text,
  p_mode text,
  p_delivery_requirement text,
  p_points integer,
  p_target_minutes integer default null,
  p_scheduled_time text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_context record;
  v_template_id uuid;
  v_local_today date := (now() at time zone 'Asia/Shanghai')::date;
begin
  select *
  into v_context
  from public.current_member_context();

  if v_context.role is distinct from 'parent' then
    raise exception 'only parent accounts can create tasks';
  end if;

  if nullif(btrim(p_name), '') is null then
    raise exception 'TASK_NAME_REQUIRED';
  end if;

  if p_points is null or p_points <= 0 then
    raise exception 'TASK_POINTS_REQUIRED';
  end if;

  perform 1
  from public.family_members fm
  where fm.id = p_child_member_id
    and fm.family_id = v_context.family_id
    and fm.role = 'child'
    and fm.is_active = true;

  if not found then
    raise exception 'CHILD_MEMBER_NOT_FOUND';
  end if;

  insert into public.task_templates (
    family_id,
    name,
    mode,
    delivery_requirement,
    point_value,
    default_target_duration_seconds,
    reminder_time_local
  )
  values (
    v_context.family_id,
    btrim(p_name),
    p_mode,
    p_delivery_requirement,
    p_points,
    case when p_target_minutes is null then null else p_target_minutes * 60 end,
    p_scheduled_time
  )
  returning id into v_template_id;

  insert into public.task_assignments (
    family_id,
    child_member_id,
    task_template_id,
    schedule_type,
    repeat_rule,
    scheduled_time_local,
    target_duration_seconds,
    starts_on,
    is_active
  )
  values (
    v_context.family_id,
    p_child_member_id,
    v_template_id,
    'recurring',
    'daily',
    p_scheduled_time,
    case when p_target_minutes is null then null else p_target_minutes * 60 end,
    v_local_today,
    true
  );

  perform public.generate_daily_occurrences(v_local_today);

  return v_template_id;
end;
$$;

grant execute on function public.parent_create_quick_task(uuid, text, text, text, integer, integer, text) to anon, authenticated, service_role;
