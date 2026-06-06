import crypto from "node:crypto";
import bcrypt from "bcryptjs";
import { SignJWT, jwtVerify } from "jose";
import { config } from "./config.js";
import { query, withTransaction } from "./db.js";

const jwtSecret = new TextEncoder().encode(config.jwtSecret);

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

function hashToken(token) {
  return crypto.createHash("sha256").update(token).digest("hex");
}

async function signAccessToken(user) {
  const now = Math.floor(Date.now() / 1000);
  const expiresAt = now + config.accessTokenTtlSeconds;
  const token = await new SignJWT({
    email: user.email,
    role: "authenticated"
  })
    .setProtectedHeader({ alg: "HS256" })
    .setSubject(user.id)
    .setIssuedAt(now)
    .setExpirationTime(expiresAt)
    .sign(jwtSecret);

  return { token, expiresAt };
}

async function insertRefreshToken(client, userId, metadata = {}) {
  const refreshToken = `${crypto.randomUUID()}.${crypto.randomBytes(24).toString("hex")}`;
  const refreshTokenHash = hashToken(refreshToken);
  const expiresAt = new Date(Date.now() + config.refreshTokenTtlSeconds * 1000).toISOString();

  const { rows } = await client.query(
    `
      insert into app_private.refresh_tokens (
        user_id,
        token_hash,
        expires_at,
        user_agent,
        issued_from_ip,
        replaced_by
      )
      values ($1, $2, $3, $4, $5, null)
      returning id
    `,
    [
      userId,
      refreshTokenHash,
      expiresAt,
      metadata.userAgent || null,
      metadata.ipAddress || null
    ]
  );

  return {
    refreshToken,
    refreshTokenId: rows[0].id
  };
}

async function buildSession(client, user, metadata = {}, revokeTokenId = null) {
  const access = await signAccessToken(user);
  const refresh = await insertRefreshToken(client, user.id, metadata);

  if (revokeTokenId) {
    await client.query(
      "update app_private.refresh_tokens set revoked_at = now(), replaced_by = $2 where id = $1",
      [revokeTokenId, refresh.refreshTokenId]
    );
  }

  return {
    access_token: access.token,
    refresh_token: refresh.refreshToken,
    expires_at: access.expiresAt
  };
}

export async function createUserAccount({
  email,
  password,
  userMetadata = {}
}) {
  const normalizedEmail = normalizeEmail(email);
  if (!normalizedEmail) {
    throw new Error("EMAIL_REQUIRED");
  }

  if (!password || password.length < 6) {
    throw new Error("PASSWORD_TOO_SHORT");
  }

  const encryptedPassword = await bcrypt.hash(password, 10);

  return withTransaction(async (client) => {
    const user = await createUserRecordWithClient(client, {
      email: normalizedEmail,
      encryptedPassword,
      userMetadata
    });
    const session = await buildSession(client, user);
    return { user, session };
  });
}

async function createUserRecordWithClient(client, { email, encryptedPassword, userMetadata }) {
  const existing = await client.query(
    "select id from auth.users where lower(email) = lower($1)",
    [email]
  );
  if (existing.rowCount > 0) {
    throw new Error("EMAIL_ALREADY_REGISTERED");
  }

  const { rows } = await client.query(
    `
      insert into auth.users (
        email,
        encrypted_password,
        email_confirmed_at,
        raw_user_meta_data
      )
      values ($1, $2, now(), $3::jsonb)
      returning id, email
    `,
    [email, encryptedPassword, JSON.stringify(userMetadata)]
  );

  return rows[0];
}

export async function createUserRecord({ email, password, userMetadata = {} }) {
  const normalizedEmail = normalizeEmail(email);
  if (!normalizedEmail) {
    throw new Error("EMAIL_REQUIRED");
  }

  if (!password || password.length < 6) {
    throw new Error("PASSWORD_TOO_SHORT");
  }

  const encryptedPassword = await bcrypt.hash(password, 10);
  return withTransaction((client) =>
    createUserRecordWithClient(client, {
      email: normalizedEmail,
      encryptedPassword,
      userMetadata
    })
  );
}

export async function authenticateWithPassword(email, password, metadata = {}) {
  const normalizedEmail = normalizeEmail(email);
  const { rows } = await query(
    `
      select id, email, encrypted_password, is_active
      from auth.users
      where lower(email) = lower($1)
      limit 1
    `,
    [normalizedEmail]
  );

  const user = rows[0];
  if (!user || !user.is_active) {
    throw new Error("INVALID_LOGIN");
  }

  const isValid = await bcrypt.compare(password || "", user.encrypted_password || "");
  if (!isValid) {
    throw new Error("INVALID_LOGIN");
  }

  return withTransaction(async (client) => buildSession(client, user, metadata));
}

export async function rotateRefreshToken(refreshToken, metadata = {}) {
  const tokenHash = hashToken(refreshToken || "");
  return withTransaction(async (client) => {
    const { rows } = await client.query(
      `
        select rt.id, rt.user_id, rt.expires_at, rt.revoked_at, u.email, u.is_active
        from app_private.refresh_tokens rt
        join auth.users u on u.id = rt.user_id
        where rt.token_hash = $1
        limit 1
      `,
      [tokenHash]
    );

    const record = rows[0];
    if (
      !record ||
      record.revoked_at ||
      !record.is_active ||
      new Date(record.expires_at).getTime() <= Date.now()
    ) {
      throw new Error("INVALID_REFRESH_TOKEN");
    }

    return buildSession(
      client,
      { id: record.user_id, email: record.email },
      metadata,
      record.id
    );
  });
}

export async function verifyAccessToken(token) {
  const { payload } = await jwtVerify(token, jwtSecret);
  return {
    userId: payload.sub,
    email: payload.email,
    role: payload.role || "authenticated"
  };
}

export async function updateUserPassword(userId, newPassword) {
  if (!newPassword || newPassword.length < 6) {
    throw new Error("NEW_PASSWORD_TOO_SHORT");
  }

  const encryptedPassword = await bcrypt.hash(newPassword, 10);
  await query(
    `
      update auth.users
      set encrypted_password = $2, updated_at = now()
      where id = $1
    `,
    [userId, encryptedPassword]
  );
}

export async function deactivateUser(userId) {
  await query(
    `
      update auth.users
      set is_active = false, updated_at = now()
      where id = $1
    `,
    [userId]
  );
}
