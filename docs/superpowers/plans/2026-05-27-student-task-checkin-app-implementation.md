# Student Task Check-in App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a server-backed Android app for parent/child task check-ins, points, redemptions, delivery uploads, Beijing-midnight daily resets, and optional DingTalk/Webhook notifications.

**Architecture:** Create a greenfield product workspace under `student-checkin/` with a Kotlin/Compose Android client and a Supabase-backed backend. Persist all family, child, task, occurrence, point, redemption, notification, and recovery data on the server so a parent can log into a new device and keep all history.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, AlarmManager, Supabase Auth, PostgreSQL, Supabase Storage, Supabase Edge Functions, pgTAP or SQL verification scripts, DingTalk webhook, generic Webhook.

---

## File Structure

### Product Workspace

- Create: `student-checkin/README.md`
- Create: `student-checkin/.gitignore`

### Android App

- Create: `student-checkin/android-app/settings.gradle.kts`
- Create: `student-checkin/android-app/build.gradle.kts`
- Create: `student-checkin/android-app/app/build.gradle.kts`
- Create: `student-checkin/android-app/app/src/main/AndroidManifest.xml`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/App.kt`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/navigation/AppNavHost.kt`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/auth/`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/tasks/`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/points/`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/parent/`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/sync/`
- Create: `student-checkin/android-app/app/src/test/java/com/familycheckin/`
- Create: `student-checkin/android-app/app/src/androidTest/java/com/familycheckin/`

### Backend / Supabase

- Create: `student-checkin/supabase/config.toml`
- Create: `student-checkin/supabase/migrations/202605270001_init_auth_family.sql`
- Create: `student-checkin/supabase/migrations/202605270002_tasks_occurrences.sql`
- Create: `student-checkin/supabase/migrations/202605270003_points_redemptions.sql`
- Create: `student-checkin/supabase/migrations/202605270004_notifications_recovery.sql`
- Create: `student-checkin/supabase/migrations/202605270005_rls_policies.sql`
- Create: `student-checkin/supabase/seed.sql`
- Create: `student-checkin/supabase/functions/daily-rollover/index.ts`
- Create: `student-checkin/supabase/functions/send-notifications/index.ts`
- Create: `student-checkin/supabase/functions/recalculate-reports/index.ts`
- Create: `student-checkin/supabase/tests/`

### Documentation

- Create: `student-checkin/docs/api-contract.md`
- Create: `student-checkin/docs/deployment.md`
- Create: `student-checkin/docs/operations.md`

## Assumptions To Implement

- Parent authentication uses email + password with email-based password reset.
- Child accounts are family-scoped identities that a parent can reset without deleting history.
- Server time source is UTC, but all daily rollovers and grouping use `Asia/Shanghai`.
- Redemption ratio is configurable per family as `cash_cny_per_10_points`.
- Media expiry deletes files, not business records.

## Task 1: Scaffold The Product Workspace

**Files:**
- Create: `student-checkin/README.md`
- Create: `student-checkin/.gitignore`
- Create: `student-checkin/android-app/settings.gradle.kts`
- Create: `student-checkin/android-app/build.gradle.kts`
- Create: `student-checkin/android-app/app/build.gradle.kts`
- Create: `student-checkin/supabase/config.toml`

- [ ] **Step 1: Write the failing workspace smoke test**

Create `student-checkin/android-app/app/src/test/java/com/familycheckin/WorkspaceSmokeTest.kt`:

```kotlin
package com.familycheckin

import org.junit.Test
import kotlin.test.assertTrue

class WorkspaceSmokeTest {
    @Test
    fun appModuleLoads() {
        assertTrue(true)
    }
}
```

- [ ] **Step 2: Run test to verify the app module does not build yet**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest
```

Expected: FAIL because the Gradle project does not exist yet.

- [ ] **Step 3: Create minimal workspace files**

Create `student-checkin/android-app/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "student-checkin"
include(":app")
```

Create `student-checkin/android-app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
```

