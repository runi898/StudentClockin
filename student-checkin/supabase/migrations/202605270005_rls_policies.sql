create schema if not exists app_private;

create or replace function app_private.belongs_to_family(p_family_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.family_members fm
    where fm.family_id = p_family_id
      and fm.profile_id = auth.uid()
      and fm.is_active = true
  )
$$;

create or replace function app_private.is_parent_for_family(p_family_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.family_members fm
    where fm.family_id = p_family_id
      and fm.profile_id = auth.uid()
      and fm.role = 'parent'
      and fm.is_active = true
  )
$$;

create or replace function app_private.is_current_child_member(p_member_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.family_members fm
    where fm.id = p_member_id
      and fm.profile_id = auth.uid()
      and fm.role = 'child'
      and fm.is_active = true
  )
$$;

alter table public.families enable row level security;
alter table public.profiles enable row level security;
alter table public.family_members enable row level security;
alter table public.family_settings enable row level security;
alter table public.task_templates enable row level security;
alter table public.task_assignments enable row level security;
alter table public.task_occurrences enable row level security;
alter table public.task_sessions enable row level security;
alter table public.task_submissions enable row level security;
alter table public.point_ledger enable row level security;
alter table public.redemption_requests enable row level security;
alter table public.auth_recovery_audits enable row level security;
alter table public.notification_channels enable row level security;
alter table public.notification_events enable row level security;
alter table public.daily_child_reports enable row level security;
alter table public.daily_task_reports enable row level security;

drop policy if exists families_select_member on public.families;
create policy families_select_member
on public.families
for select
using (app_private.belongs_to_family(id));

drop policy if exists families_insert_authenticated on public.families;
create policy families_insert_authenticated
on public.families
for insert
to authenticated
with check (auth.uid() is not null);

drop policy if exists families_update_parent on public.families;
create policy families_update_parent
on public.families
for update
using (app_private.is_parent_for_family(id))
with check (app_private.is_parent_for_family(id));

drop policy if exists profiles_select_self on public.profiles;
create policy profiles_select_self
on public.profiles
for select
using (id = auth.uid());

drop policy if exists profiles_insert_self on public.profiles;
create policy profiles_insert_self
on public.profiles
for insert
to authenticated
with check (id = auth.uid());

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self
on public.profiles
for update
using (id = auth.uid())
with check (id = auth.uid());

drop policy if exists family_members_parent_manage on public.family_members;
create policy family_members_parent_manage
on public.family_members
for all
using (app_private.is_parent_for_family(family_id))
with check (app_private.is_parent_for_family(family_id));

drop policy if exists family_members_child_select_self on public.family_members;
create policy family_members_child_select_self
on public.family_members
for select
using (profile_id = auth.uid());

drop policy if exists family_settings_parent_manage on public.family_settings;
create policy family_settings_parent_manage
on public.family_settings
for all
using (app_private.is_parent_for_family(family_id))
with check (app_private.is_parent_for_family(family_id));

drop policy if exists family_settings_child_read on public.family_settings;
create policy family_settings_child_read
on public.family_settings
for select
using (app_private.belongs_to_family(family_id));

drop policy if exists task_templates_parent_manage on public.task_templates;
create policy task_templates_parent_manage
on public.task_templates
for all
using (app_private.is_parent_for_family(family_id))
with check (app_private.is_parent_for_family(family_id));

drop policy if exists task_templates_child_read on public.task_templates;
create policy task_templates_child_read
on public.task_templates
for select
using (
  exists (
    select 1
    from public.task_assignments a
    join public.family_members fm on fm.id = a.child_member_id
    where a.task_template_id = task_templates.id
      and fm.profile_id = auth.uid()
  )
);

drop policy if exists task_assignments_parent_manage on public.task_assignments;
create policy task_assignments_parent_manage
on public.task_assignments
for all
using (app_private.is_parent_for_family(family_id))
with check (app_private.is_parent_for_family(family_id));

drop policy if exists task_assignments_child_read on public.task_assignments;
create policy task_assignments_child_read
on public.task_assignments
for select
using (app_private.is_current_child_member(child_member_id));

drop policy if exists task_occurrences_parent_manage on public.task_occurrences;
create policy task_occurrences_parent_manage
on public.task_occurrences
for all
using (app_private.is_parent_for_family(family_id))
with check (app_private.is_parent_for_family(family_id));

drop policy if exists task_occurrences_child_read_update on public.task_occurrences;
create policy task_occurrences_child_read_update
on public.task_occurrences
for select
using (app_private.is_current_child_member(child_member_id));

drop policy if exists task_occurrences_child_update on public.task_occurrences;
create policy task_occurrences_child_update
on public.task_occurrences
for update
using (app_private.is_current_child_member(child_member_id))
with check (app_private.is_current_child_member(child_member_id));

