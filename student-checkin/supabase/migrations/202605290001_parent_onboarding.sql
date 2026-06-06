create or replace function public.member_family_context()
returns table (
  family_name text,
  timezone text,
  media_retention_days integer
)
language sql
stable
security definer
set search_path = public
as $$
  with member_context as (
    select *
    from public.current_member_context()
  )
  select
    f.name as family_name,
    fs.timezone,
    fs.media_retention_days
  from member_context mc
  join public.families f
    on f.id = mc.family_id
  join public.family_settings fs
    on fs.family_id = mc.family_id
  limit 1
$$;

create or replace function public.complete_parent_onboarding(
  p_family_name text,
  p_parent_display_name text,
  p_cash_cny_per_10_points numeric default 1.00,
  p_min_redeem_points integer default 10,
  p_media_retention_days integer default 30,
  p_timezone text default 'Asia/Shanghai'
)
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
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_profile_id uuid := auth.uid();
  v_email text;
  v_family_id uuid;
  v_login_name text;
begin
  if v_profile_id is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  if exists (
    select 1
    from public.family_members fm
    where fm.profile_id = v_profile_id
      and fm.is_active = true
  ) then
    return query
    select *
    from public.current_member_context();
    return;
  end if;

  select u.email
  into v_email
  from auth.users u
  where u.id = v_profile_id;

  if v_email is null or btrim(v_email) = '' then
    raise exception 'EMAIL_REQUIRED';
  end if;

  if btrim(coalesce(p_family_name, '')) = '' then
    raise exception 'FAMILY_NAME_REQUIRED';
  end if;

  if btrim(coalesce(p_parent_display_name, '')) = '' then
    raise exception 'PARENT_NAME_REQUIRED';
  end if;

  insert into public.families (name, timezone)
  values (btrim(p_family_name), coalesce(nullif(btrim(p_timezone), ''), 'Asia/Shanghai'))
  returning id into v_family_id;

  insert into public.family_settings (
    family_id,
    cash_cny_per_10_points,
    min_redeem_points,
    media_retention_days,
    timezone
  )
  values (
    v_family_id,
    coalesce(p_cash_cny_per_10_points, 1.00),
    coalesce(p_min_redeem_points, 10),
    coalesce(p_media_retention_days, 30),
    coalesce(nullif(btrim(p_timezone), ''), 'Asia/Shanghai')
  );

  insert into public.profiles (id, email, display_name)
  values (v_profile_id, v_email, btrim(p_parent_display_name))
  on conflict (id) do update
  set
    email = excluded.email,
    display_name = excluded.display_name;

  v_login_name := split_part(v_email, '@', 1);

  insert into public.family_members (
    family_id,
    profile_id,
    role,
    login_name,
    is_active
  )
  values (
    v_family_id,
    v_profile_id,
    'parent',
    nullif(v_login_name, ''),
    true
  );

  return query
  select *
  from public.current_member_context();
end;
$$;

create or replace function public.parent_child_accounts()
returns table (
  member_id uuid,
  child_name text,
  email text,
  created_at timestamptz
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
    fm.id as member_id,
    fm.child_display_name as child_name,
    p.email,
    fm.created_at
  from member_context mc
  join public.family_members fm
    on fm.family_id = mc.family_id
   and fm.role = 'child'
   and fm.is_active = true
  join public.profiles p
    on p.id = fm.profile_id
  order by fm.created_at asc
$$;

create or replace function public.parent_update_family_settings(
  p_cash_cny_per_10_points numeric,
  p_min_redeem_points integer,
  p_media_retention_days integer
)
returns public.family_settings
language plpgsql
security definer
set search_path = public
as $$
declare
  v_context record;
  v_updated public.family_settings%rowtype;
begin
  select *
  into v_context
  from public.current_member_context()
  where role = 'parent';

  if v_context.family_id is null then
    raise exception 'PARENT_CONTEXT_REQUIRED';
  end if;

  update public.family_settings
  set
    cash_cny_per_10_points = coalesce(p_cash_cny_per_10_points, cash_cny_per_10_points),
    min_redeem_points = coalesce(p_min_redeem_points, min_redeem_points),
    media_retention_days = coalesce(p_media_retention_days, media_retention_days)
  where family_id = v_context.family_id
  returning * into v_updated;

  return v_updated;
end;
$$;
