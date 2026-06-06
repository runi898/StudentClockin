import fs from "node:fs/promises";
import path from "node:path";
import express from "express";
import {
  authenticateWithPassword,
  createUserAccount,
  createUserRecord,
  deactivateUser,
  rotateRefreshToken,
  updateUserPassword,
  verifyAccessToken
} from "./auth.js";
import { beijingDateString, config } from "./config.js";
import {
  ensureOperationalData,
  query,
  runMigrations,
  withAuthContext,
  withTransaction
} from "./db.js";
import {
  createNotificationEvent,
  sendNotificationMessage
} from "./notifications.js";

const app = express();

function asyncHandler(handler) {
  return (req, res, next) => {
    Promise.resolve(handler(req, res, next)).catch(next);
  };
}

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

function clientIp(req) {
  return req.headers["x-forwarded-for"]?.split(",")[0]?.trim() || req.socket.remoteAddress || null;
}

function authTokenFromRequest(req) {
  const header = req.headers.authorization || "";
  return header.startsWith("Bearer ") ? header.slice(7).trim() : null;
}

async function requireUser(req, res, next) {
  try {
    const token = authTokenFromRequest(req);
    if (!token) {
      res.status(401).json({ error: "AUTH_REQUIRED" });
      return;
    }

    req.user = await verifyAccessToken(token);
    next();
  } catch {
    res.status(401).json({ error: "INVALID_TOKEN" });
  }
}

function resolveStoragePath(relativePath) {
  const normalized = path.posix.normalize(`/${relativePath || ""}`).replace(/^\/+/, "");
  const target = path.resolve(config.storageRoot, normalized);
  const storageRoot = path.resolve(config.storageRoot);

  if (!target.startsWith(storageRoot)) {
    throw new Error("INVALID_STORAGE_PATH");
  }

  return { normalized, target };
}

async function loadMemberContext(userId) {
  return withAuthContext(userId, async (client) => {
    const { rows } = await client.query("select * from public.current_member_context()");
    return rows[0] || null;
  });
}

function sendAppError(res, error) {
  const message = error instanceof Error ? error.message : "REQUEST_FAILED";
  const upper = message.toUpperCase();
  const code = typeof error === "object" && error ? error.code : null;

  if (code === "23505") {
    res.status(400).json({ error: "DUPLICATE_RESOURCE" });
    return;
  }

  if (code === "23503") {
    res.status(400).json({ error: "RELATED_RESOURCE_NOT_FOUND" });
    return;
  }

  if (upper.includes("AUTH") || upper.includes("INVALID_TOKEN")) {
    res.status(401).json({ error: message });
    return;
  }

  if (upper.includes("ACCESS DENIED") || upper.includes("PARENT_CONTEXT_REQUIRED")) {
    res.status(403).json({ error: message });
    return;
  }

  if (upper.includes("NOT_FOUND")) {
    res.status(404).json({ error: message });
    return;
  }

  res.status(400).json({ error: message });
}

async function notifyTaskCompleted(occurrenceId) {
  const { rows } = await query(
    `
      select
        o.family_id,
        o.child_member_id,
        fm.child_display_name,
        o.task_name_snapshot,
        o.point_value_snapshot,
        l.balance_after
      from public.task_occurrences o
      join public.family_members fm on fm.id = o.child_member_id
      left join public.point_ledger l
        on l.occurrence_id = o.id
       and l.change_type = 'task_reward'
      where o.id = $1
      order by l.created_at desc nulls last
      limit 1
    `,
    [occurrenceId]
  );

  const row = rows[0];
  if (!row) {
    return;
  }

  const messageText = `${row.child_display_name} 任务 ${row.task_name_snapshot} 完成，积分+${row.point_value_snapshot}`;
  await createNotificationEvent({
    familyId: row.family_id,
    childMemberId: row.child_member_id,
    eventType: "task_completed",
    messageText,
    payload: {
      occurrence_id: occurrenceId,
      points_awarded: row.point_value_snapshot,
      balance_after: row.balance_after
    }
  });
}

