create or replace function public.current_member_context()
returns table (
  profile_id uuid,
  family_id uuid,
  member_id uuid,
  role text,
  display_name text,
  email text,
  cash_cny_per_10_points numeric,
  min_redeem_points integer
)
language sql
stable
security definer
set search_path = public
as $$
  select
    p.id as profile_id,
    fm.family_id,
    fm.id as member_id,
    fm.role,
    coalesce(fm.child_display_name, p.display_name) as display_name,
    p.email,
    fs.cash_cny_per_10_points,
    fs.min_redeem_points
  from public.profiles p
  join public.family_members fm
    on fm.profile_id = p.id
   and fm.is_active = true
  join public.family_settings fs
    on fs.family_id = fm.family_id
  where p.id = auth.uid()
  order by case when fm.role = 'parent' then 0 else 1 end
  limit 1
$$;

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
  left join lateral (
    select s.actual_duration_seconds
    from public.task_sessions s
    where s.occurrence_id = o.id
    order by s.created_at desc
    limit 1
  ) latest_session on true
  order by coalesce(o.scheduled_time_local, '99:99'), o.created_at
$$;

create or replace function public.parent_today_snapshot()
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

create or replace function public.parent_redemption_stats()
returns table (
  last_7_days_count integer,
  last_7_days_cash_cny numeric,
  current_month_count integer,
  current_month_cash_cny numeric
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
  stats as (
    select
      count(*) filter (
        where (requested_at at time zone 'Asia/Shanghai')::date >= ((now() at time zone 'Asia/Shanghai')::date - 6)
      )::integer as last_7_days_count,
      coalesce(sum(case
        when status = 'approved'
         and (coalesce(reviewed_at, requested_at) at time zone 'Asia/Shanghai')::date >= ((now() at time zone 'Asia/Shanghai')::date - 6)
        then cash_amount_cny else 0 end), 0)::numeric as last_7_days_cash_cny,
      count(*) filter (
        where date_trunc('month', requested_at at time zone 'Asia/Shanghai') =
          date_trunc('month', now() at time zone 'Asia/Shanghai')
      )::integer as current_month_count,
      coalesce(sum(case
        when status = 'approved'
         and date_trunc('month', coalesce(reviewed_at, requested_at) at time zone 'Asia/Shanghai') =
             date_trunc('month', now() at time zone 'Asia/Shanghai')
        then cash_amount_cny else 0 end), 0)::numeric as current_month_cash_cny
    from public.redemption_requests r
    join member_context mc on mc.family_id = r.family_id
  )
  select *
  from stats
$$;

create or replace function public.parent_redemption_list()
returns table (
  id uuid,
  child_name text,
  points_requested integer,
  cash_amount_cny numeric,
  requested_at timestamptz,
  status text
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
  )
  select
    r.id,
    fm.child_display_name as child_name,
    r.points_requested,
    r.cash_amount_cny,
    r.requested_at,
    r.status
  from public.redemption_requests r
  join member_context mc
    on mc.family_id = r.family_id
  join public.family_members fm
    on fm.id = r.child_member_id
  order by r.requested_at desc
$$;

create or replace function public.handle_occurrence_reward()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_balance integer;
begin
  if new.status = 'completed' and coalesce(old.status, '') <> 'completed' then
    if not exists (
      select 1
      from public.point_ledger l
      where l.occurrence_id = new.id
        and l.change_type = 'task_reward'
    ) then
      v_balance := public.current_points_balance(new.child_member_id) + new.point_value_snapshot;

      insert into public.point_ledger (
        family_id,
        child_member_id,
        occurrence_id,
        change_type,
        points_delta,
        balance_after,
        note
      )
      values (
        new.family_id,
        new.child_member_id,
        new.id,
        'task_reward',
        new.point_value_snapshot,
        v_balance,
        new.task_name_snapshot
      );
    end if;
  end if;

  return new;
end;
$$;

drop trigger if exists task_occurrences_reward_after_complete on public.task_occurrences;
create trigger task_occurrences_reward_after_complete
after update of status, completed_at on public.task_occurrences
for each row
execute function public.handle_occurrence_reward();

create or replace function public.handle_redemption_approved_ledger()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_balance integer;
begin
  if new.status = 'approved' and coalesce(old.status, '') <> 'approved' then
    v_balance := public.current_points_balance(new.child_member_id) - new.points_requested;

    if v_balance < 0 then
      raise exception 'insufficient points balance';
    end if;

    if not exists (
      select 1
      from public.point_ledger l
      where l.redemption_request_id = new.id
        and l.change_type = 'redemption_approved'
    ) then
      insert into public.point_ledger (
        family_id,
        child_member_id,
        redemption_request_id,
        change_type,
        points_delta,
        balance_after,
        note
      )
      values (
        new.family_id,
        new.child_member_id,
        new.id,
        'redemption_approved',
        -new.points_requested,
        v_balance,
        coalesce(new.note, 'redemption approved')
      );
    end if;
  end if;

  return new;
end;
$$;

drop trigger if exists redemption_requests_apply_ledger_after_approve on public.redemption_requests;
create trigger redemption_requests_apply_ledger_after_approve
after update of status on public.redemption_requests
for each row
execute function public.handle_redemption_approved_ledger();

create or replace function public.child_start_task(
  p_occurrence_id uuid,
  p_started_at timestamptz default now()
)
returns public.task_occurrences
language plpgsql
security definer
set search_path = public
as $$
declare
  v_occurrence public.task_occurrences;
begin
  update public.task_occurrences o
  set
    status = 'running',
    started_at = coalesce(o.started_at, p_started_at),
    updated_at = now()
  where o.id = p_occurrence_id
    and exists (
      select 1
      from public.family_members fm
      where fm.id = o.child_member_id
        and fm.profile_id = auth.uid()
        and fm.role = 'child'
        and fm.is_active = true
    )
  returning * into v_occurrence;

  if v_occurrence.id is null then
    raise exception 'occurrence not found or access denied';
  end if;

  if not exists (
    select 1
    from public.task_sessions s
    where s.occurrence_id = p_occurrence_id
      and s.ended_at is null
  ) then
    insert into public.task_sessions (occurrence_id, started_at)
    values (p_occurrence_id, coalesce(v_occurrence.started_at, p_started_at));
  end if;

  return v_occurrence;
end;
$$;

create or replace function public.child_complete_task(
  p_occurrence_id uuid,
  p_completed_at timestamptz default now()
)
returns public.task_occurrences
language plpgsql
security definer
set search_path = public
as $$
declare
  v_occurrence public.task_occurrences;
begin
  update public.task_occurrences o
  set
    status = 'completed',
    started_at = coalesce(o.started_at, p_completed_at),
    completed_at = p_completed_at,
    completion_method = 'child_check',
    updated_at = now()
  where o.id = p_occurrence_id
    and exists (
      select 1
      from public.family_members fm
      where fm.id = o.child_member_id
        and fm.profile_id = auth.uid()
        and fm.role = 'child'
        and fm.is_active = true
    )
  returning * into v_occurrence;

  if v_occurrence.id is null then
    raise exception 'occurrence not found or access denied';
  end if;

  if not exists (
    select 1
    from public.task_sessions s
    where s.occurrence_id = p_occurrence_id
  ) then
    insert into public.task_sessions (
      occurrence_id,
      started_at,
      ended_at,
      actual_duration_seconds
    )
    values (
      p_occurrence_id,
      v_occurrence.started_at,
      p_completed_at,
      greatest(extract(epoch from (p_completed_at - v_occurrence.started_at))::integer, 0)
    );
  else
    update public.task_sessions
    set
      ended_at = coalesce(ended_at, p_completed_at),
      actual_duration_seconds = coalesce(
        actual_duration_seconds,
        greatest(extract(epoch from (p_completed_at - coalesce(started_at, p_completed_at)))::integer, 0)
      ),
      updated_at = now()
    where occurrence_id = p_occurrence_id
      and ended_at is null;
  end if;

  return v_occurrence;
end;
$$;

create or replace function public.child_finish_task(
  p_occurrence_id uuid,
  p_completed_at timestamptz default now(),
  p_actual_duration_seconds integer default 0
)
returns public.task_occurrences
language plpgsql
security definer
set search_path = public
as $$
declare
  v_occurrence public.task_occurrences;
  v_started_at timestamptz;
  v_duration integer;
begin
  select o.started_at
  into v_started_at
  from public.task_occurrences o
  where o.id = p_occurrence_id;

  v_duration := case
    when p_actual_duration_seconds > 0 then p_actual_duration_seconds
    when v_started_at is not null then greatest(extract(epoch from (p_completed_at - v_started_at))::integer, 0)
    else 0
  end;

  update public.task_occurrences o
  set
    status = 'completed',
    started_at = coalesce(o.started_at, p_completed_at),
    completed_at = p_completed_at,
    completion_method = 'child_timer',
    updated_at = now()
  where o.id = p_occurrence_id
    and exists (
      select 1
      from public.family_members fm
      where fm.id = o.child_member_id
        and fm.profile_id = auth.uid()
        and fm.role = 'child'
        and fm.is_active = true
    )
  returning * into v_occurrence;

  if v_occurrence.id is null then
    raise exception 'occurrence not found or access denied';
  end if;

  if exists (
    select 1
    from public.task_sessions s
    where s.occurrence_id = p_occurrence_id
      and s.ended_at is null
  ) then
    update public.task_sessions
    set
      ended_at = p_completed_at,
      actual_duration_seconds = v_duration,
      updated_at = now()
    where occurrence_id = p_occurrence_id
      and ended_at is null;
  else
    insert into public.task_sessions (
      occurrence_id,
      started_at,
      ended_at,
      actual_duration_seconds
    )
    values (
      p_occurrence_id,
      coalesce(v_occurrence.started_at, p_completed_at),
      p_completed_at,
      v_duration
    );
  end if;

  return v_occurrence;
end;
$$;

create or replace function public.child_submit_redemption(
  p_points_requested integer
)
returns public.redemption_requests
language plpgsql
security definer
set search_path = public
as $$
declare
  v_context record;
  v_balance integer;
  v_cash numeric;
  v_request public.redemption_requests;
begin
  select *
  into v_context
  from public.current_member_context();

  if v_context.role is distinct from 'child' then
    raise exception 'only child accounts can request redemption';
  end if;

  v_balance := public.current_points_balance(v_context.member_id);

  if p_points_requested < v_context.min_redeem_points then
    raise exception 'points below family minimum';
  end if;

  if p_points_requested >= v_balance then
    raise exception 'points must remain below current balance';
  end if;

  v_cash := public.calculate_redemption_cash(
    p_points_requested,
    v_context.cash_cny_per_10_points
  );

  insert into public.redemption_requests (
    family_id,
    child_member_id,
    points_requested,
    cash_amount_cny,
    status
  )
  values (
    v_context.family_id,
    v_context.member_id,
    p_points_requested,
    v_cash,
    'pending'
  )
  returning * into v_request;

  return v_request;
end;
$$;

create or replace function public.parent_review_redemption(
  p_request_id uuid,
  p_approve boolean,
  p_note text default null
)
returns public.redemption_requests
language plpgsql
security definer
set search_path = public
as $$
declare
  v_context record;
  v_balance integer;
  v_request public.redemption_requests;
begin
  select *
  into v_context
  from public.current_member_context();

  if v_context.role is distinct from 'parent' then
    raise exception 'only parent accounts can review redemption';
  end if;

  select *
  into v_request
  from public.redemption_requests r
  where r.id = p_request_id
    and r.family_id = v_context.family_id;

  if v_request.id is null then
    raise exception 'redemption request not found';
  end if;

  if p_approve then
    v_balance := public.current_points_balance(v_request.child_member_id);
    if v_request.points_requested >= v_balance then
      raise exception 'insufficient points for approval';
    end if;
  end if;

  update public.redemption_requests r
  set
    status = case when p_approve then 'approved' else 'rejected' end,
    reviewed_at = now(),
    reviewed_by = auth.uid(),
    note = p_note
  where r.id = p_request_id
  returning * into v_request;

  return v_request;
end;
$$;

create or replace function public.parent_create_quick_task(
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
    p_name,
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
  select
    v_context.family_id,
    fm.id,
    v_template_id,
    'recurring',
    'daily',
    p_scheduled_time,
    case when p_target_minutes is null then null else p_target_minutes * 60 end,
    v_local_today,
    true
  from public.family_members fm
  where fm.family_id = v_context.family_id
    and fm.role = 'child'
    and fm.is_active = true;

  perform public.generate_daily_occurrences(v_local_today);

  return v_template_id;
end;
$$;

create or replace function public.parent_reset_today_occurrences()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_context record;
  v_local_today date := (now() at time zone 'Asia/Shanghai')::date;
  v_count integer := 0;
begin
  select *
  into v_context
  from public.current_member_context();

  if v_context.role is distinct from 'parent' then
    raise exception 'only parent accounts can reset occurrences';
  end if;

  delete from public.point_ledger l
  using public.task_occurrences o
  where o.id = l.occurrence_id
    and o.family_id = v_context.family_id
    and o.local_date = v_local_today
    and l.change_type = 'task_reward';

  delete from public.task_sessions s
  using public.task_occurrences o
  where o.id = s.occurrence_id
    and o.family_id = v_context.family_id
    and o.local_date = v_local_today;

  update public.task_occurrences o
  set
    status = 'pending',
    started_at = null,
    completed_at = null,
    completion_method = null,
    updated_at = now()
  where o.family_id = v_context.family_id
    and o.local_date = v_local_today;

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;
