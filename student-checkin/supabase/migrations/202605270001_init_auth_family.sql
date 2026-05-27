create extension if not exists pgcrypto;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table if not exists public.families (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  timezone text not null default 'Asia/Shanghai',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text not null unique,
  display_name text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.family_members (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  profile_id uuid references public.profiles(id) on delete cascade,
  role text not null check (role in ('parent', 'child')),
  child_display_name text,
  login_name text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint child_name_required
    check (
      (role = 'child' and child_display_name is not null)
      or role = 'parent'
    )
);

create unique index if not exists family_members_family_login_name_idx
  on public.family_members (family_id, login_name)
  where login_name is not null;

create table if not exists public.family_settings (
  family_id uuid primary key references public.families(id) on delete cascade,
  cash_cny_per_10_points numeric(10,2) not null default 1.00,
  min_redeem_points integer not null default 10 check (min_redeem_points >= 1),
  media_retention_days integer not null default 30 check (media_retention_days >= 1),
  timezone text not null default 'Asia/Shanghai',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

drop trigger if exists families_set_updated_at on public.families;
create trigger families_set_updated_at
before update on public.families
for each row
execute function public.set_updated_at();

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles
for each row
execute function public.set_updated_at();

drop trigger if exists family_members_set_updated_at on public.family_members;
create trigger family_members_set_updated_at
before update on public.family_members
for each row
execute function public.set_updated_at();

drop trigger if exists family_settings_set_updated_at on public.family_settings;
create trigger family_settings_set_updated_at
before update on public.family_settings
for each row
execute function public.set_updated_at();