async function notifyRedemptionRequested(requestId) {
  const { rows } = await query(
    `
      select
        r.family_id,
        r.child_member_id,
        fm.child_display_name,
        r.points_requested,
        r.cash_amount_cny
      from public.redemption_requests r
      join public.family_members fm on fm.id = r.child_member_id
      where r.id = $1
      limit 1
    `,
    [requestId]
  );

  const row = rows[0];
  if (!row) {
    return;
  }

  const messageText = `${row.child_display_name} 发起积分兑换 ${row.points_requested} 分，可兑换 ${row.cash_amount_cny} 元，等待家长审核`;
  await createNotificationEvent({
    familyId: row.family_id,
    childMemberId: row.child_member_id,
    eventType: "redemption_requested",
    messageText,
    payload: { request_id: requestId }
  });
}

async function notifyRedemptionReviewed(requestId) {
  const { rows } = await query(
    `
      select
        r.family_id,
        r.child_member_id,
        fm.child_display_name,
        r.points_requested,
        r.status,
        public.current_points_balance(r.child_member_id) as balance_after
      from public.redemption_requests r
      join public.family_members fm on fm.id = r.child_member_id
      where r.id = $1
      limit 1
    `,
    [requestId]
  );

  const row = rows[0];
  if (!row) {
    return;
  }

  const messageText =
    row.status === "approved"
      ? `${row.child_display_name} 兑换 ${row.points_requested} 积分已通过，剩余积分 ${row.balance_after}`
      : `${row.child_display_name} 的积分兑换未通过，请查看家长端说明`;

  await createNotificationEvent({
    familyId: row.family_id,
    childMemberId: row.child_member_id,
    eventType: row.status === "approved" ? "redemption_approved" : "redemption_rejected",
    messageText,
    payload: { request_id: requestId }
  });
}

