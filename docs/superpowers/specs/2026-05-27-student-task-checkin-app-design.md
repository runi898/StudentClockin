# Student Task Check-in App Design

Date: 2026-05-27
Status: Approved design
Audience: Product, Android, backend, analytics

## Summary

This app is a family-use Android application for parents and children. Parents create tasks for one or more children, children complete tasks through one-tap check-ins, countdown-based tasks, or manual timer tasks, and the system records precise Beijing-time execution data, awards points automatically, and supports point redemption with parent approval.

The same Android app serves both parent and child roles. The first version is optimized for fast delivery, clean daily use, and a data model that can later be open-sourced and extended for more families.

## Goals

- Support one family with multiple children under one parent-managed workspace.
- Let parents assign recurring or one-off tasks to specific children.
- Support three task completion modes:
  - One-tap completion
  - Countdown completion with alarm and confirm
  - Manual start/stop timing with actual duration tracking
- Support delivery requirements per task:
  - None
  - Photo
  - Video
  - Audio
- Award fixed task points automatically on completion.
- Support point redemption with a minimum of 10 points and a conversion rate of 10 points = 1 CNY.
- Let parents review daily, weekly, and monthly completion and duration trends.
- Record all critical task and point events with Beijing-time precision to the second.
- Support optional DingTalk robot notifications and generic webhook notifications.
- Persist data on a server so users can switch devices by logging into the same account.
- Reset daily task state on Beijing-time midnight (`00:00:00` Asia/Shanghai).
- Support parent account password recovery and parent-managed child credential safety.

## Non-Goals For MVP

- Teacher, classroom, or organization roles
- WeChat official account delivery in production
- AI-based verification of submitted media
- iOS client
- Advanced gamification such as badges and leveling
- Complex workflow approvals for task completion

## Users And Roles

### Parent

- Creates the family space
- Creates child accounts
- Owns account recovery and password safety for the family workspace
- Creates and edits task templates
- Assigns tasks to specific children
- Sets delivery requirements, schedules, durations, and point values
- Reviews task completion, time spent, media submissions, points, redemptions, and reports
- Configures optional notification channels
- Approves or rejects redemption requests

### Child

- Logs into the same app with a child account
- Sees today's tasks
- Completes tasks through the configured mode
- Uploads required media when needed
- Views current point balance, point details, and redemption history
- Submits redemption requests

## Product Scope

### Task Configuration

Each task supports:

- Task name
- Optional description
- Completion mode:
  - `check_only`
  - `countdown`
  - `stopwatch`
- Delivery requirement:
  - `none`
  - `photo`
  - `video`
  - `audio`
- Fixed point value
- Optional target duration
- Recurring or one-off scheduling
- Optional local reminder time

Only parents can create or edit point values.

### Completion Rules

- If delivery requirement is `none`, the task completes directly through the configured completion mode.
- If delivery requirement is `photo`, `video`, or `audio`, the child must upload the required media as part of completion.
- Once the child completes the task or uploads the required media, the task is immediately marked completed.
- Completion immediately awards the task's configured fixed points.
- Parents review completed work after the fact; there is no blocking approval step for task completion in MVP.

### Point Rules

- Every task has its own point value.
- Points are awarded automatically on successful completion.
- Children can request point redemption.
- Minimum redemption is 10 points.
- Conversion rate is fixed at 10 points = 1 CNY.
- Parent approval deducts points.
- Parent rejection leaves balance unchanged.
- Parent account can change the redemption rule later; the product UI must already support a configurable cash value per 10 points.

### Media Retention Rules

- Parents configure media retention days per family.
- Default retention is 30 days.
- When media expires, the file may be deleted from storage.
- Task records, completion records, point records, and redemption records remain preserved.

### Notification Rules

Notification configuration is optional.

MVP supports:

- DingTalk robot notifications
- Generic webhook notifications

Reserved for later:

- WeChat official account notifications

Initial notification events:

- Task completed and points awarded
- Redemption request submitted
- Redemption request approved
- Redemption request rejected

Notifications must be asynchronous so delivery failures do not affect task completion or point updates.

### Account And Recovery Rules

- Parent accounts authenticate against the server and can restore data on a new device by logging in again.
- Parent accounts need password reset / recovery so child data is not lost when a parent forgets credentials.
- Child accounts live inside a family workspace and should be resettable by a parent without deleting child history.
- The family workspace must remain durable on the server as long as the parent account remains active.

## UX Direction

The app should use a light, compact, growth-oriented visual style.

Key UX decisions:

