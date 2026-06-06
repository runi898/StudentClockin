import { query } from "./db.js";

async function dispatchChannel(channel, messageText) {
  const webhookUrl = channel.config_json?.webhook_url;
  if (!webhookUrl) {
    return { ok: false, error: "MISSING_WEBHOOK_URL" };
  }

  const payload =
    channel.channel_type === "dingtalk"
      ? { msgtype: "text", text: { content: messageText } }
      : { text: messageText };

  const response = await fetch(webhookUrl, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload)
  });

  return response.ok
    ? { ok: true }
    : { ok: false, error: `HTTP_${response.status}` };
}

export async function sendNotificationMessage(familyId, messageText) {
  const { rows } = await query(
    `
      select channel_type, config_json
      from public.notification_channels
      where family_id = $1
        and is_enabled = true
      order by created_at asc
    `,
    [familyId]
  );

  const results = [];
  for (const channel of rows) {
    try {
      results.push(await dispatchChannel(channel, messageText));
    } catch (error) {
      results.push({
        ok: false,
        error: error instanceof Error ? error.message : "DISPATCH_FAILED"
      });
    }
  }

  return results;
}

export async function createNotificationEvent({
  familyId,
  childMemberId = null,
  eventType,
  messageText,
  payload = {}
}) {
  const { rows } = await query(
    `
      insert into public.notification_events (
        family_id,
        child_member_id,
        event_type,
        message_text,
        payload_json
      )
      values ($1, $2, $3, $4, $5::jsonb)
      returning id
    `,
    [familyId, childMemberId, eventType, messageText, JSON.stringify(payload)]
  );

  const eventId = rows[0].id;
  const results = await sendNotificationMessage(familyId, messageText);
  const failed = results.some((result) => !result.ok);

  await query(
    `
      update public.notification_events
      set status = $2, sent_at = case when $2 = 'sent' then now() else null end
      where id = $1
    `,
    [eventId, failed ? "failed" : "sent"]
  );

  return { eventId, results };
}
