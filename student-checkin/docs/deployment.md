# Deployment

## Android Local Setup

1. Install JDK 21 or newer.
2. Install Android SDK platform 35 and build-tools 35.
3. From `student-checkin/android-app`, run `./gradlew testDebugUnitTest`.

## Supabase Setup

1. Create a Supabase project.
2. Link the repo `student-checkin/supabase/` directory to the project.
3. Apply the SQL migrations in timestamp order.
4. Deploy the Edge Functions:
   - `daily-rollover`
   - `send-notifications`
   - `recalculate-reports`

## Required Secrets

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `SUPABASE_PROJECT_REF`
- `SUPABASE_ACCESS_TOKEN`
- `SUPABASE_DB_PASSWORD`
- `STUDENTCLOCKIN_SUPABASE_URL`
- `STUDENTCLOCKIN_SUPABASE_ANON_KEY`

## Scheduled Jobs

- Beijing `00:00:00` every day:
  - invoke `daily-rollover`
- Every 15 minutes or hourly:
  - invoke `recalculate-reports`
- Event-driven after task completion / redemption:
  - invoke `send-notifications`

## Production Notes

- Store media in Supabase Storage with lifecycle cleanup based on `expires_at`.
- Keep `family_settings.timezone = Asia/Shanghai` for this MVP.
- Enable email password recovery for parent accounts before public release.
- On Windows, if local `npx supabase` cannot run the official binary package, deploy from Ubuntu/WSL or use [deploy-supabase.yml](/C:/Users/Administrator/Documents/Codex/2026-05-27/StudentClockin/.github/workflows/deploy-supabase.yml).
- The GitHub Action uses the official `supabase/setup-cli` action and deploys migrations plus all three Edge Functions.
