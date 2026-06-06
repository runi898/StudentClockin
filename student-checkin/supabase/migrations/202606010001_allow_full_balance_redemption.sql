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

  if p_points_requested > v_balance then
    raise exception 'points cannot exceed current balance';
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
    if v_request.points_requested > v_balance then
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
