# Developer Setup

This document is for developers who clone the repository and want to:

1. run or deploy their own backend
2. point the Android app at that backend
3. build a test APK

## 1. Clone the repository

```bash
git clone https://github.com/your-account/StudentClockin.git
cd StudentClockin/student-checkin
```

The Git repository root is usually the parent folder:

- repository root: `StudentClockin`
- app project root: `StudentClockin/student-checkin`

## 2. Configure the Android app backend URL

The Android app does not need source code changes to switch servers.

Copy the sample file:

```bash
cp android-app/gradle-local.example.properties android-app/gradle-local.properties
```

Edit `android-app/gradle-local.properties`:

```properties
studentclockinSupabaseUrl=https://your-api.example.com
studentclockinSupabasePublicKey=student-checkin-public
```

Notes:

- `studentclockinSupabaseUrl` should be your backend base URL.
- `studentclockinSupabasePublicKey` is only a non-empty placeholder for the
  current mobile wiring.
- `android-app/gradle-local.properties` is ignored by Git, so each developer can
  keep their own private local config.

You can also override the same values with environment variables:

```bash
STUDENTCLOCKIN_SUPABASE_URL=https://your-api.example.com
STUDENTCLOCKIN_SUPABASE_PUBLIC_KEY=student-checkin-public
```

## 3. Start the backend locally or on a server

Copy the backend environment template:

```bash
cp .env.example .env
```

Edit `.env` and change at least:

```dotenv
POSTGRES_PASSWORD=replace-with-a-strong-db-password
JWT_SECRET=replace-with-a-long-random-secret
API_PORT=28547
```

Then start the 2-container stack:

```bash
docker compose up -d --build
```

This starts:

- `student-checkin-postgres`
- `student-checkin-api`

The backend will automatically apply SQL migrations from `supabase/migrations/`.

## 4. Verify the backend

Example:

```bash
curl http://127.0.0.1:28547/health
curl http://127.0.0.1:28547/auth/v1/
curl http://127.0.0.1:28547/rest/v1/
```

If your backend is remote, replace `127.0.0.1:28547` with your actual server
domain or IP.

## 5. Build the Android APK

Requirements:

- JDK 17 or newer
- Android SDK installed
- `android-app/local.properties` containing `sdk.dir=...`

Windows PowerShell:

```powershell
$env:JAVA_HOME="C:\path\to\jdk"
.\scripts\build-debug-apk.ps1
```

Manual Gradle build:

```bash
cd android-app
./gradlew assembleDebug
```

APK output:

- `android-app/app/build/outputs/apk/debug/app-debug.apk`

## 6. Change to another backend later

If you want to switch the app to another server later, you only need to update:

- `android-app/gradle-local.properties`

Change this line:

```properties
studentclockinSupabaseUrl=https://your-new-api.example.com
```

Then rebuild the APK.

## 7. Deploy the backend to a Linux server

For a home server or VPS deployment guide, see:

- `docs/home-server-docker.md`

## 8. Git workflow

Typical workflow:

```bash
git status
git add .
git commit -m "Describe your change"
git push
```

Keep these files private and local:

- `.env`
- `android-app/gradle-local.properties`
- `android-app/local.properties`
