do $$
declare
  v_parent_id uuid := 'b72878fc-08ff-4f80-95b2-14a92aba7497';
  v_child_id uuid := 'ab1a0e28-f81b-4a9f-b5be-bcb564b83ad3';
  v_family_id uuid;
  v_child_member_id uuid;
  v_today date := (now() at time zone 'Asia/Shanghai')::date;
begin
  insert into public.profiles (id, email, display_name)
  values
    (v_parent_id, 'parent@familycheckin.local', '家长测试账号'),
    (v_child_id, 'child1@familycheckin.local', '小宇')
  on conflict (id) do update
  set
    email = excluded.email,
    display_name = excluded.display_name;

  select id
  into v_family_id
  from public.families
  where name = '内网测试家庭'
  limit 1;

  if v_family_id is null then
    insert into public.families (name, timezone)
    values ('内网测试家庭', 'Asia/Shanghai')
    returning id into v_family_id;
  end if;

  insert into public.family_settings (
    family_id,
    cash_cny_per_10_points,
    min_redeem_points,
    media_retention_days,
    timezone
  )
  values (v_family_id, 2.00, 10, 30, 'Asia/Shanghai')
  on conflict (family_id) do update
  set
    cash_cny_per_10_points = excluded.cash_cny_per_10_points,
    min_redeem_points = excluded.min_redeem_points,
    media_retention_days = excluded.media_retention_days,
    timezone = excluded.timezone;

  insert into public.family_members (
    family_id,
    profile_id,
    role,
    login_name,
    is_active
  )
  values (v_family_id, v_parent_id, 'parent', 'parent', true)
  on conflict do nothing;

  insert into public.family_members (
    family_id,
    profile_id,
    role,
    child_display_name,
    login_name,
    is_active
  )
  values (v_family_id, v_child_id, 'child', '小宇', 'child1', true)
  on conflict do nothing;

  select id
  into v_child_member_id
  from public.family_members
  where family_id = v_family_id
    and profile_id = v_child_id
  limit 1;

  delete from public.task_assignments
  where family_id = v_family_id;

  delete from public.task_templates
  where family_id = v_family_id;

  with inserted_templates as (
    insert into public.task_templates (
      family_id,
      name,
      mode,
      delivery_requirement,
      point_value,
      default_target_duration_seconds,
      reminder_time_local
    )
    values
      (v_family_id, '晨读 20 分钟', 'check_only', 'none', 1, 1200, '07:10'),
      (v_family_id, '英语跟读', 'countdown', 'audio', 2, 900, '07:40'),
      (v_family_id, '数学口算', 'stopwatch', 'photo', 2, 900, '18:00'),
      (v_family_id, '整理书包', 'check_only', 'photo', 1, null, '20:30'),
      (v_family_id, '练字', 'countdown', 'photo', 2, 1200, '19:00'),
      (v_family_id, '跳绳', 'stopwatch', 'video', 3, null, '17:30'),
      (v_family_id, '背古诗', 'check_only', 'none', 1, null, '12:20'),
      (v_family_id, '收拾书桌', 'check_only', 'photo', 1, null, '21:00'),
      (v_family_id, '阅读打卡', 'stopwatch', 'none', 2, null, '20:00'),
      (v_family_id, '钢琴练习', 'countdown', 'audio', 3, 1800, '18:30'),
      (v_family_id, '洗漱准备', 'check_only', 'none', 1, null, '21:15'),
      (v_family_id, '体育拉伸', 'stopwatch', 'video', 2, null, '06:50')
    returning id, default_target_duration_seconds, reminder_time_local
  )
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
    v_family_id,
    v_child_member_id,
    id,
    'recurring',
    'daily',
    reminder_time_local,
    default_target_duration_seconds,
    v_today,
    true
  from inserted_templates;

  delete from public.point_ledger
  where family_id = v_family_id
    and change_type = 'manual_adjust'
    and note = '内网测试初始积分';

  insert into public.point_ledger (
    family_id,
    child_member_id,
    change_type,
    points_delta,
    balance_after,
    note
  )
  values (
    v_family_id,
    v_child_member_id,
    'manual_adjust',
    20,
    20,
    '内网测试初始积分'
  );

  perform public.generate_daily_occurrences(v_today);
end $$;
