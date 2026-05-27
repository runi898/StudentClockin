create table if not exists public.auth_recovery_audits (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  action text not null check (action in ('password_reset_requested', 'child_password_reset')),
  created_at timestamptz not null default now(),
  payload jsonb not null default '{}'::jsonb
);

create table if not exists public.notification_channels (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  channel_type text not null check (channel_type in ('dingtalk', 'webhook', 'wechat_reserved')),
  config_json jsonb not null default '{}'::jsonb,
  is_enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.notification_events (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  child_member_id uuid references public.family_members(id) on delete set null,
  event_type text not null check (event_type in ('task_completed', 'redemption_requested', 'redemption_approved', 'redemption_rejected')),
  message_text text not null,
  payload_json jsonb not null default '{}'::jsonb,
  status text not null check (status in ('pending', 'sent', 'failed')) default 'pending',
  created_at timestamptz not null default now(),
  sent_at timestamptz
);

create table if not exists public.daily_child_reports (
  family_id uuid not null references public.families(id) on delete cascade,
  child_member_id uuid not null references public.family_members(id) on delete cascade,
  local_date date not null,
  task_total_count integer not null default 0,
  task_completed_count integer not null default 0,
  total_duration_seconds integer not null default 0,
  points_awarded integer not null default 0,
  points_redeemed integer not null default 0,
  redemption_cash_cny numeric(10,2) not null default 0,
  updated_at timestamptz not null default now(),
  primary key (child_member_id, local_date)
);

create table if not exists public.daily_task_reports (
  family_id uuid not null references public.families(id) on delete cascade,
  task_template_id uuid not null references public.task_templates(id) on delete cascade,
  local_date date not null,
  assigned_count integer not null default 0,
  completed_count integer not null default 0,
  avg_duration_seconds integer,
  total_points_awarded integer not null default 0,
  updated_at timestamptz not null default now(),
  primary key (task_template_id, local_date)
);

create or replace function public.recalculate_daily_reports(
  p_from date default null,
  p_to date default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_from date := coalesce(p_from, current_date - 31);
  v_to date := coalesce(p_to, current_date);
begin
  delete from public.daily_child_reports
  where local_date between v_from and v_to;

  with occurrence_stats as (
    select
      o.family_id,
      o.child_member_id,
      o.local_date,
      count(*)::integer as task_total_count,
      count(*) filter (where o.status = 'completed')::integer as task_completed_count,
      coalesce(sum(s.actual_duration_seconds), 0)::integer as total_duration_seconds
    from public.task_occurrences o
    left join public.task_sessions s on s.occurrence_id = o.id
    where o.local_date between v_from and v_to
    group by o.family_id, o.child_member_id, o.local_date
  ),
  point_stats as (
    select
      l.family_id,
      l.child_member_id,
      (l.created_at at time zone 'Asia/Shanghai')::date as local_date,
      coalesce(sum(case when l.change_type = 'task_reward' then l.points_delta else 0 end), 0)::integer as points_awarded,
      coalesce(sum(case when l.change_type = 'redemption_approved' then abs(l.points_delta) else 0 end), 0)::integer as points_redeemed
    from public.point_ledger l
    where (l.created_at at time zone 'Asia/Shanghai')::date between v_from and v_to
    group by l.family_id, l.child_member_id, (l.created_at at time zone 'Asia/Shanghai')::date
  ),
  redemption_stats as (
    select
      r.family_id,
      r.child_member_id,
      (coalesce(r.reviewed_at, r.requested_at) at time zone 'Asia/Shanghai')::date as local_date,
      coalesce(sum(case when r.status = 'approved' then r.cash_amount_cny else 0 end), 0)::numeric(10,2) as redemption_cash_cny
    from public.redemption_requests r
    where (coalesce(r.reviewed_at, r.requested_at) at time zone 'Asia/Shanghai')::date between v_from and v_to
    group by r.family_id, r.child_member_id, (coalesce(r.reviewed_at, r.requested_at) at time zone 'Asia/Shanghai')::date
  )
  insert into public.daily_child_reports (
    family_id,
    child_member_id,
    local_date,
    task_total_count,
    task_completed_count,
    total_duration_seconds,
    points_awarded,
    points_redeemed,
    redemption_cash_cny
  )
  select
    o.family_id,
    o.child_member_id,
    o.local_date,
    o.task_total_count,
    o.task_completed_count,
    o.total_duration_seconds,
    coalesce(p.points_awarded, 0),
    coalesce(p.points_redeemed, 0),
    coalesce(r.redemption_cash_cny, 0)
  from occurrence_stats o
  left join point_stats p
    on p.child_member_id = o.child_member_id
   and p.local_date = o.local_date
  left join redemption_stats r
    on r.child_member_id = o.child_member_id
   and r.local_date = o.local_date;

  delete from public.daily_task_reports
  where local_date between v_from and v_to;

  with task_occurrence_stats as (
    select
      o.family_id,
      o.task_template_id,
      o.local_date,
      count(*)::integer as assigned_count,
      count(*) filter (where o.status = 'completed')::integer as completed_count,
      avg(s.actual_duration_seconds)::integer as avg_duration_seconds
    from public.task_occurrences o
    left join public.task_sessions s on s.occurrence_id = o.id
    where o.local_date between v_from and v_to
    group by o.family_id, o.task_template_id, o.local_date
  ),
  task_point_stats as (
    select
      o.task_template_id,
      o.local_date,
      coalesce(sum(l.points_delta), 0)::integer as total_points_awarded
    from public.task_occurrences o
    join public.point_ledger l
      on l.occurrence_id = o.id
     and l.change_type = 'task_reward'
    where o.local_date between v_from and v_to
    group by o.task_template_id, o.local_date
  )
  insert into public.daily_task_reports (
    family_id,
    task_template_id,
    local_date,
    assigned_count,
    completed_count,
    avg_duration_seconds,
    total_points_awarded
  )
  select
    s.family_id,
    s.task_template_id,
    s.local_date,
    s.assigned_count,
    s.completed_count,
    s.avg_duration_seconds,
    coalesce(p.total_points_awarded, 0)
  from task_occurrence_stats s
  left join task_point_stats p
    on p.task_template_id = s.task_template_id
   and p.local_date = s.local_date;

  return jsonb_build_object(
    'from', v_from,
    'to', v_to,
    'ok', true
  );
end;
$$;

drop trigger if exists notification_channels_set_updated_at on public.notification_channels;
create trigger notification_channels_set_updated_at
before update on public.notification_channels
for each row
execute function public.set_updated_at();
