# API Contract

## Roles

- `parent`: manages family, children, tasks, exchange ratio, notifications, and redemption approval
- `child`: sees own tasks, starts/stops timers, uploads proof, submits redemption requests

## Core Data Contracts

### Task occurrence

```json
{
  "id": "uuid",
  "child_member_id": "uuid",
  "task_template_id": "uuid",
  "local_date": "2026-05-27",
  "task_name_snapshot": "阅读 20 分钟",
  "mode_snapshot": "countdown",
  "delivery_requirement_snapshot": "photo",
  "point_value_snapshot": 1,
  "scheduled_time_local": "19:30:00",
  "status": "pending",
  "started_at": "2026-05-27T11:30:00Z",
  "completed_at": "2026-05-27T11:50:20Z"
}
```

### Redemption request

```json
{
  "id": "uuid",
  "family_id": "uuid",
  "child_member_id": "uuid",
  "points_requested": 10,
  "cash_amount_cny": 1.0,
  "status": "pending",
  "requested_at": "2026-05-27T12:00:00Z",
  "reviewed_at": null
}
```

### Notification event

```json
{
  "family_id": "uuid",
  "event_type": "task_completed",
  "message_text": "小宇 任务《阅读 20 分钟》完成，积分 +1，当前积分 21",
  "payload_json": {
    "child_name": "小宇",
    "task_name": "阅读 20 分钟",
    "points_delta": 1,
    "balance_after": 21
  }
}
```

## Planned Client Flows

1. Parent login / password reset
2. Child login and task list sync
3. Task complete -> media upload -> occurrence update -> point ledger write
4. Child submits redemption -> parent approves / rejects -> ledger updated
5. Scheduled `daily-rollover` and `recalculate-reports`
