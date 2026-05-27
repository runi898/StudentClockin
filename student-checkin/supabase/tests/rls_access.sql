-- Manual verification checklist for local Supabase:
-- 1. Sign in as a parent account and confirm full-family reads work.
-- 2. Sign in as a child account and confirm only self-scoped tasks/points/redeems are visible.
-- 3. Sign in as an unrelated family and confirm zero rows are visible.

select schemaname, tablename, rowsecurity
from pg_tables
where schemaname = 'public'
  and tablename in (
    'families',
    'profiles',
    'family_members',
    'family_settings',
    'task_templates',
    'task_assignments',
    'task_occurrences',
    'task_sessions',
    'task_submissions',
    'point_ledger',
    'redemption_requests',
    'auth_recovery_audits',
    'notification_channels',
    'notification_events',
    'daily_child_reports',
    'daily_task_reports'
  )
order by tablename;
