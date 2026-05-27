import { createClient } from "jsr:@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL") ?? "",
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
);

Deno.serve(async (req) => {
  const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};
  const { data, error } = await supabase.rpc("recalculate_daily_reports", {
    p_from: body.from ?? null,
    p_to: body.to ?? null,
  });

  if (error) {
    return new Response(JSON.stringify({ ok: false, error: error.message }), {
      status: 500,
      headers: { "content-type": "application/json" },
    });
  }

  return new Response(JSON.stringify({ ok: true, data }), {
    headers: { "content-type": "application/json" },
  });
});
