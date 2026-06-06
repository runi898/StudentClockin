create or replace function public.parent_update_family_settings(
  p_family_name text,
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

  if nullif(trim(coalesce(p_family_name, '')), '') is not null then
    update public.families
    set
      name = trim(p_family_name),
      updated_at = now()
    where id = v_context.family_id;
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
