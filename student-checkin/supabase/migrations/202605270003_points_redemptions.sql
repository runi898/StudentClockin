create table if not exists public.task_sessions (
  id uuid primary key default gen_random_uuid(),
  occurrence_id uuid not null references public.task_occurrences(id) on delete cascade,
  started_at timestamptz not null,
  ended_at timestamptz,
  actual_duration_seconds integer,
  alarm_fired_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.task_submissions (
  id uuid primary key default gen_random_uuid(),
  occurrence_id uuid not null references public.task_occurrences(id) on delete cascade,
  submission_type text not null check (submission_type in ('photo', 'video', 'audio')),
  storage_path text not null,
  uploaded_at timestamptz not null default now(),
  retention_days integer not null default 30,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);

create table if not exists public.redemption_requests (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  child_member_id uuid not null references public.family_members(id) on delete cascade,
  points_requested integer not null check (points_requested >= 10),
  cash_amount_cny numeric(10,2) not null,
  status text not null check (status in ('pending', 'approved', 'rejected')) default 'pending',
  requested_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references public.profiles(id) on delete set null,
  note text
);

create table if not exists public.point_ledger (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  child_member_id uuid not null references public.family_members(id) on delete cascade,
  occurrence_id uuid references public.task_occurrences(id) on delete set null,
  redemption_request_id uuid references public.redemption_requests(id) on delete set null,
  change_type text not null check (change_type in ('task_reward', 'redemption_approved', 'manual_adjust')),
  points_delta integer not null,
  balance_after integer not null,
  created_at timestamptz not null default now(),
  note text
);

create or replace function public.current_points_balance(p_child_member_id uuid)
returns integer
language sql
stable
as $$
  select coalesce(sum(points_delta), 0)::integer
  from public.point_ledger
  where child_member_id = p_child_member_id
$$;

create or replace function public.calculate_redemption_cash(
  p_points_requested integer,
  p_cash_cny_per_10_points numeric
)
returns numeric
language sql
immutable
as $$
  select round((p_points_requested::numeric / 10.0) * p_cash_cny_per_10_points, 2)
$$;

drop trigger if exists task_sessions_set_updated_at on public.task_sessions;
create trigger task_sessions_set_updated_at
before update on public.task_sessions
for each row
execute function public.set_updated_at();
