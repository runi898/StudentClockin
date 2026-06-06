# Student Check-in

一个可自部署的学生任务打卡系统，适合家庭场景，也适合后续继续二开和开源。

核心能力：

- 一个家庭管理多个孩子
- 家长给不同孩子分配不同任务
- 孩子按任务完成打卡、倒计时、正计时
- 任务可要求上传照片 / 视频 / 音频 / 无要求
- 完成自动发积分
- 孩子主动发起积分兑换，家长审核
- 家长查看每天、最近 7 天、最近 30 天等统计数据
- 可选钉钉 / Webhook 通知

## 项目结构

- `android-app/`
  - Android 客户端，Kotlin + Jetpack Compose
- `backend/`
  - Node.js API 服务
- `supabase/`
  - PostgreSQL 数据库迁移脚本
- `docs/`
  - 开发、部署、运维文档
- `deploy/`
  - 家用服务器 / Docker 部署辅助文件

## 快速开始

### 1. 准备后端

复制环境变量：

```bash
cp .env.example .env
```

至少修改：

- `POSTGRES_PASSWORD`
- `JWT_SECRET`
- `API_PORT`

启动服务：

```bash
docker compose up -d --build
```

默认是精简的 2 容器架构：

1. `student-checkin-postgres`
2. `student-checkin-api`

API 容器启动时会自动执行 `supabase/migrations/` 下的数据库迁移脚本，所以不需要一整套 Supabase 容器。

### 2. 配置 Android 连接你的后端

复制配置模板：

```bash
cp android-app/gradle-local.example.properties android-app/gradle-local.properties
```

填写你的服务端地址：

```properties
studentclockinSupabaseUrl=http://your-server:28547
studentclockinSupabasePublicKey=student-checkin-public
```

说明：

- Android 端目前沿用旧变量名，目的是减少构建层改动
- `studentclockinSupabasePublicKey` 当前只需要一个非空占位值

### 3. 编译 APK

Windows PowerShell：

```powershell
.\scripts\build-debug-apk.ps1
```

或进入 Android 目录手动编译：

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

## Android 开发环境

1. 安装 JDK 17 或更高版本
2. 安装 Android SDK Platform 35
3. 创建 `android-app/local.properties` 并填写 `sdk.dir=...`
4. 复制 `android-app/gradle-local.example.properties` 为 `android-app/gradle-local.properties`
5. 在 `android-app/` 目录运行测试：

```powershell
.\gradlew.bat testDebugUnitTest
```

## 公开仓库配置说明

为了方便别人直接拿去部署和二开，这个项目已经改成“本地配置 + 示例文件”的方式：

- Android 本地配置：
  - `android-app/gradle-local.properties`
  - 示例：`android-app/gradle-local.example.properties`
- 后端部署配置：
  - `.env`
  - 示例：`.env.example`

需要用户自己填写的内容：

- Android 连接地址：
  - `STUDENTCLOCKIN_SUPABASE_URL`
  - `STUDENTCLOCKIN_SUPABASE_PUBLIC_KEY`
- 后端部署：
  - `POSTGRES_PASSWORD`
  - `JWT_SECRET`
  - `API_PORT`

源码中不应固定写死个人内网 IP、个人域名、数据库密码或 JWT 密钥。

## 重要文档

- `docs/developer-setup.md`
- `docs/home-server-docker.md`
- `docker-compose.yml`
- `.env.example`
- `android-app/gradle-local.example.properties`
- `scripts/build-debug-apk.ps1`
- `deploy/home-server/docker-compose.two-container.yml`
- `deploy/home-server/.env.example`

## 内置定时任务

这些任务运行在 API 容器内部：

- 按北京时间生成每日任务实例
- 汇总孩子每日任务报表
- 发送钉钉 / Webhook 通知
- 家长侧孩子账户创建、重置密码、停用流程支持