const rpcDefinitions = {
  current_member_context: {
    sql: "select * from public.current_member_context()"
  },
  member_family_context: {
    sql: "select * from public.member_family_context()"
  },
  child_today_snapshot: {
    sql: "select * from public.child_today_snapshot()"
  },
  parent_today_snapshot: {
    sql: "select * from public.parent_today_snapshot()"
  },
  parent_redemption_list: {
    sql: "select * from public.parent_redemption_list()"
  },
  parent_redemption_stats: {
    sql: "select * from public.parent_redemption_stats()"
  },
  parent_child_accounts: {
    sql: "select * from public.parent_child_accounts()"
  },
  complete_parent_onboarding: {
    sql: `
      select *
      from public.complete_parent_onboarding(
        $1::text,
        $2::text,
        $3::numeric,
        $4::integer,
        $5::integer,
        $6::text
      )
    `,
    params: (body) => [
      body.p_family_name,
      body.p_parent_display_name,
      body.p_cash_cny_per_10_points ?? 1,
      body.p_min_redeem_points ?? 10,
      body.p_media_retention_days ?? 30,
      body.p_timezone ?? "Asia/Shanghai"
    ]
  },
  child_start_task: {
    sql: "select * from public.child_start_task($1::uuid)",
    params: (body) => [body.p_occurrence_id]
  },
  child_complete_task: {
    sql: "select * from public.child_complete_task($1::uuid)",
    params: (body) => [body.p_occurrence_id],
    afterCommit: async (_rows, body) => notifyTaskCompleted(body.p_occurrence_id)
  },
  child_finish_task: {
    sql: "select * from public.child_finish_task($1::uuid, now(), $2::integer)",
    params: (body) => [body.p_occurrence_id, body.p_actual_duration_seconds ?? 0],
    afterCommit: async (_rows, body) => notifyTaskCompleted(body.p_occurrence_id)
  },
  child_submit_redemption: {
    sql: "select * from public.child_submit_redemption($1::integer)",
    params: (body) => [body.p_points_requested],
    afterCommit: async (rows) => {
      const requestId = rows[0]?.id;
      if (requestId) {
        await notifyRedemptionRequested(requestId);
      }
    }
  },
  parent_review_redemption: {
    sql: "select * from public.parent_review_redemption($1::uuid, $2::boolean, null)",
    params: (body) => [body.p_request_id, Boolean(body.p_approve)],
    afterCommit: async (rows) => {
      const requestId = rows[0]?.id;
      if (requestId) {
        await notifyRedemptionReviewed(requestId);
      }
    }
  },
  parent_update_family_settings: {
    custom: async (req) => {
      const context = await loadMemberContext(req.user.userId);
      if (!context || context.role !== "parent") {
        throw httpError(403, "PARENT_CONTEXT_REQUIRED");
      }

      const familyName = String(
        req.body?.p_family_name ??
          req.body?.family_name ??
          req.body?.familyName ??
          req.body?.name ??
          ""
      ).trim();
      if (familyName) {
        await query(
          `
            update public.families
            set name = $2,
                updated_at = now()
            where id = $1
            returning id, name
          `,
          [context.family_id, familyName]
        );
      }

      const { rows } = await query(
        `
          update public.family_settings
          set
            cash_cny_per_10_points = coalesce($2::numeric, cash_cny_per_10_points),
            min_redeem_points = coalesce($3::integer, min_redeem_points),
            media_retention_days = coalesce($4::integer, media_retention_days),
            updated_at = now()
          where family_id = $1
          returning *
        `,
        [
          context.family_id,
          req.body?.p_cash_cny_per_10_points ?? null,
          req.body?.p_min_redeem_points ?? null,
          req.body?.p_media_retention_days ?? null
        ]
      );
      return rows;
    }
  },
  parent_create_quick_task: {
    sql: `
      select public.parent_create_quick_task(
        $1::uuid,
        $2::text,
        $3::text,
        $4::text,
        $5::integer,
        $6::integer,
        $7::text
      )
    `,
    params: (body) => [
      body.p_child_member_id,
      body.p_name,
      body.p_mode,
      body.p_delivery_requirement,
      body.p_points,
      body.p_target_minutes ?? null,
      body.p_scheduled_time ?? null
    ]
  },
  parent_update_task_template: {
    sql: `
      select *
      from public.parent_update_task_template(
        $1::uuid,
        $2::text,
        $3::text,
        $4::text,
        $5::integer,
        $6::integer,
        $7::text
      )
    `,
    params: (body) => [
      body.p_task_template_id,
      body.p_name,
      body.p_mode,
      body.p_delivery_requirement,
      body.p_points,
      body.p_target_minutes ?? null,
      body.p_scheduled_time ?? null
    ]
  },
  parent_delete_task_template: {
    sql: "select public.parent_delete_task_template($1::uuid)",
    params: (body) => [body.p_task_template_id]
  },
  parent_delete_task_occurrence: {
    sql: "select public.parent_delete_task_occurrence($1::uuid)",
    params: (body) => [body.p_occurrence_id]
  },
  parent_reset_today_occurrences: {
    sql: "select public.parent_reset_today_occurrences()"
  }
};

app.get("/", (_req, res) => {
  res.json({ ok: true, service: "student-checkin-api" });
});

app.get("/health", (_req, res) => {
  res.json({ ok: true });
});

app.get("/auth/v1/", (_req, res) => {
  res.status(200).json({ ok: true });
});

app.get("/rest/v1/", (_req, res) => {
  res.status(200).json({ ok: true });
});

app.post(
  "/storage/v1/object/task-deliveries/*",
  requireUser,
  express.raw({ type: "*/*", limit: "100mb" }),
  asyncHandler(async (req, res) => {
    const relativePath = req.params[0];
    const { normalized, target } = resolveStoragePath(relativePath);
    const occurrenceId = normalized.split("/")[0];
    const context = await loadMemberContext(req.user.userId);

    if (!context || context.role !== "child") {
      res.status(403).json({ error: "CHILD_CONTEXT_REQUIRED" });
      return;
    }

    const ownership = await query(
      `
        select 1
        from public.task_occurrences
        where id = $1::uuid
          and child_member_id = $2::uuid
        limit 1
      `,
      [occurrenceId, context.member_id]
    );

    if (ownership.rowCount === 0) {
      res.status(403).json({ error: "TASK_UPLOAD_ACCESS_DENIED" });
      return;
    }

    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.writeFile(target, req.body);

    await query(
      `
        insert into storage.objects (bucket_id, name, owner, metadata, last_accessed_at)
        values ('task-deliveries', $1, $2::uuid, $3::jsonb, now())
        on conflict (bucket_id, name) do update
        set owner = excluded.owner,
            metadata = excluded.metadata,
            updated_at = now(),
            last_accessed_at = now()
      `,
      [
        normalized,
        req.user.userId,
        JSON.stringify({
          contentType: req.headers["content-type"] || "application/octet-stream",
          size: req.body.length
        })
      ]
    );

    res.status(200).json({ ok: true, path: normalized });
  })
);

