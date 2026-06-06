import { createClient } from "jsr:@supabase/supabase-js@2";

type ManageChildPayload = {
  action?: "reset_password" | "delete_child" | "rename_child";
  member_id?: string;
  new_password?: string;
  child_name?: string;
};

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const supabase = createClient(supabaseUrl, serviceRoleKey);

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return jsonResponse(405, { error: "METHOD_NOT_ALLOWED" });
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return jsonResponse(401, { error: "AUTH_REQUIRED" });
  }

  const jwt = authHeader.replace("Bearer ", "").trim();
  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser(jwt);

  if (authError || !user) {
    return jsonResponse(401, { error: authError?.message ?? "INVALID_TOKEN" });
  }

  const { data: parentMember, error: parentError } = await supabase
    .from("family_members")
    .select("family_id, role")
    .eq("profile_id", user.id)
    .eq("role", "parent")
    .eq("is_active", true)
    .maybeSingle();

  if (parentError) {
    return jsonResponse(500, { error: parentError.message });
  }

  if (!parentMember?.family_id) {
    return jsonResponse(403, { error: "PARENT_CONTEXT_REQUIRED" });
  }

  let payload: ManageChildPayload;
  try {
    payload = await req.json();
  } catch {
    return jsonResponse(400, { error: "INVALID_JSON" });
  }

  const memberId = payload.member_id?.trim();
  if (!memberId) {
    return jsonResponse(400, { error: "MEMBER_ID_REQUIRED" });
  }

  const { data: childMember, error: childError } = await supabase
    .from("family_members")
    .select("id, family_id, profile_id, role, child_display_name")
    .eq("id", memberId)
    .eq("family_id", parentMember.family_id)
    .eq("role", "child")
    .eq("is_active", true)
    .maybeSingle();

  if (childError) {
    return jsonResponse(500, { error: childError.message });
  }

  if (!childMember?.profile_id) {
    return jsonResponse(404, { error: "CHILD_ACCOUNT_NOT_FOUND" });
  }

  if (payload.action === "reset_password") {
    const newPassword = payload.new_password?.trim();
    if (!newPassword || newPassword.length < 6) {
      return jsonResponse(400, { error: "NEW_PASSWORD_TOO_SHORT" });
    }

    const { error: updateError } = await supabase.auth.admin.updateUserById(childMember.profile_id, {
      password: newPassword,
    });

    if (updateError) {
      return jsonResponse(400, { error: updateError.message });
    }

    await supabase.from("auth_recovery_audits").insert({
      profile_id: childMember.profile_id,
      action: "child_password_reset",
      payload: {
        member_id: childMember.id,
        child_name: childMember.child_display_name,
        reset_by: user.id,
      },
    });

    return jsonResponse(200, {
      ok: true,
      action: "reset_password",
      member_id: childMember.id,
    });
  }

  if (payload.action === "rename_child") {
    const childName = payload.child_name?.trim();
    if (!childName) {
      return jsonResponse(400, { error: "CHILD_NAME_REQUIRED" });
    }

    const { error: renameError } = await supabase
      .from("family_members")
      .update({
        child_display_name: childName,
        updated_at: new Date().toISOString(),
      })
      .eq("id", childMember.id)
      .eq("family_id", parentMember.family_id);

    if (renameError) {
      return jsonResponse(400, { error: renameError.message });
    }

    return jsonResponse(200, {
      ok: true,
      action: "rename_child",
      member_id: childMember.id,
      child_name: childName,
    });
  }

  if (payload.action === "delete_child") {
    const { error: deactivateError } = await supabase
      .from("family_members")
      .update({
        is_active: false,
        updated_at: new Date().toISOString(),
      })
      .eq("id", childMember.id)
      .eq("family_id", parentMember.family_id);

    if (deactivateError) {
      return jsonResponse(400, { error: deactivateError.message });
    }

    await supabase.auth.admin.deleteUser(childMember.profile_id);

    return jsonResponse(200, {
      ok: true,
      action: "delete_child",
      member_id: childMember.id,
    });
  }

  return jsonResponse(400, { error: "ACTION_NOT_SUPPORTED" });
});
