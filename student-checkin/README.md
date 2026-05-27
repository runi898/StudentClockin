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
- Daily, weekly, and monthly trend reporting

## Workspace Layout

- `android-app/`: Kotlin + Jetpack Compose client
- `supabase/`: PostgreSQL migrations, seed data, and Edge Functions
- `docs/`: API, deployment, and operations notes

## Android Bootstrap

1. Install JDK 21 or newer.
2. Install Android SDK platform 35.
3. Create `android-app/local.properties` with `sdk.dir=...`.
4. From `android-app/`, run `./gradlew testDebugUnitTest`.

## Backend Bootstrap

1. Install the Supabase CLI.
2. Start or link a Supabase project.
3. Apply migrations under `supabase/migrations/`.
4. Deploy the Edge Functions.

## Core Scheduled Jobs

- `daily-rollover`: generate each Beijing-time day of occurrences
- `recalculate-reports`: rebuild daily child/task summaries
- `send-notifications`: post events to DingTalk / generic webhook