app.get(
  "/storage/v1/object/public/task-deliveries/*",
  asyncHandler(async (req, res) => {
    const relativePath = req.params[0];
    const { normalized, target } = resolveStoragePath(relativePath);
    const file = await fs.readFile(target);
    const metadata = await query(
      "select metadata from storage.objects where bucket_id = 'task-deliveries' and name = $1 limit 1",
      [normalized]
    );
    const contentType = metadata.rows[0]?.metadata?.contentType;
    if (contentType) {
      res.type(contentType);
    }
    res.status(200).end(file);
  })
);

app.use(express.json({ limit: "2mb" }));

app.post(
  "/auth/v1/signup",
  asyncHandler(async (req, res) => {
    const email = normalizeEmail(req.body?.email);
    const password = String(req.body?.password || "");
    const result = await createUserAccount({
      email,
      password,
      userMetadata: { role: "parent" }
    });
    res.json(result.session);
  })
);

app.post(
  "/auth/v1/token",
  asyncHandler(async (req, res) => {
    const grantType = String(req.query.grant_type || "");

    if (grantType === "password") {
      const session = await authenticateWithPassword(req.body?.email, req.body?.password, {
        userAgent: req.headers["user-agent"] || null,
        ipAddress: clientIp(req)
      });
      res.json(session);
      return;
    }

    if (grantType === "refresh_token") {
      const session = await rotateRefreshToken(req.body?.refresh_token, {
        userAgent: req.headers["user-agent"] || null,
        ipAddress: clientIp(req)
      });
      res.json(session);
      return;
    }

    res.status(400).json({ error: "GRANT_TYPE_NOT_SUPPORTED" });
  })
);

app.post(
  "/auth/v1/recover",
  asyncHandler(async (req, res) => {
    const email = normalizeEmail(req.body?.email);
    const { rows } = await query(
      `
        select p.id as profile_id
        from public.profiles p
        where lower(p.email) = lower($1)
        limit 1
      `,
      [email]
    );

    const profileId = rows[0]?.profile_id;
    if (profileId) {
      await query(
        `
          insert into public.auth_recovery_audits (profile_id, action, payload)
          values ($1, 'password_reset_requested', $2::jsonb)
        `,
        [profileId, JSON.stringify({ email })]
      );
    }

    res.json({ ok: true });
  })
);

app.use("/rest/v1", requireUser);
app.use("/functions/v1", requireUser);

app.post(
  "/rest/v1/rpc/:functionName",
  asyncHandler(async (req, res) => {
    const definition = rpcDefinitions[req.params.functionName];
    if (!definition) {
      res.status(404).json({ error: "RPC_NOT_FOUND" });
      return;
    }

    const rows = definition.custom
      ? await definition.custom(req)
      : await withAuthContext(req.user.userId, async (client) => {
          const params = definition.params ? definition.params(req.body || {}) : [];
          const result = await client.query(definition.sql, params);
          return result.rows;
        });

    if (definition.afterCommit) {
      await definition.afterCommit(rows, req.body || {});
    }

    res.json(rows);
  })
);

app.get(
  "/rest/v1/point_ledger",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context) {
      res.status(403).json({ error: "MEMBER_CONTEXT_REQUIRED" });
      return;
    }

    const values =
      context.role === "child"
        ? [context.member_id]
        : [context.family_id];
    const sql =
      context.role === "child"
        ? `
            select id, created_at, change_type, points_delta, balance_after, note
            from public.point_ledger
            where child_member_id = $1
            order by created_at desc
          `
        : `
            select id, created_at, change_type, points_delta, balance_after, note
            from public.point_ledger
            where family_id = $1
            order by created_at desc
          `;

    const { rows } = await query(sql, values);
    res.json(rows);
  })
);