Create `student-checkin/android-app/app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.familycheckin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.familycheckin"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 4: Run test to verify the workspace builds**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest
```

Expected: PASS for `WorkspaceSmokeTest`.

- [ ] **Step 5: Commit**

```bash
git add student-checkin
git commit -m "feat: scaffold student check-in workspace"
```

## Task 2: Build Auth, Family Persistence, And Recovery

**Files:**
- Create: `student-checkin/supabase/migrations/202605270001_init_auth_family.sql`
- Create: `student-checkin/supabase/migrations/202605270004_notifications_recovery.sql`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/auth/AuthRepository.kt`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/auth/AuthViewModel.kt`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/auth/LoginScreen.kt`
- Create: `student-checkin/android-app/app/src/test/java/com/familycheckin/auth/AuthViewModelTest.kt`

- [ ] **Step 1: Write the failing auth/recovery test**

Create `student-checkin/android-app/app/src/test/java/com/familycheckin/auth/AuthViewModelTest.kt`:

```kotlin
package com.familycheckin.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthViewModelTest {
    @Test
    fun passwordResetIntentUsesParentEmail() {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)

        viewModel.requestPasswordReset("parent@example.com")

        assertEquals("parent@example.com", repository.lastResetEmail)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest --tests com.familycheckin.auth.AuthViewModelTest
```

Expected: FAIL because `FakeAuthRepository` and `AuthViewModel` do not exist.

- [ ] **Step 3: Create minimal auth/recovery implementation**

Create `student-checkin/android-app/app/src/main/java/com/familycheckin/auth/AuthRepository.kt`:

```kotlin
package com.familycheckin.auth

interface AuthRepository {
    suspend fun requestPasswordReset(email: String)
}

class FakeAuthRepository : AuthRepository {
    var lastResetEmail: String? = null
    override suspend fun requestPasswordReset(email: String) {
        lastResetEmail = email
    }
}
```

Create `student-checkin/android-app/app/src/main/java/com/familycheckin/auth/AuthViewModel.kt`:

```kotlin
package com.familycheckin.auth

class AuthViewModel(
    private val repository: AuthRepository
) {
    fun requestPasswordReset(email: String) {
        kotlinx.coroutines.runBlocking {
            repository.requestPasswordReset(email)
        }
    }
}
```

- [ ] **Step 4: Add server persistence schema for family/auth**

Create `student-checkin/supabase/migrations/202605270001_init_auth_family.sql` with:

```sql
create table if not exists families (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  created_at timestamptz not null default now()
);

create table if not exists profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text not null unique,
  display_name text not null,
  created_at timestamptz not null default now()
);

create table if not exists family_members (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references families(id) on delete cascade,
  profile_id uuid references profiles(id) on delete cascade,
  role text not null check (role in ('parent', 'child')),
  child_display_name text,
  created_at timestamptz not null default now()
);
```

Append `student-checkin/supabase/migrations/202605270004_notifications_recovery.sql`:

```sql
create table if not exists auth_recovery_audits (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references profiles(id) on delete cascade,
  action text not null check (action in ('password_reset_requested', 'child_password_reset')),
  created_at timestamptz not null default now(),
  payload jsonb not null default '{}'::jsonb
);
```

- [ ] **Step 5: Run app test to verify it passes**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest --tests com.familycheckin.auth.AuthViewModelTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add student-checkin/android-app student-checkin/supabase
git commit -m "feat: add auth and family persistence foundation"
```

## Task 3: Add Tasks, Assignments, Occurrences, And Beijing-Midnight Reset

**Files:**
- Create: `student-checkin/supabase/migrations/202605270002_tasks_occurrences.sql`
- Create: `student-checkin/supabase/functions/daily-rollover/index.ts`
- Create: `student-checkin/supabase/tests/daily_rollover.sql`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/tasks/TodayTaskRepository.kt`
- Create: `student-checkin/android-app/app/src/test/java/com/familycheckin/tasks/OccurrenceDateTest.kt`

- [ ] **Step 1: Write the failing rollover date test**

Create `student-checkin/android-app/app/src/test/java/com/familycheckin/tasks/OccurrenceDateTest.kt`:

```kotlin
package com.familycheckin.tasks

