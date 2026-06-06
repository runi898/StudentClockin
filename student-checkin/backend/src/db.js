import fs from "node:fs/promises";
import path from "node:path";
import pg from "pg";
import { beijingDateString, config } from "./config.js";

const { Pool } = pg;

export const pool = new Pool({
  connectionString: config.databaseUrl,
  max: 10
});

export async function query(text, params = []) {
  return pool.query(text, params);
}

export async function withTransaction(work) {
  const client = await pool.connect();
  try {
    await client.query("begin");
    const result = await work(client);
    await client.query("commit");
    return result;
  } catch (error) {
    await client.query("rollback");
    throw error;
  } finally {
    client.release();
  }
}

export async function withAuthContext(userId, work, role = "authenticated") {
  return withTransaction(async (client) => {
    await client.query(
      "select set_config('request.jwt.claim.sub', $1, true), set_config('request.jwt.claim.role', $2, true)",
      [userId, role]
    );
    return work(client);
  });
}

export async function runMigrations() {
  const entries = await fs.readdir(config.migrationsDir, { withFileTypes: true });
  const files = entries
    .filter((entry) => entry.isFile() && entry.name.endsWith(".sql"))
    .map((entry) => entry.name)
    .sort();

  await query("create schema if not exists app_private");
  await query(`
    create table if not exists app_private.schema_migrations (
      filename text primary key,
      applied_at timestamptz not null default now()
    )
  `);

  for (const fileName of files) {
    const existing = await query(
      "select 1 from app_private.schema_migrations where filename = $1",
      [fileName]
    );

    if (existing.rowCount > 0) {
      continue;
    }

    const sql = await fs.readFile(path.join(config.migrationsDir, fileName), "utf8");
    await withTransaction(async (client) => {
      await client.query(sql);
      await client.query(
        "insert into app_private.schema_migrations (filename) values ($1)",
        [fileName]
      );
    });
  }
}

export async function ensureOperationalData() {
  const today = beijingDateString();
  const yesterday = beijingDateString(
    new Date(Date.now() - 24 * 60 * 60 * 1000)
  );

  await query("select public.generate_daily_occurrences($1::date)", [today]);
  await query("select public.recalculate_daily_reports($1::date, $2::date)", [
    yesterday,
    today
  ]);
}