app.get(
  "/rest/v1/family_members",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context || context.role !== "parent") {
      res.status(403).json({ error: "PARENT_CONTEXT_REQUIRED" });
      return;
    }

    const { rows } = await query(
      `
        select id, child_display_name, role
        from public.family_members
        where family_id = $1
          and role = 'child'
          and is_active = true
        order by child_display_name asc
      `,
      [context.family_id]
    );
    res.json(rows);
  })
);

app.get(
  "/rest/v1/notification_channels",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context || context.role !== "parent") {
      res.status(403).json({ error: "PARENT_CONTEXT_REQUIRED" });
      return;
    }

    const { rows } = await query(
      `
        select id, channel_type, config_json, is_enabled
        from public.notification_channels
        where family_id = $1
        order by created_at asc
      `,
      [context.family_id]
    );
    res.json(rows);
  })
);

app.post(
  "/rest/v1/notification_channels",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context || context.role !== "parent") {
      res.status(403).json({ error: "PARENT_CONTEXT_REQUIRED" });
      return;
    }

    const { rows } = await query(
      `
        insert into public.notification_channels (
          family_id,
          channel_type,
          config_json,
          is_enabled
        )
        values ($1, $2, $3::jsonb, $4)
        returning id
      `,
      [
        context.family_id,
        req.body?.channel_type || "webhook",
        JSON.stringify(req.body?.config_json || {}),
        Boolean(req.body?.is_enabled)
      ]
    );
    res.status(201).json(rows);
  })
);

app.patch(
  "/rest/v1/families",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context || context.role !== "parent") {
      res.status(403).json({ error: "PARENT_CONTEXT_REQUIRED" });
      return;
    }

    const familyId = String(req.query.id || "").replace(/^eq\./, "");
    if (!familyId || familyId !== context.family_id) {
      res.status(404).json({ error: "FAMILY_NOT_FOUND" });
      return;
    }

    const familyName = String(req.body?.name || "").trim();
    if (!familyName) {
      res.status(400).json({ error: "FAMILY_NAME_REQUIRED" });
      return;
    }

    const { rows } = await query(
      `
        update public.families
        set name = $2,
            updated_at = now()
        where id = $1
        returning id, name
      `,
      [familyId, familyName]
    );

    res.json(rows);
  })
);

app.patch(
  "/rest/v1/notification_channels",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context || context.role !== "parent") {
      res.status(403).json({ error: "PARENT_CONTEXT_REQUIRED" });
      return;
    }

    const id = String(req.query.id || "").replace(/^eq\./, "");
    const { rows } = await query(
      `
        update public.notification_channels
        set
          channel_type = coalesce($3, channel_type),
          config_json = coalesce($4::jsonb, config_json),
          is_enabled = coalesce($5, is_enabled),
          updated_at = now()
        where id = $1
          and family_id = $2
        returning id
      `,
      [
        id,
        context.family_id,
        req.body?.channel_type || null,
        req.body?.config_json ? JSON.stringify(req.body.config_json) : null,
        typeof req.body?.is_enabled === "boolean" ? req.body.is_enabled : null
      ]
    );
    res.json(rows);
  })
);

app.get(
  "/rest/v1/daily_child_reports",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context) {
      res.status(403).json({ error: "MEMBER_CONTEXT_REQUIRED" });
      return;
    }

    const limit = Number(req.query.limit || 30);
    const { rows } =
      context.role === "child"
        ? await query(
            `
              select child_member_id, local_date, task_total_count, task_completed_count,
                     total_duration_seconds, points_awarded, points_redeemed, redemption_cash_cny
              from public.daily_child_reports
              where child_member_id = $1
              order by local_date desc
              limit $2
            `,
            [context.member_id, limit]
          )
        : await query(
            `
              select child_member_id, local_date, task_total_count, task_completed_count,
                     total_duration_seconds, points_awarded, points_redeemed, redemption_cash_cny
              from public.daily_child_reports
              where family_id = $1
              order by local_date desc
              limit $2
            `,
            [context.family_id, limit]
          );

    res.json(rows);
  })
);

