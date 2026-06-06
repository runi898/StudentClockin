create or replace function app_private.storage_occurrence_id(p_name text)
returns uuid
language sql
immutable
as $$
  select case
    when split_part(p_name, '/', 1) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      then split_part(p_name, '/', 1)::uuid
    else null
  end
$$;

create or replace function public.set_task_submission_expiry()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  new.retention_days := coalesce(new.retention_days, 30);
  new.expires_at := coalesce(
    new.expires_at,
    now() + make_interval(days => new.retention_days)
  );
  new.uploaded_at := coalesce(new.uploaded_at, now());
  return new;
end;
$$;

drop trigger if exists task_submissions_set_expiry on public.task_submissions;
create trigger task_submissions_set_expiry
before insert on public.task_submissions
for each row
execute function public.set_task_submission_expiry();

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'task-deliveries',
  'task-deliveries',
  true,
  104857600,
  array[
    'image/jpeg',
    'image/png',
    'image/webp',
    'video/mp4',
    'video/quicktime',
    'audio/mpeg',
    'audio/mp4',
    'audio/aac',
    'audio/wav',
    'audio/x-wav',
    'audio/3gpp',
    'audio/ogg'
  ]
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists task_deliveries_select on storage.objects;
create policy task_deliveries_select
on storage.objects
for select
to authenticated
using (
  bucket_id = 'task-deliveries'
  and exists (
    select 1
    from public.task_occurrences o
    where o.id = app_private.storage_occurrence_id(name)
      and (
        app_private.is_parent_for_family(o.family_id)
        or app_private.is_current_child_member(o.child_member_id)
      )
  )
);

drop policy if exists task_deliveries_insert_child on storage.objects;
create policy task_deliveries_insert_child
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'task-deliveries'
  and exists (
    select 1
    from public.task_occurrences o
    where o.id = app_private.storage_occurrence_id(name)
      and app_private.is_current_child_member(o.child_member_id)
  )
);

drop policy if exists task_deliveries_update_child on storage.objects;
create policy task_deliveries_update_child
on storage.objects
for update
to authenticated
using (
  bucket_id = 'task-deliveries'
  and exists (
    select 1
    from public.task_occurrences o
    where o.id = app_private.storage_occurrence_id(name)
      and app_private.is_current_child_member(o.child_member_id)
  )
)
with check (
  bucket_id = 'task-deliveries'
  and exists (
    select 1
    from public.task_occurrences o
    where o.id = app_private.storage_occurrence_id(name)
      and app_private.is_current_child_member(o.child_member_id)
  )
);

drop policy if exists task_deliveries_delete_parent on storage.objects;
create policy task_deliveries_delete_parent
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'task-deliveries'
  and exists (
    select 1
    from public.task_occurrences o
    where o.id = app_private.storage_occurrence_id(name)
      and app_private.is_parent_for_family(o.family_id)
  )
);