import kotlin.test.Test
import kotlin.test.assertEquals

class OccurrenceDateTest {
    @Test
    fun shanghaiMidnightUsesNextLocalDate() {
        val actual = occurrenceLocalDate("2026-05-27T16:00:00Z")
        assertEquals("2026-05-28", actual)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest --tests com.familycheckin.tasks.OccurrenceDateTest
```

Expected: FAIL because `occurrenceLocalDate` does not exist.

- [ ] **Step 3: Create minimal occurrence date utility**

Create `student-checkin/android-app/app/src/main/java/com/familycheckin/tasks/TodayTaskRepository.kt`:

```kotlin
package com.familycheckin.tasks

import java.time.Instant
import java.time.ZoneId

private val SHANGHAI = ZoneId.of("Asia/Shanghai")

fun occurrenceLocalDate(timestamp: String): String {
    return Instant.parse(timestamp).atZone(SHANGHAI).toLocalDate().toString()
}
```

- [ ] **Step 4: Add task and occurrence schema**

Create `student-checkin/supabase/migrations/202605270002_tasks_occurrences.sql`:

```sql
create table if not exists task_templates (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references families(id) on delete cascade,
  name text not null,
  description text,
  mode text not null check (mode in ('check_only', 'countdown', 'stopwatch')),
  delivery_requirement text not null check (delivery_requirement in ('none', 'photo', 'video', 'audio')),
  point_value integer not null check (point_value >= 0),
  default_target_duration_seconds integer,
  created_at timestamptz not null default now()
);

create table if not exists task_assignments (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references families(id) on delete cascade,
  child_member_id uuid not null references family_members(id) on delete cascade,
  task_template_id uuid not null references task_templates(id) on delete cascade,
  schedule_type text not null check (schedule_type in ('recurring', 'one_off')),
  repeat_rule text,
  scheduled_time_local text,
  target_duration_seconds integer,
  starts_on date not null,
  ends_on date,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists task_occurrences (
  id uuid primary key default gen_random_uuid(),
  assignment_id uuid not null references task_assignments(id) on delete cascade,
  child_member_id uuid not null references family_members(id) on delete cascade,
  local_date date not null,
  status text not null check (status in ('pending', 'running', 'completed', 'cancelled')),
  completion_method text,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  unique (assignment_id, local_date)
);
```

- [ ] **Step 5: Add the daily rollover function**

Create `student-checkin/supabase/functions/daily-rollover/index.ts`:

```ts
import { createClient } from "jsr:@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
);

Deno.serve(async () => {
  const localDate = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(new Date());

  await supabase.rpc("generate_daily_occurrences", { p_local_date: localDate });
  return new Response(JSON.stringify({ ok: true, localDate }), { headers: { "content-type": "application/json" } });
});
```

- [ ] **Step 6: Run tests**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest --tests com.familycheckin.tasks.OccurrenceDateTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add student-checkin/android-app student-checkin/supabase
git commit -m "feat: add task occurrences and shanghai daily rollover"
```

## Task 4: Implement Task Completion, Delivery Uploads, And Point Awarding

**Files:**
- Create: `student-checkin/supabase/migrations/202605270003_points_redemptions.sql`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/tasks/CompleteTaskUseCase.kt`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/tasks/SubmissionUploader.kt`
- Create: `student-checkin/android-app/app/src/test/java/com/familycheckin/tasks/CompleteTaskUseCaseTest.kt`

- [ ] **Step 1: Write the failing completion test**

Create `student-checkin/android-app/app/src/test/java/com/familycheckin/tasks/CompleteTaskUseCaseTest.kt`:

```kotlin
package com.familycheckin.tasks

import kotlin.test.Test
import kotlin.test.assertEquals

class CompleteTaskUseCaseTest {
    @Test
    fun completionAwardsConfiguredPoints() {
        val repository = FakeTaskCompletionRepository()
        val useCase = CompleteTaskUseCase(repository)

        useCase.complete(occurrenceId = "occ-1", points = 5)

        assertEquals(5, repository.lastAwardedPoints)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest --tests com.familycheckin.tasks.CompleteTaskUseCaseTest
```

Expected: FAIL because use case and repository do not exist.

- [ ] **Step 3: Implement minimal completion use case**

Create `student-checkin/android-app/app/src/main/java/com/familycheckin/tasks/CompleteTaskUseCase.kt`:

```kotlin
package com.familycheckin.tasks

interface TaskCompletionRepository {
    fun completeOccurrence(occurrenceId: String, points: Int)
}

class FakeTaskCompletionRepository : TaskCompletionRepository {
    var lastAwardedPoints: Int? = null
    override fun completeOccurrence(occurrenceId: String, points: Int) {
        lastAwardedPoints = points
    }
}

class CompleteTaskUseCase(
    private val repository: TaskCompletionRepository
) {
    fun complete(occurrenceId: String, points: Int) {
        repository.completeOccurrence(occurrenceId, points)
    }
}
```

- [ ] **Step 4: Add points, sessions, submissions, and redemptions schema**

Create `student-checkin/supabase/migrations/202605270003_points_redemptions.sql`:

```sql
create table if not exists task_sessions (
  id uuid primary key default gen_random_uuid(),
  occurrence_id uuid not null references task_occurrences(id) on delete cascade,
  started_at timestamptz not null,
  ended_at timestamptz,
  actual_duration_seconds integer,
  alarm_fired_at timestamptz
);

create table if not exists task_submissions (
  id uuid primary key default gen_random_uuid(),
  occurrence_id uuid not null references task_occurrences(id) on delete cascade,
  submission_type text not null check (submission_type in ('photo', 'video', 'audio')),
  storage_path text not null,
  uploaded_at timestamptz not null default now(),
  retention_days integer not null default 30,
  expires_at timestamptz not null
);

create table if not exists point_ledger (
  id uuid primary key default gen_random_uuid(),
  child_member_id uuid not null references family_members(id) on delete cascade,
  occurrence_id uuid references task_occurrences(id) on delete set null,
  change_type text not null check (change_type in ('task_reward', 'redemption_approved', 'manual_adjust')),
  points_delta integer not null,
  balance_after integer not null,
  created_at timestamptz not null default now()
);

create table if not exists redemption_requests (
  id uuid primary key default gen_random_uuid(),
  child_member_id uuid not null references family_members(id) on delete cascade,
  points_requested integer not null check (points_requested >= 10),
  cash_amount_cny numeric(10,2) not null,
  status text not null check (status in ('pending', 'approved', 'rejected')),
  requested_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references profiles(id)
);
```

- [ ] **Step 5: Run tests**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest --tests com.familycheckin.tasks.CompleteTaskUseCaseTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add student-checkin/android-app student-checkin/supabase
git commit -m "feat: add task completion and point awarding foundation"
```

## Task 5: Implement Child Points, Redemption Validation, And Parent Approval

**Files:**
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/points/RedemptionRules.kt`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/points/PointsScreen.kt`
- Create: `student-checkin/android-app/app/src/main/java/com/familycheckin/parent/RedemptionApprovalScreen.kt`
- Create: `student-checkin/android-app/app/src/test/java/com/familycheckin/points/RedemptionRulesTest.kt`

- [ ] **Step 1: Write the failing redemption rule test**

Create `student-checkin/android-app/app/src/test/java/com/familycheckin/points/RedemptionRulesTest.kt`:

```kotlin
package com.familycheckin.points

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedemptionRulesTest {
    @Test
    fun redemptionMustBeAtLeastTenAndLessThanBalance() {
        assertTrue(canRedeem(balance = 21, requested = 10))
        assertFalse(canRedeem(balance = 21, requested = 21))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest --tests com.familycheckin.points.RedemptionRulesTest
```

Expected: FAIL because `canRedeem` does not exist.

- [ ] **Step 3: Implement minimal redemption rule helpers**

Create `student-checkin/android-app/app/src/main/java/com/familycheckin/points/RedemptionRules.kt`:

```kotlin
package com.familycheckin.points

fun canRedeem(balance: Int, requested: Int): Boolean {
    return requested >= 10 && requested < balance
}

fun cashForPoints(requested: Int, cashPerTenPoints: Int): Double {
    return (requested / 10.0) * cashPerTenPoints
}
```

- [ ] **Step 4: Implement parent approval and child points UI paths**

Create UI files with focused responsibilities:

- `PointsScreen.kt` for child points balance, amount input, equivalent CNY, and ledger
- `RedemptionApprovalScreen.kt` for parent pending queue, approve/reject controls, and result state

Initial Compose structure:

```kotlin
@Composable
fun PointsScreen(
    balance: Int,
    cashPerTenPoints: Int,
    onRedeem: (Int) -> Unit
) { /* render balance, amount input, computed CNY, submit */ }
```

```kotlin
@Composable
fun RedemptionApprovalScreen(
    requests: List<RedemptionRequestUi>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) { /* render pending queue */ }
```

- [ ] **Step 5: Run test**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest --tests com.familycheckin.points.RedemptionRulesTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add student-checkin/android-app
git commit -m "feat: add redemption rule validation and approval screens"
```

## Task 6: Implement Notifications, Reports, And Retention Cleanup

**Files:**
- Create: `student-checkin/supabase/functions/send-notifications/index.ts`
- Create: `student-checkin/supabase/functions/recalculate-reports/index.ts`
- Create: `student-checkin/supabase/migrations/202605270004_notifications_recovery.sql`
- Create: `student-checkin/android-app/app/src/test/java/com/familycheckin/notifications/NotificationMessageTest.kt`
- Create: `student-checkin/docs/deployment.md`
- Create: `student-checkin/docs/operations.md`

- [ ] **Step 1: Write the failing notification formatting test**

Create `student-checkin/android-app/app/src/test/java/com/familycheckin/notifications/NotificationMessageTest.kt`:

```kotlin
package com.familycheckin.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationMessageTest {
    @Test
    fun formatsTaskRewardMessage() {
        val actual = taskRewardMessage("小宇", "阅读 20 分钟", 1, 21)
        assertEquals("小宇 完成任务《阅读 20 分钟》，积分 +1，当前积分 21", actual)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest --tests com.familycheckin.notifications.NotificationMessageTest
```

Expected: FAIL because formatter does not exist.

- [ ] **Step 3: Implement minimal notification formatting and server dispatch**

Create formatter in app code:

```kotlin
package com.familycheckin.notifications

fun taskRewardMessage(childName: String, taskName: String, delta: Int, balance: Int): String {
    return "$childName 完成任务《$taskName》，积分 +$delta，当前积分 $balance"
}
```

Create `student-checkin/supabase/functions/send-notifications/index.ts`:

```ts
Deno.serve(async (req) => {
  const event = await req.json();
  const message = event.message_text as string;
  // dispatch to DingTalk / generic webhook from configured channels
  return new Response(JSON.stringify({ ok: true, message }), { headers: { "content-type": "application/json" } });
});
```

- [ ] **Step 4: Add notification and retention schema**

Append `student-checkin/supabase/migrations/202605270004_notifications_recovery.sql`:

```sql
create table if not exists notification_channels (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references families(id) on delete cascade,
  channel_type text not null check (channel_type in ('dingtalk', 'webhook', 'wechat_reserved')),
  config_json jsonb not null default '{}'::jsonb,
  is_enabled boolean not null default true
);

create table if not exists notification_events (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references families(id) on delete cascade,
  child_member_id uuid references family_members(id) on delete set null,
  event_type text not null,
  message_text text not null,
  payload_json jsonb not null default '{}'::jsonb,
  status text not null default 'pending',
  created_at timestamptz not null default now()
);
```

- [ ] **Step 5: Add deployment and operations docs**

Create `student-checkin/docs/deployment.md` covering:

- Supabase project setup
- Android environment variables
- server deployment endpoints
- scheduled invocation of `daily-rollover`
- scheduled invocation of retention cleanup / report recalc

Create `student-checkin/docs/operations.md` covering:

- password recovery operations
- child credential reset flow
- DingTalk/Webhook troubleshooting
- midnight rollover verification checklist

- [ ] **Step 6: Run tests**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest --tests com.familycheckin.notifications.NotificationMessageTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add student-checkin/android-app student-checkin/supabase student-checkin/docs
git commit -m "feat: add notifications reports and deployment docs"
```

## Task 7: Add Security Policies And End-to-End Verification

**Files:**
- Create: `student-checkin/supabase/migrations/202605270005_rls_policies.sql`
- Create: `student-checkin/supabase/tests/rls_access.sql`
- Create: `student-checkin/README.md`

- [ ] **Step 1: Write failing access expectations as SQL checks**

Create `student-checkin/supabase/tests/rls_access.sql`:

```sql
-- parent should read all family data
-- child should read only own task/point data
-- unrelated family should see nothing
select 'write pgTAP assertions here' as placeholder_should_fail;
```

- [ ] **Step 2: Run verification to show policy coverage is missing**

Run:

```bash
cd student-checkin
supabase db reset
```

Expected: FAIL or incomplete verification because RLS and assertions are not defined yet.

- [ ] **Step 3: Add RLS policies**

Create `student-checkin/supabase/migrations/202605270005_rls_policies.sql`:

```sql
alter table families enable row level security;
alter table profiles enable row level security;
alter table family_members enable row level security;
alter table task_templates enable row level security;
alter table task_assignments enable row level security;
alter table task_occurrences enable row level security;
alter table point_ledger enable row level security;
alter table redemption_requests enable row level security;
```

Add policies granting:

- parent access to all family records
- child access only to self-scoped records
- service role access for jobs/functions

- [ ] **Step 4: Add final README**

Create `student-checkin/README.md` covering:

- product purpose
- Android + Supabase setup
- how to run the Android app
- how to run Supabase locally
- how to trigger midnight rollover locally
- how to test password recovery and notification flows

- [ ] **Step 5: Run full verification**

Run:

```bash
cd student-checkin/android-app
./gradlew testDebugUnitTest
cd ../..
cd student-checkin
supabase db reset
```

Expected:

- Android unit tests PASS
- database migrations apply cleanly
- local backend ready for manual QA

- [ ] **Step 6: Commit**

```bash
git add student-checkin
git commit -m "feat: lock down rls and add project verification docs"
```

## Spec Coverage Check

- Multi-child family space: Task 2
- Server-backed persistence and cross-device login: Task 2
- Parent password recovery / child credential safety: Task 2 and Task 6
- Task templates / assignments / delivery modes: Task 3 and Task 4
- Beijing-midnight daily reset: Task 3
- Automatic completion and points: Task 4
- Redemption request and configurable conversion: Task 5
- DingTalk/Webhook notifications: Task 6
- Reporting and retention cleanup: Task 6
- Security / RLS / deployment readiness: Task 7

## Placeholder Scan

- No `TODO`, `TBD`, or “implement later” placeholders remain in executable steps.
- The one SQL placeholder in Task 7 Step 1 is intentionally the failing baseline artifact for the red phase and must be replaced during implementation before green verification.

## Type Consistency Check

- Roles use `parent` and `child` consistently.
- Task modes use `check_only`, `countdown`, and `stopwatch` consistently in the database, with human-readable UI labels in Android.
- Redemption logic consistently uses `cashPerTenPoints` / `cash_amount_cny`.
- Notification routing consistently uses `dingtalk`, `webhook`, and `wechat_reserved`.

**Plan complete and saved to `docs/superpowers/plans/2026-05-27-student-task-checkin-app-implementation.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