app.get(
  "/rest/v1/task_occurrences",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context) {
      res.status(403).json({ error: "MEMBER_CONTEXT_REQUIRED" });
      return;
    }

    const occurrenceId = String(req.query.id || "").replace(/^eq\./, "");
    if (occurrenceId) {
      const { rows } = await query(
        `
          select task_template_id, task_name_snapshot
          from public.task_occurrences
          where id = $1
            and coalesce(is_visible, true)
            and (
              ($2 = 'child' and child_member_id = $3)
              or ($2 = 'parent' and family_id = $4)
            )
          limit 1
        `,
        [occurrenceId, context.role, context.member_id, context.family_id]
      );
      res.json(rows);
      return;
    }

    const templateId = String(req.query.task_template_id || "").replace(/^eq\./, "");
    const fromDate = String(req.query.local_date || "").replace(/^gte\./, "");

    const { rows } = await query(
        `
        select local_date, status, completed_at
        from public.task_occurrences
        where task_template_id = $1
          and coalesce(is_visible, true)
          and local_date >= $2::date
          and (
            ($3 = 'child' and child_member_id = $4)
            or ($3 = 'parent' and family_id = $5)
          )
        order by local_date desc
      `,
      [templateId, fromDate, context.role, context.member_id, context.family_id]
    );
    res.json(rows);
  })
);

app.get(
  "/rest/v1/task_submissions",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context) {
      res.status(403).json({ error: "MEMBER_CONTEXT_REQUIRED" });
      return;
    }

    const { rows } = await query(
      `
        select s.id, s.occurrence_id, s.submission_type, s.storage_path, s.uploaded_at
        from public.task_submissions s
        join public.task_occurrences o on o.id = s.occurrence_id
        where (
          ($1 = 'child' and o.child_member_id = $2)
          or ($1 = 'parent' and o.family_id = $3)
        )
        order by s.uploaded_at desc
      `,
      [context.role, context.member_id, context.family_id]
    );
    res.json(rows);
  })
);

app.post(
  "/rest/v1/task_submissions",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context || context.role !== "child") {
      res.status(403).json({ error: "CHILD_CONTEXT_REQUIRED" });
      return;
    }

    const { rows } = await query(
      `
        insert into public.task_submissions (
          occurrence_id,
          submission_type,
          storage_path,
          retention_days
        )
        select
          o.id,
          $2::text,
          $3::text,
          fs.media_retention_days
        from public.task_occurrences o
        join public.family_settings fs
          on fs.family_id = o.family_id
        where o.id = $1::uuid
          and o.child_member_id = $4::uuid
        returning id
      `,
      [
        req.body?.occurrence_id,
        req.body?.submission_type,
        req.body?.storage_path,
        context.member_id
      ]
    );
    if (rows.length === 0) {
      res.status(404).json({ error: "TASK_OCCURRENCE_NOT_FOUND" });
      return;
    }
    res.status(201).json(rows);
  })
);

app.post(
  "/functions/v1/send-notifications",
  asyncHandler(async (req, res) => {
    const results = await sendNotificationMessage(req.body?.family_id, req.body?.message_text || "");
    res.json({ ok: true, results });
  })
);

app.post(
  "/functions/v1/create-child-account",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context || context.role !== "parent") {
      res.status(403).json({ error: "PARENT_CONTEXT_REQUIRED" });
      return;
    }

    const childName = String(req.body?.child_name || "").trim();
    const childEmail = normalizeEmail(req.body?.child_email);
    const childPassword = String(req.body?.child_password || "");

    if (!childName) {
      res.status(400).json({ error: "CHILD_NAME_REQUIRED" });
      return;
    }

    if (!childEmail) {
      res.status(400).json({ error: "CHILD_EMAIL_REQUIRED" });
      return;
    }

    if (childPassword.length < 6) {
      res.status(400).json({ error: "CHILD_PASSWORD_TOO_SHORT" });
      return;
    }

    const createdUser = await createUserRecord({
      email: childEmail,
      password: childPassword,
      userMetadata: { role: "child", child_name: childName }
    });

    try {
      const result = await withTransaction(async (client) => {
        await client.query(
          `
            insert into public.profiles (id, email, display_name)
            values ($1::uuid, $2, $3)
            on conflict (id) do update
            set email = excluded.email, display_name = excluded.display_name
          `,
          [createdUser.id, childEmail, childName]
        );

        const { rows } = await client.query(
          `
            insert into public.family_members (
              family_id,
              profile_id,
              role,
              child_display_name,
              login_name,
              is_active
            )
            values ($1::uuid, $2::uuid, 'child', $3, $4, true)
            returning id as member_id, created_at
          `,
          [context.family_id, createdUser.id, childName, childEmail.split("@")[0] || null]
        );

        await client.query(
          "select public.generate_daily_occurrences($1::date)",
          [beijingDateString()]
        );

        return rows[0];
      });

      res.json({
        member_id: result.member_id,
        child_name: childName,
        email: childEmail,
        created_at: result.created_at
      });
    } catch (error) {
      await deactivateUser(createdUser.id);
      throw error;
    }
  })
);