- Child home uses a lightweight high-density task list, not large stacked cards.
- A child should be able to see most or all of roughly 10-12 tasks with minimal scrolling.
- Parent screens prioritize overview, exceptions, and trend visibility without a heavy dashboard feel.
- Parents and children share the same app shell but get role-specific navigation and homepage content.

## System Architecture

### Recommended Stack

- Android client: Kotlin + Jetpack Compose
- Local persistence: Room
- Background sync/jobs: WorkManager
- Precise alarms/reminders: AlarmManager
- Cloud backend: Supabase
- Database: PostgreSQL
- Media storage: Supabase Storage
- Notifications:
  - DingTalk robot
  - Webhook
  - Reserved event integration point for WeChat

### Why This Stack

- Native Android is the best fit for alarms, timers, background behavior, and device UX.
- Supabase provides a fast path to delivery while keeping PostgreSQL as the source of truth.
- PostgreSQL makes later reporting, trend analysis, and open-source extension easier than a document-first backend.
- The system stays light enough for family use but structured enough for future community extension.

### Layers

1. Presentation layer
   - Parent UI
   - Child UI
2. Client domain layer
   - Task execution logic
   - Timer/countdown logic
   - Completion logic
   - Point award logic
   - Redemption request logic
   - Submission upload logic
3. Local data layer
   - Cached tasks
   - Cached summaries
   - Offline-safe local actions with later sync
4. Cloud data layer
   - Family, users, tasks, assignments, occurrences, submissions, points, redemptions, notifications
5. Async processing layer
   - Alarm triggers
   - Notification dispatch
   - Media cleanup
   - Daily/weekly/monthly aggregation

## Time Model

All critical task and point events must preserve Beijing-time precision to the second.

Rules:

- Store canonical event timestamps as `timestamptz`
- Record `local_date` for grouping and reporting
- Display all dates/times in `Asia/Shanghai`
- Reset daily task state and generate the new day of task occurrences at `00:00:00` Asia/Shanghai
- Preserve second-level timestamps for:
  - Task start
  - Alarm fire
  - Submission upload
  - Task completion
  - Timer end
  - Redemption request
  - Redemption approval/rejection
  - Point ledger changes

This ensures accurate daily grouping and human-readable auditing while remaining safe for sync and future multi-device support.

## Core Data Model

The system should center on one row per `child x local_date x task occurrence`.

### Account And Family

- `families`
  - Family workspace
- `profiles`
  - App user profile linked to Supabase Auth
- `family_members`
  - Family membership and role (`parent`, `child`)
- `auth_recovery_audits`
  - Optional audit trail for password resets and account recovery actions

### Task Definition

- `task_templates`
  - Reusable task definition
  - Key fields:
    - `name`
    - `description`
    - `mode`
    - `delivery_requirement`
    - `point_value`
    - `default_target_duration_seconds`

### Task Assignment

- `task_assignments`
  - Assigns a template to a specific child
  - Key fields:
    - `family_id`
    - `child_member_id`
    - `task_template_id`
    - `schedule_type` (`recurring`, `one_off`)
    - `repeat_rule`
    - `scheduled_time_local`
    - `target_duration_seconds`
    - `starts_on`
    - `ends_on`
    - `is_active`

### Daily Occurrence

- `task_occurrences`
  - Concrete daily task instance for one child on one date
  - Key fields:
    - `assignment_id`
    - `child_member_id`
    - `local_date`
    - `status` (`pending`, `running`, `completed`, `cancelled`)
    - `completion_method`
    - `completed_at`

### Sessions, Events, And Submission

- `task_sessions`
  - Start/end timing unit
  - Key fields:
    - `occurrence_id`
    - `started_at`
    - `ended_at`
    - `actual_duration_seconds`
    - `alarm_fired_at`

- `task_submissions`
  - Uploaded media metadata
  - Key fields:
    - `occurrence_id`
    - `submission_type`
    - `storage_path`
    - `uploaded_at`
    - `retention_days`
    - `expires_at`

- `task_events`
  - Immutable event log
  - Key fields:
    - `occurrence_id`
    - `event_type`
    - `event_at`
    - `payload_json`

### Points And Redemption

- `point_ledger`
  - Immutable point balance changes
  - Key fields:
    - `child_member_id`
    - `occurrence_id` nullable
    - `change_type`
    - `points_delta`
    - `balance_after`
    - `created_at`

- `redemption_requests`
  - Child redemption requests
  - Key fields:
    - `child_member_id`
    - `points_requested`
    - `cash_amount_cny`
    - `status`
    - `requested_at`
    - `reviewed_at`
    - `reviewed_by`

