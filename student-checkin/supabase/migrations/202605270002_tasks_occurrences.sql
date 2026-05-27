create table if not exists public.task_templates (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  name text not null,
  description text,
  mode text not null check (mode in ('check_only', 'countdown', 'stopwatch')),
  delivery_requirement text not null check (delivery_requirement in ('none', 'photo', 'video', 'audio')),
  point_value integer not null check (point_value >= 0),
  default_target_duration_seconds integer check (default_target_duration_seconds is null or default_target_duration_seconds >= 0),
  reminder_time_local text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.task_assignments (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  child_member_id uuid not null references public.family_members(id) on delete cascade,
  task_template_id uuid not null references public.task_templates(id) on delete cascade,
  schedule_type text not null check (schedule_type in ('recurring', 'one_off')),
  repeat_rule text,
  scheduled_time_local text,
  target_duration_seconds integer check (target_duration_seconds is null or target_duration_seconds >= 0),
  starts_on date not null,
  ends_on date,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint assignment_date_window check (ends_on is null or ends_on >= starts_on)
);

create table if not exists public.task_occurrences (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  assignment_id uuid not null references public.task_assignments(id) on delete cascade,
  child_member_id uuid not null references public.family_members(id) on delete cascade,
  task_template_id uuid not null references public.task_templates(id) on delete cascade,
  local_date date not null,
  task_name_snapshot text not null,
  mode_snapshot text not null check (mode_snapshot in ('check_only', 'countdown', 'stopwatch')),
  delivery_requirement_snapshot text not null check (delivery_requirement_snapshot in ('none', 'photo', 'video', 'audio')),
  point_value_snapshot integer not null check (point_value_snapshot >= 0),
  target_duration_seconds_snapshot integer,
  scheduled_time_local text,
  status text not null check (status in ('pending', 'running', 'completed', 'cancelled')) default 'pending',
  started_at timestamptz,
  completed_at timestamptz,
  completion_method text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (assignment_id, local_date)
);

create or replace function public.task_assignment_occurs_on(
  p_schedule_type text,
  p_repeat_rule text,
  p_starts_on date,
  p_ends_on date,
  p_local_date date
)
returns boolean
language plpgsql
immutable
as $$
declare
  iso_dow text;
  weekday_list text;
begin
  if p_local_date < p_starts_on then
    return false;
  end if;

  if p_ends_on is not null and p_local_date > p_ends_on then
    return false;
  end if;

  if p_schedule_type = 'one_off' then
    return p_local_date = p_starts_on;
  end if;

  if p_repeat_rule is null or btrim(p_repeat_rule) = '' or p_repeat_rule = 'daily' then
    return true;
  end if;

  if p_repeat_rule like 'weekdays:%' then
    iso_dow := extract(isodow from p_local_date)::text;
    weekday_list := split_part(p_repeat_rule, ':', 2);
    return iso_dow = any(string_to_array(weekday_list, ','));
  end if;

  return true;
end;
$$;

create or replace function public.generate_daily_occurrences(p_local_date date)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  inserted_count integer := 0;
begin
  with generated as (
    insert into public.task_occurrences (
      family_id,
      assignment_id,
      child_member_id,
      task_template_id,
      local_date,
      task_name_snapshot,
      mode_snapshot,
      delivery_requirement_snapshot,
      point_value_snapshot,
      target_duration_seconds_snapshot,
      scheduled_time_local
    )
    select
      a.family_id,
      a.id,
      a.child_member_id,
      t.id,
      p_local_date,
      t.name,
      t.mode,
      t.delivery_requirement,
      t.point_value,
      coalesce(a.target_duration_seconds, t.default_target_duration_seconds),
      coalesce(a.scheduled_time_local, t.reminder_time_local)
    from public.task_assignments a
    join public.task_templates t on t.id = a.task_template_id
    where a.is_active = true
      and t.is_active = true
      and public.task_assignment_occurs_on(
        a.schedule_type,
        a.repeat_rule,
        a.starts_on,
        a.ends_on,
        p_local_date
      )
    on conflict (assignment_id, local_date) do nothing
    returning 1
  )
  select count(*) into inserted_count from generated;

  return inserted_count;
end;
$$;

drop trigger if exists task_templates_set_updated_at on public.task_templates;
create trigger task_templates_set_updated_at
before update on public.task_templates
for each row
execute function public.set_updated_at();

drop trigger if exists task_assignments_set_updated_at on public.task_assignments;
create trigger task_assignments_set_updated_at
before update on public.task_assignments
for each row
execute function public.set_updated_at();

drop trigger if exists task_occurrences_set_updated_at on public.task_occurrences;
create trigger task_occurrences_set_updated_at
before update on public.task_occurrences
for each row
execute function public.set_updated_at();
