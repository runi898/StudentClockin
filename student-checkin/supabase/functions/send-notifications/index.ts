import { createClient } from "jsr:@supabase/supabase-js@2";

type NotificationChannel = {
  channel_type: "dingtalk" | "webhook" | "wechat_reserved";
  config_json: Record<string, string>;
};

const supabase = createClient(
  Deno.env.get("SUPABASE_URL") ?? "",
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
);

async function dispatch(channel: NotificationChannel, message: string) {
  const webhookUrl = channel.config_json.webhook_url;

  if (!webhookUrl) {
    return { ok: false, error: "missing webhook_url" };
  }

  const payload =
    channel.channel_type === "dingtalk"
      ? { msgtype: "text", text: { content: message } }
      : { text: message };

  const response = await fetch(webhookUrl, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload),
  });

  return response.ok
    ? { ok: true }
    : { ok: false, error: `http_${response.status}` };
}

Deno.serve(async (req) => {
  const event = await req.json();
  const familyId = event.family_id as string;
  const message = event.message_text as string;

  const { data: channels, error } = await supabase
    .from("notification_channels")
    .select("channel_type, config_json")
    .eq("family_id", familyId)
    .eq("is_enabled", true);

  if (error) {
    return new Response(JSON.stringify({ ok: false, error: error.message }), {
      status: 500,
      headers: { "content-type": "application/json" },
    });
  }

  const results = await Promise.all(
    (channels ?? []).map((channel) =>
      dispatch(channel as NotificationChannel, message)
    )
  );

  return new Response(JSON.stringify({ ok: true, message, results }), {
    headers: { "content-type": "application/json" },
  });
});
