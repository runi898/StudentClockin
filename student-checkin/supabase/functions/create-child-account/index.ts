import { createClient } from "jsr:@supabase/supabase-js@2";

type CreateChildPayload = {
  child_name?: string;
  child_email?: string;
  child_password?: string;
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

function deriveLoginName(email: string) {
  return email.split("@")[0]?.trim() || null;
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

  let payload: CreateChildPayload;
  try {
    payload = await req.json();
  } catch {
    return jsonResponse(400, { error: "INVALID_JSON" });
  }

  const childName = payload.child_name?.trim();
  const childEmail = payload.child_email?.trim().toLowerCase();
  const childPassword = payload.child_password?.trim();

  if (!childName) {
    return jsonResponse(400, { error: "CHILD_NAME_REQUIRED" });
  }
  if (!childEmail) {
    return jsonResponse(400, { error: "CHILD_EMAIL_REQUIRED" });
  }
  if (!childPassword || childPassword.length < 6) {
    return jsonResponse(400, { error: "CHILD_PASSWORD_TOO_SHORT" });
  }

  const { data: createdUser, error: createUserError } = await supabase.auth.admin.createUser({
    email: childEmail,
    password: childPassword,
    email_confirm: true,
    user_metadata: {
      role: "child",
      child_name: childName,
    },
  });

  if (createUserError || !createdUser.user) {
    return jsonResponse(400, { error: createUserError?.message ?? "CREATE_USER_FAILED" });
  }

  try {
    const profilePayload = {
      id: createdUser.user.id,
      email: childEmail,
      display_name: childName,
    };

    const { error: profileError } = await supabase.from("profiles").upsert(profilePayload);
    if (profileError) {
      throw profileError;
    }

    const { data: memberRow, error: memberError } = await supabase
      .from("family_members")
      .insert({
        family_id: parentMember.family_id,
        profile_id: createdUser.user.id,
        role: "child",
        child_display_name: childName,
        login_name: deriveLoginName(childEmail),
        is_active: true,
      })
      .select("id, created_at")
      .single();

    if (memberError || !memberRow) {
      throw memberError ?? new Error("CREATE_MEMBER_FAILED");
    }

    return jsonResponse(200, {
      member_id: memberRow.id,
      child_name: childName,
      email: childEmail,
      created_at: memberRow.created_at,
    });
  } catch (error) {
    await supabase.auth.admin.deleteUser(createdUser.user.id);
    const message = error instanceof Error ? error.message : "CREATE_CHILD_FAILED";
    return jsonResponse(400, { error: message });
  }
});