### Notifications

- `notification_channels`
  - Parent-configured endpoints
- `notification_events`
  - Business events to fan out
- `notification_deliveries`
  - Per-channel delivery attempt log

### Reporting

- `daily_summaries`
- `weekly_summaries`
- `monthly_summaries`

MVP may compute some reports directly from primary tables and introduce persisted aggregates where needed.

## Core Workflows

### 1. Parent Creates And Assigns Task

1. Parent creates or edits a task template
2. Parent selects a child
3. Parent sets recurrence or one-off date
4. Parent sets mode, delivery requirement, points, reminder time, and target duration
5. System stores the assignment
6. System generates daily occurrences for the relevant dates

### 1b. Daily Reset And Occurrence Roll Forward

1. At `00:00:00` Beijing time, the system closes the previous day's task window
2. Daily task status is reset for the new day
3. New occurrences are generated for recurring assignments due that day
4. The child app syncs and shows the new day's task list

### 2. Child Completes One-Tap Task

1. Child opens today's task list
2. Child taps complete
3. If no delivery is required, system completes the occurrence immediately
4. System records completion timestamp
5. System writes point ledger entry
6. System queues notification event

### 3. Child Completes Countdown Task

1. Child taps start
2. System records session start
3. Alarm fires at target duration
4. Child confirms completion
5. If delivery is required, child uploads required media
6. System records completion and duration
7. System awards points and queues notifications

### 4. Child Completes Stopwatch Task

1. Child taps start
2. System records session start
3. Child taps end when finished
4. If delivery is required, child uploads required media
5. System records end time, actual duration, completion, points, and notification events

### 5. Child Requests Redemption

1. Child opens points page
2. Child sees total points and equivalent CNY value
3. Child submits a redemption request for at least 10 points
4. System creates a `pending` redemption request
5. Notification event is queued

### 6. Parent Reviews Redemption

1. Parent opens redemption review page
2. Parent approves or rejects request
3. If approved:
   - System writes point deduction ledger
   - System updates request status
   - Notification event is queued
4. If rejected:
   - System updates request status only
   - Notification event is queued

### 7. Parent Recovers Account Or Resets Child Access

1. Parent requests password recovery
2. Server sends or validates a reset flow
3. Parent sets a new password and regains access to the family workspace
4. Parent can also reset a child account credential without deleting the child's task, point, or redemption history

## Page Structure

### Child Pages

1. Login
2. Today task list
3. Task detail
4. Countdown execution page
5. Stopwatch execution page
6. Submission page
7. Completion result page
8. Points page
9. Redemption request page
10. History page

### Parent Pages

1. Login / family create
2. Child management
3. Task template list
4. Task assignment page
5. Today overview
6. Child detail
7. Reports
8. Redemption review
9. Notification settings
10. Media retention settings
11. Account and password management

## MVP Scope

### P0

- Parent and child accounts in one app
- Multi-child support
- Task templates
- Task assignments
- Three completion modes
- Delivery requirements (`none`, `photo`, `video`, `audio`)
- Automatic completion and point award
- Beijing-time event recording to the second
- Child points page with point-to-cash equivalent
- Redemption requests
- Parent redemption review
- Parent daily overview
- Basic daily and monthly reporting
- Server-side account persistence across devices
- Parent password recovery and child credential reset
- Beijing-time midnight daily occurrence refresh
- DingTalk robot notifications
- Webhook notifications
- Configurable media retention days

### P1

- Weekly report
- Per-task trend analysis
- Notification retry logic
- CSV export
- Parent manual point adjustments
- Persisted report aggregates where needed

### P2

- WeChat official account notifications
- Richer charts and insights
- Achievement system
- AI-assisted media analysis
- iOS client

## Risks And Constraints

- Android vendor background restrictions may affect alarm reliability; this must be tested on real devices.
- Media storage growth can become expensive without retention cleanup.
- Video and audio uploads must be constrained in size and upload UX to keep family use manageable.
- Notification delivery must never block the main business flow.

## Recommendation

Build the MVP as a balanced family-first system:

- Native Android for execution quality
- Supabase/PostgreSQL for speed plus strong reporting semantics
- Lightweight, high-density child task UI
- Daily occurrence-centered data model
- Automatic point awarding and parent-reviewed redemptions
- Optional DingTalk/webhook notifications

This is the smallest architecture that fully supports the confirmed requirements while preserving a clean path to open-source expansion.
