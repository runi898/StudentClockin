# Operations

## Parent Password Recovery

1. Parent taps `忘记密码`.
2. App sends password reset email through Supabase Auth.
3. Server writes an `auth_recovery_audits` row with `password_reset_requested`.

## Child Credential Reset

1. Parent opens the child management screen.
2. Parent requests reset for the child's login credential.
3. Backend resets the child auth credential with the service role.
4. Server writes `auth_recovery_audits.action = child_password_reset`.

## DingTalk / Webhook Troubleshooting

- Confirm `notification_channels.is_enabled = true`.
- Verify `config_json.webhook_url` is present.
- Check `notification_events.status` for `failed`.
- Re-send failed events through `send-notifications`.

## Midnight Rollover Checklist

- Verify server schedule runs at `00:00:00 Asia/Shanghai`.
- Confirm `generate_daily_occurrences` inserts one row per active assignment/date.
- Confirm new occurrences start with `status = pending`.
- Confirm prior-day data remains unchanged.

## Media Retention Checklist

- Compare `task_submissions.expires_at` with current time.
- Delete expired storage files only; keep database history intact.
- Re-run `recalculate-reports` after batch cleanup if analytics summaries drift.
