# Student Check-in

Server-backed Android app for parent-managed student task check-in, timed tasks,
proof uploads, point rewards, redemption approval, and optional DingTalk /
Webhook notifications.

## Product Scope

- One family can manage multiple children
- Daily task reset at `00:00:00 Asia/Shanghai`
- Check-only / countdown / stopwatch task modes
- Delivery requirement per task: none / photo / video / audio
- Automatic point awards after completion
- Child redemption requests with parent approval
- Parent-managed child account creation / password reset / deletion
- Daily, weekly, and monthly trend reporting

## Workspace Layout

- `android-app/`: Kotlin + Jetpack Compose client
- `backend/`: unified Node.js API for auth, RPC, uploads, and notifications
- `supabase/`: PostgreSQL schema migrations reused by the new backend
- `docs/`: API, deployment, and operations notes

## Quick Start

1. Clone the repository.
2. Start your backend with `docker compose up -d --build`.
3. Copy `android-app/gradle-local.example.properties` to `android-app/gradle-local.properties`.
4. Fill in your backend URL.
5. Build the APK.

Detailed setup guide:

- `docs/developer-setup.md`
- `docs/home-server-docker.md`

## Android Bootstrap

1. Install JDK 17 or newer.
2. Install Android SDK platform 35.
3. Create `android-app/local.properties` with `sdk.dir=...`.
4. Optional but recommended: copy `android-app/gradle-local.example.properties` to `android-app/gradle-local.properties`.
5. From `android-app/`, run `./gradlew testDebugUnitTest`.

## Backend Bootstrap

This project now supports a true `2 container` deployment:

1. `student-checkin-postgres`
2. `student-checkin-api`

The API container applies `supabase/migrations/` automatically on startup, so no
Supabase CLI, Kong, Auth, Storage, or Edge Function containers are required.

For self-hosting on your own Docker server with `nginx` and public port `8443`, see `docs/home-server-docker.md`.

Android environment variables still reuse the old names to avoid changing the
mobile build wiring:

- `STUDENTCLOCKIN_SUPABASE_URL`
- `STUDENTCLOCKIN_SUPABASE_PUBLIC_KEY`

Android local config file:

- `android-app/gradle-local.properties`
- sample: `android-app/gradle-local.example.properties`

For the new backend, `STUDENTCLOCKIN_SUPABASE_PUBLIC_KEY` can be any non-empty
placeholder string such as `student-checkin-public`.

Deployment helpers:

- `scripts/build-debug-apk.ps1`
- `android-app/gradle-local.example.properties`
- `docs/home-server-docker.md`
- `docs/developer-setup.md`
- `docker-compose.yml`
- `.env.example`
- `deploy/home-server/docker-compose.two-container.yml`
- `deploy/home-server/.env.example`

## Open Source / GitHub

You can publish this repository to GitHub, but other people still need to provide
their own deployment values when compiling or deploying:

- Android build:
  - `STUDENTCLOCKIN_SUPABASE_URL`
  - `STUDENTCLOCKIN_SUPABASE_PUBLIC_KEY`
- Backend deploy:
  - `POSTGRES_PASSWORD`
  - `JWT_SECRET`
  - optional `API_PORT`

No personal server URL or home LAN IP is intentionally hard-coded in the app
source. The Android client reads the backend URL from build environment
variables or `android-app/gradle-local.properties`, and the Docker deployment
reads secrets from `.env`.

Quick start for the server:

1. Copy `.env.example` to `.env`.
2. Change at least `POSTGRES_PASSWORD` and `JWT_SECRET`.
3. Run `docker compose up -d --build`.

The API will then listen on host port `28547` by default unless you change
`API_PORT` in `.env`.

Quick start for the APK:

1. Copy `android-app/gradle-local.example.properties` to `android-app/gradle-local.properties`.
2. Fill in `studentclockinSupabaseUrl`.
3. Set `JAVA_HOME`.
4. Run `.\scripts\build-debug-apk.ps1`.

## Core Scheduled Jobs

These now run inside the API container:

- Beijing-time daily occurrence generation
- Daily child/task report recalculation
- DingTalk / generic webhook dispatch
- Parent-side child account creation, password reset, and deactivation
