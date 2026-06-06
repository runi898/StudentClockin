import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, "..", "..");

function envNumber(name, fallback) {
  const raw = process.env[name];
  if (!raw) {
    return fallback;
  }

  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export const config = {
  host: process.env.HOST || "0.0.0.0",
  port: envNumber("PORT", 8000),
  databaseUrl:
    process.env.DATABASE_URL ||
    "postgresql://postgres:postgres@postgres:5432/student_checkin",
  jwtSecret: process.env.JWT_SECRET || "student-checkin-dev-secret",
  accessTokenTtlSeconds: envNumber("ACCESS_TOKEN_TTL_SECONDS", 60 * 60 * 24 * 7),
  refreshTokenTtlSeconds: envNumber("REFRESH_TOKEN_TTL_SECONDS", 60 * 60 * 24 * 30),
  storageRoot:
    process.env.STORAGE_ROOT ||
    path.resolve(projectRoot, "data", "storage", "task-deliveries"),
  migrationsDir: path.resolve(projectRoot, "supabase", "migrations"),
  schedulerIntervalMs: envNumber("SCHEDULER_INTERVAL_MS", 60 * 1000)
};

export function beijingDateString(date = new Date()) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(date);
}