app.post(
  "/functions/v1/manage-child-account",
  asyncHandler(async (req, res) => {
    const context = await loadMemberContext(req.user.userId);
    if (!context || context.role !== "parent") {
      res.status(403).json({ error: "PARENT_CONTEXT_REQUIRED" });
      return;
    }

    const memberId = String(req.body?.member_id || "");
    const action = String(req.body?.action || "");
    const { rows } = await query(
      `
        select fm.id, fm.profile_id, fm.child_display_name
        from public.family_members fm
        where fm.id = $1
          and fm.family_id = $2
          and fm.role = 'child'
      `,
      [memberId, context.family_id]
    );

    const child = rows[0];
    if (!child) {
      res.status(404).json({ error: "CHILD_ACCOUNT_NOT_FOUND" });
      return;
    }

    if (action === "reset_password") {
      await updateUserPassword(child.profile_id, req.body?.new_password);
      await query(
        `
          insert into public.auth_recovery_audits (profile_id, action, payload)
          values ($1::uuid, 'child_password_reset', $2::jsonb)
        `,
        [
          child.profile_id,
          JSON.stringify({
            member_id: child.id,
            child_name: child.child_display_name,
            reset_by: req.user.userId
          })
        ]
      );
      res.json({ ok: true, action, member_id: child.id });
      return;
    }

    if (action === "rename_child") {
      const childName = String(req.body?.child_name || "").trim();
      if (!childName) {
        res.status(400).json({ error: "CHILD_NAME_REQUIRED" });
        return;
      }

      await withTransaction(async (client) => {
        await client.query(
          `
            update public.family_members
            set child_display_name = $2,
                updated_at = now()
            where id = $1
          `,
          [child.id, childName]
        );

        await client.query(
          `
            update public.profiles
            set display_name = $2,
                updated_at = now()
            where id = $1::uuid
          `,
          [child.profile_id, childName]
        );
      });

      res.json({ ok: true, action, member_id: child.id, child_name: childName });
      return;
    }

    if (action === "delete_child") {
      await query(
        `
          update public.family_members
          set is_active = false, updated_at = now()
          where id = $1
        `,
        [child.id]
      );
      await deactivateUser(child.profile_id);
      res.json({ ok: true, action, member_id: child.id });
      return;
    }

    res.status(400).json({ error: "ACTION_NOT_SUPPORTED" });
  })
);

app.use((error, _req, res, _next) => {
  console.error(error);
  sendAppError(res, error);
});

let lastScheduledDate = null;

function startScheduler() {
  const tick = async () => {
    const today = beijingDateString();
    if (today === lastScheduledDate) {
      return;
    }

    lastScheduledDate = today;
    try {
      await ensureOperationalData();
      console.log(`[scheduler] operational data ensured for ${today}`);
    } catch (error) {
      console.error("[scheduler] failed", error);
    }
  };

  void tick();
  setInterval(() => {
    void tick();
  }, config.schedulerIntervalMs);
}

async function boot() {
  await fs.mkdir(config.storageRoot, { recursive: true });
  await runMigrations();
  await ensureOperationalData();
  startScheduler();

  app.listen(config.port, config.host, () => {
    console.log(`student-checkin-api listening on http://${config.host}:${config.port}`);
  });
}

boot().catch((error) => {
  console.error("failed to start student-checkin-api", error);
  process.exitCode = 1;
});