drop policy if exists task_sessions_parent_manage on public.task_sessions;
create policy task_sessions_parent_manage
on public.task_sessions
for all
using (
  exists (
    select 1
    from public.task_occurrences o
    where o.id = task_sessions.occurrence_id
      and app_private.is_parent_for_family(o.family_id)
  )
)
with check (
  exists (
    select 1
    from public.task_occurrences o
    where o.id = task_sessions.occurrence_id
      and app_private.is_parent_for_family(o.family_id)
  )
);

drop policy if exists task_sessions_child_manage on public.task_sessions;
create policy task_sessions_child_manage
on public.task_sessions
for all
using (
  exists (
    select 1
    from public.task_occurrences o
    where o.id = task_sessions.occurrence_id
      and app_private.is_current_child_member(o.child_member_id)
  )
)
with check (
  exists (
    select 1
    from public.task_occurrences o
    where o.id = task_sessions.occurrence_id
      and app_private.is_current_child_member(o.child_member_id)
  )
);

drop policy if exists task_submissions_parent_manage on public.task_submissions;
create policy task_submissions_parent_manage
on public.task_submissions
for all
using (
  exists (
    select 1
    from public.task_occurrences o
    where o.id = task_submissions.occurrence_id
      and app_private.is_parent_for_family(o.family_id)
  )
)
with check (
  exists (
    select 1
    from public.task_occurrences o
    where o.id = task_submissions.occurrence_id
      and app_private.is_parent_for_family(o.family_id)
  )
);

drop policy if exists task_submissions_child_manage on public.task_submissions;
create policy task_submissions_child_manage
on public.task_submissions
for all
using (
  exists (
    select 1
    from public.task_occurrences o
    where o.id = task_submissions.occurrence_id
      and app_private.is_current_child_member(o.child_member_id)
  )
)
with check (
  exists (
    select 1
    from public.task_occurrences o
    where o.id = task_submissions.occurrence_id
      and app_private.is_current_child_member(o.child_member_id)
  )
);

drop policy if exists point_ledger_parent_read on public.point_ledger;
create policy point_ledger_parent_read
on public.point_ledger
for select
using (app_private.is_parent_for_family(family_id));

drop policy if exists point_ledger_child_read on public.point_ledger;
create policy point_ledger_child_read
on public.point_ledger
for select
using (app_private.is_current_child_member(child_member_id));

drop policy if exists redemption_requests_parent_manage on public.redemption_requests;
create policy redemption_requests_parent_manage
on public.redemption_requests
for all
using (app_private.is_parent_for_family(family_id))
with check (app_private.is_parent_for_family(family_id));

drop policy if exists redemption_requests_child_read on public.redemption_requests;
create policy redemption_requests_child_read
on public.redemption_requests
for select
using (app_private.is_current_child_member(child_member_id));

drop policy if exists redemption_requests_child_insert on public.redemption_requests;
create policy redemption_requests_child_insert
on public.redemption_requests
for insert
to authenticated
with check (app_private.is_current_child_member(child_member_id));

drop policy if exists auth_recovery_audits_parent_read on public.auth_recovery_audits;
create policy auth_recovery_audits_parent_read
on public.auth_recovery_audits
for select
using (
  exists (
    select 1
    from public.family_members fm
    where fm.profile_id = auth_recovery_audits.profile_id
      and app_private.is_parent_for_family(fm.family_id)
  )
);

drop policy if exists auth_recovery_audits_self_insert on public.auth_recovery_audits;
create policy auth_recovery_audits_self_insert
on public.auth_recovery_audits
for insert
to authenticated
with check (profile_id = auth.uid());

drop policy if exists notification_channels_parent_manage on public.notification_channels;
create policy notification_channels_parent_manage
on public.notification_channels
for all
using (app_private.is_parent_for_family(family_id))
with check (app_private.is_parent_for_family(family_id));

drop policy if exists notification_events_parent_read on public.notification_events;
create policy notification_events_parent_read
on public.notification_events
for select
using (app_private.is_parent_for_family(family_id));

drop policy if exists daily_child_reports_parent_read on public.daily_child_reports;
create policy daily_child_reports_parent_read
on public.daily_child_reports
for select
using (app_private.is_parent_for_family(family_id));

drop policy if exists daily_child_reports_child_read on public.daily_child_reports;
create policy daily_child_reports_child_read
on public.daily_child_reports
for select
using (app_private.is_current_child_member(child_member_id));

drop policy if exists daily_task_reports_parent_read on public.daily_task_reports;
create policy daily_task_reports_parent_read
on public.daily_task_reports
for select
using (app_private.is_parent_for_family(family_id));
