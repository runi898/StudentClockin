create or replace function public.parent_update_task_template(
  p_task_template_id uuid,
  p_name text,
  p_mode text,
  p_delivery_requirement text,
  p_points integer,
  p_target_minutes integer default null,
  p_scheduled_time text default null
)
returns public.task_templates
language plpgsql
security definer
set search_path = public
as $$
declare
  v_context record;
  v_updated public.task_templates%rowtype;
  v_today date := (now() at time zone 'Asia/Shanghai')::date;
begin
  select *
  into v_context
  from public.current_member_context()
  where role = 'parent';

  if v_context.family_id is null then
    raise exception 'PARENT_CONTEXT_REQUIRED';
  end if;

  update public.task_templates t
  set
    name = coalesce(nullif(btrim(p_name), ''), t.name),
    mode = coalesce(p_mode, t.mode),
    delivery_requirement = coalesce(p_delivery_requirement, t.delivery_requirement),
    point_value = coalesce(p_points, t.point_value),
    default_target_duration_seconds = case
      when p_target_minutes is null then null
      else p_target_minutes * 60
    end,
    reminder_time_local = nullif(btrim(coalesce(p_scheduled_time, t.reminder_time_local)), ''),
    updated_at = now()
  where t.id = p_task_template_id
    and t.family_id = v_context.family_id
  returning * into v_updated;

  if v_updated.id is null then
    raise exception 'TASK_TEMPLATE_NOT_FOUND';
  end if;

  update public.task_assignments a
  set
    scheduled_time_local = nullif(btrim(coalesce(p_scheduled_time, a.scheduled_time_local)), ''),
    target_duration_seconds = case
      when p_target_minutes is null then null
      else p_target_minutes * 60
    end,
    updated_at = now()
  where a.family_id = v_context.family_id
    and a.task_template_id = p_task_template_id
    and a.is_active = true;

  update public.task_occurrences o
  set
    task_name_snapshot = v_updated.name,
    mode_snapshot = v_updated.mode,
    delivery_requirement_snapshot = v_updated.delivery_requirement,
    point_value_snapshot = v_updated.point_value,
    target_duration_seconds_snapshot = v_updated.default_target_duration_seconds,
    scheduled_time_local = v_updated.reminder_time_local,
    updated_at = now()
  where o.family_id = v_context.family_id
    and o.task_template_id = p_task_template_id
    and o.local_date >= v_today
    and o.status <> 'completed';

  return v_updated;
end;
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
